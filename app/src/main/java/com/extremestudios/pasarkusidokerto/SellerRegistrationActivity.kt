package com.extremestudios.pasarkusidokerto

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
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

class SellerRegistrationActivity : AppCompatActivity() {
    private val orange = Color.rgb(249, 115, 22)
    private val orangeDark = Color.rgb(217, 72, 15)
    private val ink = Color.rgb(31, 41, 55)
    private val muted = Color.rgb(107, 114, 128)
    private val softBg = Color.rgb(248, 250, 252)
    private val stroke = Color.rgb(229, 231, 235)

    private lateinit var shopNameInput: EditText
    private lateinit var whatsappInput: EditText
    private lateinit var categoryInput: AutoCompleteTextView
    private lateinit var locationInput: EditText
    private val itemNameInputs = mutableListOf<EditText>()
    private val itemPriceInputs = mutableListOf<EditText>()
    private lateinit var photoPreview: ImageView
    private lateinit var photoStatus: TextView
    private lateinit var submitButton: MaterialButton

    private var tempCameraUri: Uri? = null
    private var processedPhoto: ProcessedPhoto? = null
    private var isProcessingPhoto = false

    private lateinit var store: MerchantRegistrationStore
    private val appsScriptClient by lazy {
        GoogleAppsScriptClient(BuildConfig.GOOGLE_APPS_SCRIPT_URL)
    }

    private val pickFromGallery = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            processSelectedPhoto(uri)
        }
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            launchCameraCapture()
        } else {
            toast("Izin kamera diperlukan untuk mengambil foto.")
        }
    }

    private val capturePhoto = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success && tempCameraUri != null) {
            processSelectedPhoto(tempCameraUri!!)
        } else {
            toast("Pengambilan foto dibatalkan.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MerchantRegistrationStore(this)
        setContentView(buildRoot())
    }

    private fun buildRoot(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(softBg)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
        }
        scroll.addView(container)

        container.addView(text("Pendaftaran Pedagang", 24f, ink, Typeface.BOLD))
        container.addView(text("Isi data warung/toko agar bisa tampil di etalase Pasarku Sidokerto.", 14f, muted, Typeface.NORMAL).apply {
            setPadding(0, dp(6), 0, dp(14))
        })

        val formCard = card()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        formCard.addView(form)
        container.addView(formCard)

        shopNameInput = inputField(form, "Nama Toko / Warung / Stand / Booth", true, InputType.TYPE_CLASS_TEXT)
        whatsappInput = inputField(form, "Nomor WhatsApp", true, InputType.TYPE_CLASS_PHONE)
        categoryInput = categoryField(form)

        locationInput = inputField(form, "Alamat / Patokan (Opsional)", false, InputType.TYPE_CLASS_TEXT).apply {
            hint = "Alamat biasa atau tempel link share lokasi"
        }
        addProductPriceFields(form)
        addPhotoSection(form)

        submitButton = primaryButton("Kirim Pendaftaran") {
            submitRegistration()
        }
        container.addView(submitButton)

        return scroll
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
            toast("Gagal menyiapkan file foto.")
            return
        }
        capturePhoto.launch(target)
    }

    private fun createTempImageUri(): Uri? {
        val resolver = contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "pasarku-${System.currentTimeMillis()}.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/PasarkuSidokerto")
            }
        }
        return resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }

    private fun submitRegistration() {
        val shopName = shopNameInput.text.toString().trim()
        val whatsappRaw = whatsappInput.text.toString().trim()
        val category = categoryInput.text.toString().trim()
        val locationHint = locationInput.text.toString().trim()
        val description = buildProductDescription()

        if (shopName.isEmpty()) {
            shopNameInput.error = "Nama toko wajib diisi"
            shopNameInput.requestFocus()
            return
        }

        if (whatsappRaw.isEmpty()) {
            whatsappInput.error = "Nomor WhatsApp wajib diisi"
            whatsappInput.requestFocus()
            return
        }

        val digitsOnly = whatsappRaw.filter { it.isDigit() }
        if (digitsOnly.length < 10) {
            whatsappInput.error = "Nomor minimal 10 digit"
            whatsappInput.requestFocus()
            return
        }

        if (category.isEmpty()) {
            categoryInput.error = "Kategori wajib dipilih"
            categoryInput.requestFocus()
            return
        }

        if (description == null) {
            return
        }

        if (isProcessingPhoto) {
            toast("Foto sedang diproses. Tunggu sebentar.")
            return
        }

        val photo = processedPhoto
        if (photo == null) {
            toast("Foto toko/gerobak wajib diupload.")
            return
        }

        val payload = MerchantRegistrationPayload(
            shopName = shopName,
            whatsapp = digitsOnly,
            category = category,
            photoBase64 = photo.base64,
            photoMimeType = photo.mimeType,
            photoFilename = buildPhotoFilename(shopName),
            locationHint = locationHint,
            shortDescription = description,
            openHours = "",
            todayPromo = ""
        )

        submitPayload(payload)
    }

    private fun submitPayload(payload: MerchantRegistrationPayload) {
        submitButton.isEnabled = false
        submitButton.text = "Mengirim pendaftaran..."

        Thread {
            val result = try {
                appsScriptClient.submit(payload)
            } catch (_: Exception) {
                AppsScriptResult(false, "Pendaftaran gagal dikirim. Coba lagi nanti.")
            }

            runOnUiThread {
                submitButton.isEnabled = true
                submitButton.text = "Kirim Pendaftaran"

                if (result.success) {
                    store.savePayload(payload)
                    showSuccessDialog()
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

    private fun showSuccessDialog() {
        AlertDialog.Builder(this)
            .setTitle("Berhasil")
            .setMessage("Pendaftaran berhasil dikirim. Data akan dicek terlebih dahulu sebelum tampil di Pasarku Sidokerto.")
            .setPositiveButton("OK") { _, _ ->
                setResult(RESULT_OK)
                finish()
            }
            .setCancelable(false)
            .show()
    }

    private fun processSelectedPhoto(uri: Uri) {
        isProcessingPhoto = true
        processedPhoto = null
        photoStatus.text = "Foto sedang diproses otomatis..."
        photoStatus.setTextColor(muted)

        Thread {
            val result = try {
                PhotoCompressor.process(this, uri)
            } catch (_: Exception) {
                null
            }

            runOnUiThread {
                isProcessingPhoto = false
                if (result == null) {
                    photoPreview.setImageDrawable(null)
                    photoPreview.setBackgroundColor(Color.rgb(245, 245, 245))
                    photoStatus.text = "Foto gagal diproses. Coba pilih foto lain."
                    photoStatus.setTextColor(Color.rgb(220, 38, 38))
                    toast("Foto gagal diproses. Coba pilih foto lain.")
                } else {
                    processedPhoto = result
                    photoPreview.setPadding(0, 0, 0, 0)
                    photoPreview.setImageBitmap(result.bitmap)
                    val sizeKb = result.jpegBytes.size / 1024
                    photoStatus.text = "Foto siap dikirim (${sizeKb}KB, JPEG)"
                    photoStatus.setTextColor(Color.rgb(22, 163, 74))
                }
            }
        }.start()
    }

    private fun buildPhotoFilename(shopName: String): String {
        val safeName = shopName
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
            .ifBlank { "pedagang" }
        return "${safeName}-${System.currentTimeMillis()}.jpg"
    }

    private fun inputField(
        parent: LinearLayout,
        label: String,
        required: Boolean,
        inputType: Int
    ): EditText {
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
            addView(field, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        parent.addView(fieldCard)
        return field
    }

    private fun addProductPriceFields(parent: LinearLayout) {
        parent.addView(text("Daftar Barang dan Harga *", 14f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(12), 0, dp(3))
        })
        parent.addView(text("Isi maksimal 5 barang. Minimal satu barang dan harga wajib diisi.", 12f, muted, Typeface.NORMAL).apply {
            setPadding(0, 0, 0, dp(4))
        })

        repeat(5) { index ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(4), 0, dp(4))
            }
            val itemName = compactInput("Nama barang ${index + 1}", InputType.TYPE_CLASS_TEXT)
            val itemPrice = compactInput("Harga", InputType.TYPE_CLASS_NUMBER)
            row.addView(itemName, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.45f))
            row.addView(itemPrice, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(8)
            })
            parent.addView(row)
            itemNameInputs.add(itemName)
            itemPriceInputs.add(itemPrice)
        }
    }

    private fun compactInput(hintText: String, type: Int): EditText {
        return EditText(this).apply {
            hint = hintText
            inputType = type
            setTextColor(ink)
            setHintTextColor(muted)
            setTextSize(14f)
            setSingleLine(true)
            setPadding(dp(10), dp(10), dp(10), dp(10))
            background = compactFieldBackground()
        }
    }

    private fun compactFieldBackground(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(12).toFloat()
            setColor(Color.WHITE)
            setStroke(dp(1), stroke)
        }
    }

    private fun buildProductDescription(): String? {
        val items = mutableListOf<String>()

        itemNameInputs.indices.forEach { index ->
            val itemName = itemNameInputs[index].text.toString().trim()
            val itemPrice = itemPriceInputs[index].text.toString().trim()

            if (itemName.isBlank() && itemPrice.isBlank()) {
                return@forEach
            }
            if (itemName.isBlank()) {
                itemNameInputs[index].error = "Isi nama barang"
                itemNameInputs[index].requestFocus()
                return null
            }
            if (itemPrice.isBlank()) {
                itemPriceInputs[index].error = "Isi harga"
                itemPriceInputs[index].requestFocus()
                return null
            }

            val price = itemPrice.toLongOrNull()?.let { formatPrice(it) } ?: itemPrice
            items.add("${items.size + 1}. $itemName - Rp$price")
        }

        if (items.isEmpty()) {
            itemNameInputs.first().error = "Isi minimal satu barang"
            itemNameInputs.first().requestFocus()
            return null
        }

        return items.joinToString("\n")
    }

    private fun formatPrice(price: Long): String {
        return String.format(java.util.Locale("id", "ID"), "%,d", price).replace(',', '.')
    }

    private fun categoryField(parent: LinearLayout): AutoCompleteTextView {
        parent.addView(text("Kategori Dagangan *", 14f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(8), 0, dp(6))
        })
        val field = AutoCompleteTextView(this).apply {
            setTextColor(ink)
            setHintTextColor(muted)
            hint = "Pilih kategori"
            inputType = InputType.TYPE_NULL
            isFocusable = true
            isClickable = true
            keyListener = null
            setPadding(dp(12), dp(12), dp(12), dp(12))
            val categories = listOf(
                "Toko", "Warung", "PKL", "Jasa", "Booth"
            )
            setAdapter(ArrayAdapter(this@SellerRegistrationActivity, android.R.layout.simple_list_item_1, categories))
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) showDropDown()
            }
        }
        val fieldCard = MaterialCardView(this).apply {
            radius = dp(14).toFloat()
            strokeColor = stroke
            strokeWidth = 1
            setCardBackgroundColor(Color.WHITE)
            addView(field, ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ))
        }
        parent.addView(fieldCard)
        return field
    }

    private fun addPhotoSection(parent: LinearLayout) {
        parent.addView(text(
            "Upload Foto Toko/Gerobak (Wajib)",
            14f, ink, Typeface.BOLD
        ).apply { setPadding(0, dp(8), 0, dp(6)) })

        photoPreview = ImageView(this).apply {
            setBackgroundColor(Color.rgb(245, 245, 245))
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = true
            setImageDrawable(null)
            setPadding(0, 0, 0, 0)
        }
        parent.addView(photoPreview, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(200)
        ))

        photoStatus = text("Belum ada foto dipilih", 12f, muted, Typeface.NORMAL).apply {
            setPadding(0, dp(6), 0, dp(10))
        }
        parent.addView(photoStatus)

        val photoActionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.START
        }
        photoActionRow.addView(outlineButton("Pilih dari Galeri") {
            pickFromGallery.launch("image/*")
        })
        photoActionRow.addView(outlineButton("Ambil dari Kamera") {
            ensureCameraPermissionThenCapture()
        }.apply {
            (layoutParams as LinearLayout.LayoutParams).setMargins(dp(10), 0, 0, 0)
        })
        parent.addView(photoActionRow)

        parent.addView(text(
            "Upload foto toko/gerobak dari depan. Usahakan terlihat area sekitar agar warga mudah mengenali lokasi.",
            12f, muted, Typeface.NORMAL
        ).apply { setPadding(0, dp(10), 0, dp(6)) })
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
            insetTop = 0
            insetBottom = 0
            gravity = Gravity.CENTER
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
            setTextColor(orangeDark)
            textSize = 13f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAllCaps = false
            cornerRadius = dp(14)
            strokeColor = android.content.res.ColorStateList.valueOf(orange)
            strokeWidth = dp(1)
            setBackgroundColor(Color.WHITE)
            setOnClickListener { onClick() }
            layoutParams = LinearLayout.LayoutParams(
                0,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                1f
            )
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

    private fun hideKeyboardAndClearFocus(view: View) {
        val inputMethodManager = getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(view.windowToken, 0)
        view.clearFocus()
    }
}
