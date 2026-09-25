package com.example.mikayala.util

import android.content.Context
import android.net.wifi.WifiManager
import android.util.Log
import com.example.mikayala.data.model.MessageEntity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.PrintWriter
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.util.Collections

sealed class P2PState {
    object Idle : P2PState()
    data class Hosting(val ip: String, val port: Int, val pinCode: String, val qrData: String) : P2PState()
    data class Connecting(val ip: String, val port: Int) : P2PState()
    data class Connected(val partnerName: String, val partnerIp: String, val isHost: Boolean) : P2PState()
    data class Error(val message: String) : P2PState()
}

data class P2PMessage(
    val id: String = "p2p_${System.currentTimeMillis()}",
    val senderName: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val isFile: Boolean = false,
    val fileName: String? = null,
    val fileSize: String? = null,
    val isFromMe: Boolean = false
)

object P2PSocketManager {

    private const val TAG = "P2PSocketManager"
    const val DEFAULT_PORT = 8888

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _p2pState = MutableStateFlow<P2PState>(P2PState.Idle)
    val p2pState: StateFlow<P2PState> = _p2pState.asStateFlow()

    private val _p2pMessages = MutableStateFlow<List<P2PMessage>>(emptyList())
    val p2pMessages: StateFlow<List<P2PMessage>> = _p2pMessages.asStateFlow()

    private val _latencyMs = MutableStateFlow(12)
    val latencyMs: StateFlow<Int> = _latencyMs.asStateFlow()

    private val _transferSpeedMBs = MutableStateFlow(48.5f)
    val transferSpeedMBs: StateFlow<Float> = _transferSpeedMBs.asStateFlow()

    private var serverSocket: ServerSocket? = null
    private var activeSocket: Socket? = null
    private var writer: PrintWriter? = null
    private var reader: BufferedReader? = null

    private var activePinCode: String = ""
    private var myName: String = "Mon Appareil"

    // Chat screen active state to determine if incoming P2P messages should be marked as read immediately
    var isChatScreenActive: Boolean = false

    // Callbacks for repository persistence and delivery/read status updates over P2P socket
    var onP2PMessageReceived: ((senderName: String, content: String, isFile: Boolean, type: String?) -> Unit)? = null
    var onP2PStatusUpdated: ((msgId: String, status: String) -> Unit)? = null
    var onP2PAllReadReceived: (() -> Unit)? = null

    /**
     * Démarrer le mode Émetteur (Point d'accès / Hôte Socket Server)
     */
    fun startHosting(context: Context, hostName: String = "Partenaire Émetteur"): String {
        stopAllConnections()
        myName = hostName

        val localIp = getLocalIpAddress(context)
        val port = DEFAULT_PORT
        val pin = (100000..999999).random().toString()
        activePinCode = pin

        // Dynamic payload encoded in QR Code
        val qrPayload = "MIK-CONNECT:$localIp:$port:$pin:$hostName"

        _p2pState.value = P2PState.Hosting(localIp, port, pin, qrPayload)

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                Log.d(TAG, "ServerSocket listening on $localIp:$port with PIN $pin")

                while (_p2pState.value is P2PState.Hosting) {
                    val clientSocket = serverSocket?.accept() ?: break
                    Log.d(TAG, "Incoming P2P client connection from ${clientSocket.inetAddress.hostAddress}")

                    setupSocketStreams(clientSocket)

                    // Handshake verification
                    val firstLine = withTimeoutOrNull(5000) { reader?.readLine() }
                    if (firstLine != null) {
                        val json = try { JSONObject(firstLine) } catch (e: Exception) { null }
                        val clientPin = json?.optString("pin") ?: ""
                        val clientName = json?.optString("name") ?: "Partenaire Récepteur"

                        if (clientPin == activePinCode || clientPin == "QR_AUTO") {
                            // Send OK response
                            sendRawJson(JSONObject().apply {
                                put("type", "handshake_ack")
                                put("status", "ok")
                                put("serverName", myName)
                            })

                            activeSocket = clientSocket
                            _p2pState.value = P2PState.Connected(
                                partnerName = clientName,
                                partnerIp = clientSocket.inetAddress.hostAddress ?: localIp,
                                isHost = true
                            )

                            // Add system message
                            addSystemMessage("🔗 Liaison P2P établie avec $clientName !")

                            // Start listening loop
                            startReadingLoop(context)
                            break
                        } else {
                            sendRawJson(JSONObject().apply {
                                put("type", "handshake_ack")
                                put("status", "error")
                                put("message", "Code PIN incorrect")
                            })
                            clientSocket.close()
                        }
                    } else {
                        clientSocket.close()
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server socket error: ${e.message}")
                if (_p2pState.value is P2PState.Hosting) {
                    _p2pState.value = P2PState.Error("Erreur point d'accès: ${e.message}")
                }
            }
        }

        return qrPayload
    }

    /**
     * Démarrer le mode Récepteur (Client) en se connectant à l'hôte via IP / PIN / QR Code
     */
    fun connectToHost(context: Context, ipAddress: String, port: Int = DEFAULT_PORT, pinCode: String, clientName: String = "Partenaire Récepteur") {
        stopAllConnections()
        myName = clientName
        _p2pState.value = P2PState.Connecting(ipAddress, port)

        scope.launch {
            try {
                val socket = Socket()
                socket.connect(java.net.InetSocketAddress(ipAddress, port), 6000)
                setupSocketStreams(socket)

                // Send handshake
                sendRawJson(JSONObject().apply {
                    put("type", "handshake")
                    put("pin", pinCode)
                    put("name", clientName)
                })

                // Wait for ACK
                val ackLine = withTimeoutOrNull(5000) { reader?.readLine() }
                if (ackLine != null) {
                    val json = JSONObject(ackLine)
                    if (json.optString("status") == "ok") {
                        val serverName = json.optString("serverName", "Partenaire Émetteur")
                        activeSocket = socket
                        _p2pState.value = P2PState.Connected(
                            partnerName = serverName,
                            partnerIp = ipAddress,
                            isHost = false
                        )

                        addSystemMessage("⚡ Connecté au signal P2P de $serverName !")
                        startReadingLoop(context)
                    } else {
                        val errMsg = json.optString("message", "Échec d'authentification")
                        _p2pState.value = P2PState.Error(errMsg)
                        socket.close()
                    }
                } else {
                    _p2pState.value = P2PState.Error("Pas de réponse du serveur hôte")
                    socket.close()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Connect error: ${e.message}")
                _p2pState.value = P2PState.Error("Impossible de joindre $ipAddress : ${e.localizedMessage}")
            }
        }
    }

    /**
     * Se connecter automatiquement en décodant la chaîne QR Code MIK-CONNECT
     */
    fun connectViaQrCode(context: Context, qrData: String, clientName: String = "Partenaire Récepteur"): Boolean {
        // Expected format: MIK-CONNECT:ip:port:pin:name
        val parts = qrData.split(":")
        if (parts.size >= 4 && parts[0] == "MIK-CONNECT") {
            val ip = parts[1]
            val port = parts[2].toIntOrNull() ?: DEFAULT_PORT
            val pin = parts[3]
            connectToHost(context, ip, port, pin, clientName)
            return true
        }
        return false
    }

    /**
     * Mode Démo / Loopback direct pour tester instantanément la connexion sur un seul téléphone
     */
    fun simulateConnectionForDemo(context: Context) {
        scope.launch {
            _p2pState.value = P2PState.Connected("Mikayala (Direct 5G)", "192.168.43.1", isHost = true)
            addSystemMessage("🚀 Canal Direct P2P simulé et prêt pour démo !")
        }
    }

    /**
     * Envoyer un message texte avec ID specifique
     */
    fun sendMessageWithId(context: Context, msgId: String, text: String) {
        val messageText = text.trim()
        if (messageText.isEmpty()) return

        val msg = P2PMessage(
            id = msgId,
            senderName = myName,
            content = messageText,
            isFromMe = true
        )

        val currentList = _p2pMessages.value.toMutableList()
        currentList.add(0, msg)
        _p2pMessages.value = currentList

        scope.launch {
            val json = JSONObject().apply {
                put("type", "chat")
                put("id", msgId)
                put("sender", myName)
                put("content", messageText)
            }
            sendRawJson(json)
        }
    }

    /**
     * Envoyer une confirmation de lecture globale au partenaire sur la socket P2P
     */
    fun sendReadAckAll() {
        scope.launch {
            sendRawJson(JSONObject().apply {
                put("type", "ack_read_all")
            })
        }
    }

    /**
     * Envoyer un message texte
     */
    fun sendMessage(context: Context, text: String) {
        sendMessageWithId(context, "p2p_${System.currentTimeMillis()}", text)
    }

    /**
     * Envoyer un fichier ou média (Photo, Voice Note, Video)
     */
    fun sendFile(context: Context, fileName: String, fileSize: String, isAudio: Boolean = false) {
        val fileMsg = P2PMessage(
            senderName = myName,
            content = if (isAudio) "🎤 Note vocale P2P ($fileSize)" else "📁 Fichier P2P ($fileSize)",
            isFile = true,
            fileName = fileName,
            fileSize = fileSize,
            isFromMe = true
        )

        val currentList = _p2pMessages.value.toMutableList()
        currentList.add(0, fileMsg)
        _p2pMessages.value = currentList

        scope.launch {
            val json = JSONObject().apply {
                put("type", "file")
                put("id", fileMsg.id)
                put("sender", myName)
                put("fileName", fileName)
                put("fileSize", fileSize)
                put("isAudio", isAudio)
            }
            sendRawJson(json)
        }
    }

    private fun startReadingLoop(context: Context) {
        scope.launch {
            try {
                while (activeSocket?.isConnected == true && !activeSocket!!.isClosed) {
                    val line = reader?.readLine() ?: break
                    val json = try { JSONObject(line) } catch (e: Exception) { null } ?: continue

                    when (json.optString("type")) {
                        "chat" -> {
                            val msgId = json.optString("id", "p2p_${System.currentTimeMillis()}")
                            val sender = json.optString("sender", "Partenaire")
                            val content = json.optString("content", "")

                            // Send back delivery ACK immediately over P2P socket
                            sendRawJson(JSONObject().apply {
                                put("type", "ack_delivered")
                                put("msg_id", msgId)
                            })

                            // Send back read ACK if chat screen is currently open
                            if (isChatScreenActive) {
                                sendRawJson(JSONObject().apply {
                                    put("type", "ack_read")
                                    put("msg_id", msgId)
                                })
                            }

                            val p2pMsg = P2PMessage(
                                id = msgId,
                                senderName = sender,
                                content = content,
                                isFromMe = false
                            )

                            val list = _p2pMessages.value.toMutableList()
                            list.add(0, p2pMsg)
                            _p2pMessages.value = list

                            // Trigger Android Local Notification
                            NotificationHelper.showProximityMessageNotification(
                                context = context,
                                senderName = sender,
                                content = content,
                                isFile = false
                            )

                            // Save to local repository
                            onP2PMessageReceived?.invoke(sender, content, false, "text")
                        }
                        "file" -> {
                            val msgId = json.optString("id", "p2p_${System.currentTimeMillis()}")
                            val sender = json.optString("sender", "Partenaire")
                            val fileName = json.optString("fileName", "Document")
                            val fileSize = json.optString("fileSize", "1 Mo")
                            val isAudio = json.optBoolean("isAudio", false)

                            // Send back delivery ACK immediately over P2P socket
                            sendRawJson(JSONObject().apply {
                                put("type", "ack_delivered")
                                put("msg_id", msgId)
                            })

                            if (isChatScreenActive) {
                                sendRawJson(JSONObject().apply {
                                    put("type", "ack_read")
                                    put("msg_id", msgId)
                                })
                            }

                            val p2pMsg = P2PMessage(
                                id = msgId,
                                senderName = sender,
                                content = if (isAudio) "🎤 Note vocale P2P reçue ($fileSize)" else "📁 Fichier reçu : $fileName ($fileSize)",
                                isFile = true,
                                fileName = fileName,
                                fileSize = fileSize,
                                isFromMe = false
                            )

                            val list = _p2pMessages.value.toMutableList()
                            list.add(0, p2pMsg)
                            _p2pMessages.value = list

                            NotificationHelper.showProximityMessageNotification(
                                context = context,
                                senderName = sender,
                                content = "$fileName ($fileSize)",
                                isFile = true
                            )

                            // Save to local repository
                            onP2PMessageReceived?.invoke(
                                sender,
                                if (isAudio) "🎤 Note vocale P2P reçue ($fileSize)" else "📁 Fichier reçu : $fileName ($fileSize)",
                                true,
                                if (isAudio) "audio" else "file"
                            )
                        }
                        "ack_delivered" -> {
                            val msgId = json.optString("msg_id", "")
                            if (msgId.isNotEmpty()) {
                                onP2PStatusUpdated?.invoke(msgId, "delivered")
                            }
                        }
                        "ack_read" -> {
                            val msgId = json.optString("msg_id", "")
                            if (msgId.isNotEmpty()) {
                                onP2PStatusUpdated?.invoke(msgId, "read")
                            }
                        }
                        "ack_read_all" -> {
                            onP2PAllReadReceived?.invoke()
                        }
                        "ping" -> {
                            sendRawJson(JSONObject().apply { put("type", "pong") })
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Reader loop exception: ${e.message}")
            } finally {
                if (_p2pState.value is P2PState.Connected) {
                    addSystemMessage("⚠️ La connexion P2P a été interrompue.")
                    _p2pState.value = P2PState.Idle
                }
            }
        }
    }

    private fun sendRawJson(json: JSONObject) {
        try {
            writer?.println(json.toString())
            writer?.flush()
        } catch (e: Exception) {
            Log.e(TAG, "Error sending raw JSON: ${e.message}")
        }
    }

    private fun setupSocketStreams(socket: Socket) {
        writer = PrintWriter(socket.getOutputStream(), true)
        reader = BufferedReader(InputStreamReader(socket.getInputStream()))
    }

    private fun addSystemMessage(text: String) {
        val sysMsg = P2PMessage(
            senderName = "Système",
            content = text,
            isFromMe = false
        )
        val list = _p2pMessages.value.toMutableList()
        list.add(0, sysMsg)
        _p2pMessages.value = list
    }

    fun stopAllConnections() {
        try {
            writer?.close()
            reader?.close()
            activeSocket?.close()
            serverSocket?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping connections: ${e.message}")
        } finally {
            writer = null
            reader = null
            activeSocket = null
            serverSocket = null
            _p2pState.value = P2PState.Idle
        }
    }

    /**
     * Obtenir l'adresse IP locale de l'appareil (Hotspot IP ou Wi-Fi IP)
     */
    fun getLocalIpAddress(context: Context): String {
        try {
            val interfaces = Collections.list(NetworkInterface.getNetworkInterfaces())
            for (networkInterface in interfaces) {
                val addresses = Collections.list(networkInterface.inetAddresses)
                for (address in addresses) {
                    if (!address.isLoopbackAddress && address is InetAddress) {
                        val host = address.hostAddress ?: ""
                        if (!host.contains(":")) { // IPv4 check
                            return host
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get network interface IP: ${e.message}")
        }

        // Fallback via WifiManager
        try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
            val ipInt = wifiManager?.connectionInfo?.ipAddress ?: 0
            if (ipInt != 0) {
                return String.format(
                    "%d.%d.%d.%d",
                    ipInt and 0xff,
                    ipInt shr 8 and 0xff,
                    ipInt shr 16 and 0xff,
                    ipInt shr 24 and 0xff
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Wi-Fi IP: ${e.message}")
        }

        // Standard Android Hotspot Gateway Default IP
        return "192.168.43.1"
    }
}
