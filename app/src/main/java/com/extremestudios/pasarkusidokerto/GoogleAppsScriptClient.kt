package com.extremestudios.pasarkusidokerto

import org.json.JSONObject
import org.json.JSONArray
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class AppsScriptResult(
    val success: Boolean,
    val message: String
)

data class MerchantStatusResult(
    val found: Boolean,
    val message: String,
    val shopName: String = "",
    val category: String = ""
)

data class PromoData(
    val timestamp: String,
    val shopName: String,
    val whatsapp: String,
    val description: String,
    val imageUrl: String,
    val type: String
)

class GoogleAppsScriptClient(private val endpointUrl: String) {
    fun isConfigured(): Boolean = endpointUrl.isNotBlank()

    fun submit(payload: MerchantRegistrationPayload): AppsScriptResult {
        if (!isConfigured()) {
            return AppsScriptResult(false, "Endpoint Google Apps Script belum disetel.")
        }

        val body = JSONObject()
            .put("nama_toko", payload.shopName)
            .put("nomor_wa", payload.whatsapp)
            .put("kategori", payload.category)
            .put("deskripsi", payload.shortDescription)
            .put("lokasi", payload.locationHint)
            .put("foto_base64", payload.photoBase64)
            .put("foto_filename", payload.photoFilename)
            .toString()

        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            val response = responseText.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            val success = response?.optBoolean("success", false) ?: false
            val message = response?.optString("error")
                ?.takeIf { it.isNotBlank() }
                ?: response?.optString("message")
                ?.takeIf { it.isNotBlank() }
                ?: if (success) "OK" else "Pendaftaran gagal dikirim."

            AppsScriptResult(success, message)
        } catch (error: Exception) {
            AppsScriptResult(false, error.message ?: "Pendaftaran gagal dikirim.")
        } finally {
            connection.disconnect()
        }
    }

    fun checkMerchantStatus(whatsapp: String): MerchantStatusResult {
        if (!isConfigured()) {
            return MerchantStatusResult(false, "Endpoint Google Apps Script belum disetel.")
        }

        val body = JSONObject()
            .put("action", "check_seller")
            .put("nomor_wa", whatsapp)
            .toString()

        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }

        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
            val responseText = if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }

            val response = responseText.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            val success = response?.optBoolean("success", false) ?: false
            val found = success
            val shopName = response?.optString("nama_toko").orEmpty()
            val category = response?.optString("kategori").orEmpty()
            val message = response?.optString("error")
                ?.takeIf { it.isNotBlank() }
                ?: response?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                ?: if (success && found) "Status pedagang ditemukan." else "Nomor ini belum terdaftar sebagai pedagang."

            MerchantStatusResult(success && found, message, shopName, category)
        } catch (error: Exception) {
            MerchantStatusResult(false, error.message ?: "Gagal mengecek status pedagang.")
        } finally {
            connection.disconnect()
        }
    }

    fun postPromo(
        shopName: String,
        whatsapp: String,
        promoDescription: String,
        promoImageBase64: String,
        promoImageFilename: String
    ): AppsScriptResult {
        if (!isConfigured()) {
            return AppsScriptResult(false, "Endpoint Google Apps Script belum disetel.")
        }
        val body = JSONObject()
            .put("action", "post_promo")
            .put("nama_toko", shopName)
            .put("nomor_wa", whatsapp)
            .put("deskripsi_promo", promoDescription)
            .put("promo_image_base64", promoImageBase64)
            .put("promo_image_filename", promoImageFilename)
            .toString()
        return postJson(body, "Promo gagal dikirim.")
    }

    fun getPromos(): Result<List<PromoData>> {
        if (!isConfigured()) {
            return Result.failure(IllegalStateException("Endpoint Google Apps Script belum disetel."))
        }
        val body = JSONObject().put("action", "get_promos").toString()
        return runCatching {
            val responseText = postRaw(body)
            val response = JSONObject(responseText)
            if (!response.optBoolean("success", false)) {
                throw IllegalStateException(response.optString("message", "Gagal mengambil promo."))
            }
            val arr = response.optJSONArray("promos")
            val promos = mutableListOf<PromoData>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val item = arr.optJSONObject(i) ?: continue
                    promos.add(
                        PromoData(
                            timestamp = item.optString("timestamp"),
                            shopName = item.optString("nama_toko"),
                            whatsapp = item.optString("nomor_wa"),
                            description = item.optString("deskripsi_promo"),
                            imageUrl = item.optString("promo_image_url"),
                            type = item.optString("type")
                        )
                    )
                }
            }
            promos
        }
    }

    fun reportItem(
        targetType: String,
        targetName: String,
        targetWhatsapp: String,
        reason: String,
        detail: String
    ): AppsScriptResult {
        if (!isConfigured()) {
            return AppsScriptResult(false, "Endpoint Google Apps Script belum disetel.")
        }
        val body = JSONObject()
            .put("action", "report_item")
            .put("target_type", targetType)
            .put("target_nama", targetName)
            .put("target_wa", targetWhatsapp)
            .put("alasan", reason)
            .put("detail", detail)
            .toString()
        return postJson(body, "Laporan gagal dikirim.")
    }

    fun loginMerchant(username: String, password: String): MerchantLoginResult {
        if (!isConfigured()) {
            return MerchantLoginResult(false, "Endpoint Google Apps Script belum disetel.")
        }

        return try {
            val response = JSONObject(postRaw(
                JSONObject()
                    .put("action", "merchant_login")
                    .put("username", username)
                    .put("password", password)
                    .toString()
            ))
            val success = response.optBoolean("success", false)
            MerchantLoginResult(
                success = success,
                message = response.optString("message", if (success) "Login berhasil." else "Login gagal."),
                username = response.optString("username"),
                sessionToken = response.optString("session_token"),
                shopName = response.optString("nama_toko"),
                whatsapp = response.optString("nomor_wa"),
                category = response.optString("kategori"),
                description = response.optString("deskripsi")
            )
        } catch (error: Exception) {
            MerchantLoginResult(false, error.message ?: "Login gagal.")
        }
    }

    fun updateMerchantProducts(sessionToken: String, items: List<MerchantItemInput>): AppsScriptResult {
        if (!isConfigured()) {
            return AppsScriptResult(false, "Endpoint Google Apps Script belum disetel.")
        }
        val productArray = JSONArray()
        items.forEach { item ->
            productArray.put(
                JSONObject()
                    .put("nama_barang", item.name)
                    .put("harga", item.price)
            )
        }
        return postJson(
            JSONObject()
                .put("action", "update_my_store")
                .put("session_token", sessionToken)
                .put("items", productArray)
                .toString(),
            "Gagal menyimpan daftar barang."
        )
    }

    private fun postJson(body: String, fallbackError: String): AppsScriptResult {
        return try {
            val responseText = postRaw(body)
            val response = responseText.takeIf { it.isNotBlank() }?.let { JSONObject(it) }
            val success = response?.optBoolean("success", false) ?: false
            val message = response?.optString("error")
                ?.takeIf { it.isNotBlank() }
                ?: response?.optString("message")
                    ?.takeIf { it.isNotBlank() }
                ?: if (success) "OK" else fallbackError
            AppsScriptResult(success, message)
        } catch (error: Exception) {
            AppsScriptResult(false, error.message ?: fallbackError)
        }
    }

    private fun postRaw(body: String): String {
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15000
            readTimeout = 20000
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
        }
        return try {
            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { writer ->
                writer.write(body)
            }
            if (connection.responseCode in 200..299) {
                connection.inputStream.bufferedReader().use { it.readText() }
            } else {
                connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
        } finally {
            connection.disconnect()
        }
    }
}
