package com.example.mikayala.util

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object MikayalaMediaStorage {

    private const val TAG = "MikayalaMediaStorage"
    const val MEDIA_FOLDER_NAME = "Mikayala"

    /**
     * Obtenir ou créer le dossier dédié pour les fichiers Médias (Style WhatsApp com.example.mikayala)
     */
    fun getDedicatedMediaFolder(context: Context, subFolder: String): File {
        val baseDir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ standard dedicated media path: Android/media/com.example.mikayala/Mikayala/
            val mediaDirs = context.externalMediaDirs
            if (mediaDirs.isNotEmpty() && mediaDirs[0] != null) {
                File(mediaDirs[0], MEDIA_FOLDER_NAME)
            } else {
                File(context.getExternalFilesDir(null), MEDIA_FOLDER_NAME)
            }
        } else {
            // Android < 10 public SDCard directory
            @Suppress("DEPRECATION")
            File(Environment.getExternalStorageDirectory(), "Mikayala")
        }

        val targetFolder = File(baseDir, subFolder)
        if (!targetFolder.exists()) {
            val created = targetFolder.mkdirs()
            Log.d(TAG, "Directory $targetFolder created=$created")
        }
        return targetFolder
    }

    fun getImagesFolder(context: Context): File = getDedicatedMediaFolder(context, "Mikayala Images")
    fun getVideosFolder(context: Context): File = getDedicatedMediaFolder(context, "Mikayala Videos")
    fun getVoiceNotesFolder(context: Context): File = getDedicatedMediaFolder(context, "Mikayala Voice Notes")
    fun getDocumentsFolder(context: Context): File = getDedicatedMediaFolder(context, "Mikayala Documents")

    /**
     * Sauvegarder un fichier média dans la galerie publique et le dossier système Mikayala
     */
    fun saveVoiceNoteToDedicatedFolder(context: Context, sourceFile: File): File? {
        try {
            if (!sourceFile.exists() || sourceFile.length() <= 0L) return null
            val folder = getVoiceNotesFolder(context)
            val destFile = File(folder, "AUD_${System.currentTimeMillis()}.m4a")
            sourceFile.copyTo(destFile, overwrite = true)
            Log.d(TAG, "Voice note saved to dedicated storage: ${destFile.absolutePath}")
            return destFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving voice note to dedicated folder: ${e.message}")
            return null
        }
    }

    fun savePhotoToDedicatedFolder(context: Context, bytes: ByteArray, extension: String = "jpg"): File? {
        try {
            if (bytes.isEmpty()) return null
            val folder = getImagesFolder(context)
            val destFile = File(folder, "IMG_${System.currentTimeMillis()}.$extension")
            FileOutputStream(destFile).use { it.write(bytes) }
            Log.d(TAG, "Photo saved to dedicated storage: ${destFile.absolutePath}")

            // Add to MediaStore gallery if applicable
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val values = ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, destFile.name)
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "${Environment.DIRECTORY_PICTURES}/Mikayala")
                }
                context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)?.let { uri ->
                    context.contentResolver.openOutputStream(uri)?.use { os ->
                        os.write(bytes)
                    }
                }
            }
            return destFile
        } catch (e: Exception) {
            Log.e(TAG, "Error saving photo to dedicated folder: ${e.message}")
            return null
        }
    }
}
