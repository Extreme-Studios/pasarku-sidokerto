package com.extremestudios.pasarkusidokerto

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class SellerPromoActivity : AppCompatActivity() {
    private val orange = Color.rgb(249, 115, 22)
    private val ink = Color.rgb(31, 41, 55)
    private val muted = Color.rgb(107, 114, 128)
    private val softBg = Color.rgb(248, 250, 252)
    private val stroke = Color.rgb(229, 231, 235)

    private lateinit var shopNameInput: EditText
    private lateinit var whatsappInput: EditText
    private lateinit var promoInput: EditText
    private lateinit var photoPreview: ImageView
    private lateinit var photoStatus: TextView
    private lateinit var submitButton: MaterialButton
    private lateinit var store: MerchantRegistrationStore

    private var tempCameraUri: Uri? = null
    private var processedPhoto: ProcessedPhoto? = null
    private var isProcessingPhoto = false

    private val appsScriptClient by lazy {
        GoogleAppsScriptClient(BuildConfig.GOOGLE_APPS_SCRIPT_URL)
    }

    private val pickFromGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) processSelectedPhoto(uri)
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            toast("Izin kamera diperlukan untuk mengambil gambar promo.")
        }
    }

    private val capturePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            processSelectedPhoto(tempCameraUri!!)
        } else {
            toast("Pengambilan gambar dibatalkan.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MerchantRegistrationStore(this)
        if (!isSellerModeActive()) {
            toast("Fitur ini hanya untuk pedagang.")
            finish()
            return
        }
        setContentView(buildRoot())
    }

    private fun buildRoot(): View {
        val scroll = ScrollView(this).apply { setBackgroundColor(softBg) }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
        }
        scroll.addView(container)

        container.addView(text("Pasang Promo", 24f, ink, Typeface.BOLD))
        container.addView(text("Kamu bisa kirim promo teks, promo gambar, atau gabungan keduanya.", 14f, muted, Typeface.NORMAL).apply {
            setPadding(0, dp(6), 0, dp(14))
        })

        val formCard = card()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        formCard.addView(form)
        container.addView(formCard)

        shopNameInput = inputField(form, "Nama Toko", true, InputType.TYPE_CLASS_TEXT).apply {
            setText(store.getRegisteredShopName().ifBlank { "Pedagang Pasarku" })
            isFocusable = false
            isClickable = false
            keyListener = null
        }
        whatsappInput = inputField(form, "Nomor WhatsApp", true, InputType.TYPE_CLASS_PHONE).apply {
            setText(store.getRegisteredWhatsapp())
            isFocusable = false
            isClickable = false
            keyListener = null
        }
        promoInput = inputField(form, "Deskripsi Promo (Opsional)", false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            hint = "Contoh: Hari ini ayam potong diskon 20%, stok terbatas sampai sore."
            minLines = 4
            maxLines = 6
            isSingleLine = false
        }

        addPhotoSection(form)

        submitButton = primaryButton("Pasang Promo") {
            submitPromo()
        }
        container.addView(submitButton)
        return scroll
    }

    private fun addPhotoSection(parent: LinearLayout) {
        parent.addView(text("Upload Gambar Promo (Opsional)", 14f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(10), 0, dp(6))
        })

        photoPreview = ImageView(this).apply {
            setBackgroundColor(Color.rgb(245, 245, 245))
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
        }
        parent.addView(photoPreview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(200)
        ))

        photoStatus = text("Belum ada gambar promo dipilih", 12f, muted, Typeface.NORMAL).apply {
            setPadding(0, dp(6), 0, dp(10))
        }
        parent.addView(photoStatus)

        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        row.addView(outlineButton("Pilih dari Galeri") {
            pickFromGallery.launch("image/*")
        })
        row.addView(outlineButton("Ambil dari Kamera") {
            ensureCameraPermissionThenCapture()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).setMargins(dp(10), 0, 0, 0)
        })
        parent.addView(row)

        parent.addView(text(
            "Boleh kosong jika hanya ingin kirim promo teks. Jika pakai gambar, aplikasi akan kompres otomatis.",
            12f, muted, Typeface.NORMAL
        ).apply { setPadding(0, dp(10), 0, dp(4)) })
    }

    private fun ensureCameraPermissionThenCapture() {
        val granted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchCameraCapture()
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraCapture() {
        tempCameraUri = createTempImageUri()
        val target = tempCameraUri
        if (target == null) {
            toast("Gagal menyiapkan file gambar.")
            return
        }
        capturePhoto.launch(target)
    }

    private fun createTempImageUri(): Uri? {
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "promo-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PasarkuSidokerto")
            }
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun processSelectedPhoto(uri: Uri) {
        isProcessingPhoto = true
        processedPhoto = null
        photoStatus.text = "Gambar promo sedang diproses..."
        photoStatus.setTextColor(muted)

        Thread {
            val result = runCatching { PhotoCompressor.process(this, uri) }.getOrNull()
            runOnUiThread {
                isProcessingPhoto = false
                if (result == null) {
                    photoPreview.setImageDrawable(null)
                    photoStatus.text = "Gambar promo gagal diproses. Coba pilih gambar lain."
                    photoStatus.setTextColor(Color.rgb(220, 38, 38))
                    toast("Gambar promo gagal diproses.")
                } else {
                    processedPhoto = result
                    photoPreview.setImageBitmap(result.bitmap)
                    val sizeKb = result.jpegBytes.size / 1024
                    photoStatus.text = "Gambar siap dikirim (${sizeKb}KB, JPEG)"
                    photoStatus.setTextColor(Color.rgb(22, 163, 74))
                }
            }
        }.start()
    }

    private fun submitPromo() {
        if (!isSellerModeActive()) {
            toast("Fitur ini hanya untuk pedagang.")
            return
        }
        if (isProcessingPhoto) {
            toast("Gambar sedang diproses. Tunggu sebentar.")
            return
        }

        val shopName = store.getRegisteredShopName().ifBlank { "Pedagang Pasarku" }.trim()
        val whatsapp = store.getRegisteredWhatsapp().trim()
        val promoDescription = promoInput.text.toString().trim()
        val photo = processedPhoto

        if (shopName.isEmpty()) {
            toast("Nama toko tidak boleh kosong.")
            return
        }
        if (whatsapp.isEmpty()) {
            toast("Nomor WhatsApp tidak boleh kosong.")
            return
        }
        if (promoDescription.isBlank() && photo == null) {
            toast("Isi deskripsi promo atau upload gambar promo.")
            return
        }

        submitButton.isEnabled = false
        submitButton.text = "Mengirim promo..."
        Thread {
            val result = appsScriptClient.postPromo(
                shopName = shopName,
                whatsapp = whatsapp,
                promoDescription = promoDescription,
                promoImageBase64 = photo?.base64.orEmpty(),
                promoImageFilename = if (photo != null) buildPromoFilename(shopName) else ""
            )
            runOnUiThread {
                submitButton.isEnabled = true
                submitButton.text = "Pasang Promo"
                if (result.success) {
                    AlertDialog.Builder(this)
                        .setTitle("Berhasil")
                        .setMessage("Promo berhasil dipasang.")
                        .setPositiveButton("OK") { _, _ ->
                            setResult(RESULT_OK)
                            finish()
                        }
                        .setCancelable(false)
                        .show()
                } else {
                    AlertDialog.Builder(this)
                        .setTitle("Gagal")
                        .setMessage(result.message)
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    private fun buildPromoFilename(shopName: String): String {
        val safe = shopName.lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "promo" }
        return "${safe}-promo-${System.currentTimeMillis()}.jpg"
    }

    private fun isSellerModeActive(): Boolean {
        return store.isRegistered() && store.getRegisteredShopName().isNotBlank() && store.getRegisteredWhatsapp().isNotBlank()
    }

    private fun inputField(parent: LinearLayout, label: String, required: Boolean, inputType: Int): EditText {
        val title = if (required) "$label *" else label
        parent.addView(text(title, 14f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(8), 0, dp(6))
        })
        val field = EditText(this).apply {
            this.inputType = inputType
            setTextColor(ink)
            setHintTextColor(muted)
            hint = label
            setBackgroundColor(Color.WHITE)
            setPadding(dp(12), dp(12), dp(12), dp(12))
        }
        val fieldCard = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            strokeColor = stroke
            strokeWidth = 1
            setCardBackgroundColor(Color.WHITE)
            addView(field, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        parent.addView(fieldCard)
        return field
    }

    private fun card(): MaterialCardView {
        return MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            strokeColor = stroke
            strokeWidth = 1
            setCardBackgroundColor(Color.WHITE)
        }
    }

    private fun primaryButton(label: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAllCaps = false
            cornerRadius = dp(14)
            setBackgroundColor(orange)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            ).apply {
                topMargin = dp(14)
            }
        }
    }

    private fun outlineButton(label: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = label
            setTextColor(orange)
            textSize = 13f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAllCaps = false
            cornerRadius = dp(14)
            strokeColor = android.content.res.ColorStateList.valueOf(orange)
            strokeWidth = dp(1)
            setBackgroundColor(Color.WHITE)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
    }

    private fun text(value: String, sp: Float, color: Int, style: Int): TextView {
        return TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
            setTypeface(Typeface.DEFAULT, style)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
