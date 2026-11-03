package com.example.kayara.data

import org.json.JSONObject

data class EmailCodeReply(val verified: Boolean, val available: Boolean, val active: Boolean, val retryAfter: Int) {
    companion object {
        fun fromJson(value: JSONObject, status: Boolean) = EmailCodeReply(
            value.opt("verified") == true,
            if (status) value.opt("available") == true else true,
            value.opt("active") == true || value.opt("sent") == true,
            value.optInt("retry_after", 0).coerceIn(0, 86_400))
    }
}
data class EmailSenderStatus(val configured: Boolean, val email: String) {
    companion object {
        fun fromJson(value: JSONObject) = EmailSenderStatus(value.opt("configured") == true, value.optString("email"))
    }
}
