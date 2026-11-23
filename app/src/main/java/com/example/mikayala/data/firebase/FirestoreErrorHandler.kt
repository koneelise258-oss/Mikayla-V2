package com.example.mikayala.data.firebase

import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import org.json.JSONArray
import org.json.JSONObject

enum class OperationType {
    CREATE,
    UPDATE,
    DELETE,
    LIST,
    GET,
    WRITE
}

fun handleFirestoreError(
    error: Throwable,
    operationType: OperationType,
    path: String?
) {
    val currentUser = try {
        Firebase.auth.currentUser
    } catch (_: Exception) {
        null
    }

    val providerArray = JSONArray().apply {
        currentUser?.providerData?.forEach { info ->
            put(
                JSONObject().apply {
                    put("providerId", info.providerId)
                    put("displayName", info.displayName ?: "")
                    put("email", info.email ?: "")
                }
            )
        }
    }

    val payload = JSONObject().apply {
        put("error", error.message ?: error.javaClass.simpleName)
        put("operationType", operationType.name.lowercase())
        put("path", path ?: "")
        put(
            "authInfo",
            JSONObject().apply {
                put("userId", currentUser?.uid ?: "")
                put("email", currentUser?.email ?: "")
                put("emailVerified", currentUser?.isEmailVerified ?: false)
                put("isAnonymous", currentUser?.isAnonymous ?: false)
                put("providerInfo", providerArray)
            }
        )
    }

    Log.e("FirestoreError", payload.toString(), error)
}
