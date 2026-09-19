package com.extremestudios.pasarkusidokerto

import android.content.Context

class MerchantRegistrationStore(context: Context) {
    private val prefs = context.getSharedPreferences("merchant_registration", Context.MODE_PRIVATE)

    fun isRegistered(): Boolean = prefs.getBoolean(KEY_REGISTERED, false)

    fun savePayload(payload: MerchantRegistrationPayload) {
        prefs.edit()
            .putBoolean(KEY_REGISTERED, true)
            .putString(KEY_PAYLOAD, payload.toLocalSummary())
            .putString(KEY_SHOP_NAME, payload.shopName)
            .putString(KEY_WHATSAPP, payload.whatsapp)
            .putString(KEY_CATEGORY, payload.category)
            .apply()
    }

    fun saveRecoveredStatus(whatsapp: String, shopName: String, category: String) {
        prefs.edit()
            .putBoolean(KEY_REGISTERED, true)
            .putString(
                KEY_PAYLOAD,
                listOf(
                    "shopName=$shopName",
                    "whatsapp=$whatsapp",
                    "category=$category",
                    "recovered=true"
                ).joinToString("&")
            )
            .putString(KEY_SHOP_NAME, shopName)
            .putString(KEY_WHATSAPP, whatsapp)
            .putString(KEY_CATEGORY, category)
            .apply()
    }

    fun getRegisteredWhatsapp(): String = prefs.getString(KEY_WHATSAPP, "").orEmpty()
    fun getRegisteredShopName(): String = prefs.getString(KEY_SHOP_NAME, "").orEmpty()

    fun isMerchantLoggedIn(): Boolean {
        return prefs.getString(KEY_SESSION_TOKEN, "").orEmpty().isNotBlank() &&
            getRegisteredShopName().isNotBlank() &&
            getRegisteredWhatsapp().isNotBlank()
    }

    fun saveMerchantSession(session: MerchantLoginResult) {
        prefs.edit()
            .putBoolean(KEY_REGISTERED, true)
            .putString(KEY_SHOP_NAME, session.shopName)
            .putString(KEY_WHATSAPP, session.whatsapp)
            .putString(KEY_CATEGORY, session.category)
            .putString(KEY_USERNAME, session.username)
            .putString(KEY_SESSION_TOKEN, session.sessionToken)
            .putString(KEY_DESCRIPTION, session.description)
            .apply()
    }

    fun getMerchantUsername(): String = prefs.getString(KEY_USERNAME, "").orEmpty()
    fun getMerchantSessionToken(): String = prefs.getString(KEY_SESSION_TOKEN, "").orEmpty()
    fun getMerchantDescription(): String = prefs.getString(KEY_DESCRIPTION, "").orEmpty()

    fun updateMerchantDescription(description: String) {
        prefs.edit().putString(KEY_DESCRIPTION, description).apply()
    }

    fun clearMerchantSession() {
        prefs.edit()
            .remove(KEY_REGISTERED)
            .remove(KEY_SHOP_NAME)
            .remove(KEY_WHATSAPP)
            .remove(KEY_CATEGORY)
            .remove(KEY_USERNAME)
            .remove(KEY_SESSION_TOKEN)
            .remove(KEY_DESCRIPTION)
            .apply()
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private companion object {
        const val KEY_REGISTERED = "registered"
        const val KEY_PAYLOAD = "payload"
        const val KEY_SHOP_NAME = "shop_name"
        const val KEY_WHATSAPP = "whatsapp"
        const val KEY_CATEGORY = "category"
        const val KEY_USERNAME = "merchant_username"
        const val KEY_SESSION_TOKEN = "merchant_session_token"
        const val KEY_DESCRIPTION = "merchant_description"
    }
}

data class MerchantLoginResult(
    val success: Boolean,
    val message: String,
    val username: String = "",
    val sessionToken: String = "",
    val shopName: String = "",
    val whatsapp: String = "",
    val category: String = "",
    val description: String = ""
)

data class MerchantItemInput(
    val name: String,
    val price: String
)

data class MerchantRegistrationPayload(
    val shopName: String,
    val whatsapp: String,
    val category: String,
    val photoBase64: String,
    val photoMimeType: String,
    val photoFilename: String,
    val locationHint: String,
    val shortDescription: String,
    val openHours: String,
    val todayPromo: String
) {
    fun toLocalSummary(): String {
        return listOf(
            "shopName=$shopName",
            "whatsapp=$whatsapp",
            "category=$category",
            "photoFilename=$photoFilename",
            "locationHint=$locationHint",
            "shortDescription=$shortDescription",
            "openHours=$openHours",
            "todayPromo=$todayPromo"
        ).joinToString("&")
    }
}
