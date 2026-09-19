package com.extremestudios.pasarkusidokerto

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class ManageMerchantStoreActivity : AppCompatActivity() {
    private val orange = Color.rgb(249, 115, 22)
    private val ink = Color.rgb(31, 41, 55)
    private val muted = Color.rgb(107, 114, 128)
    private val stroke = Color.rgb(229, 231, 235)
    private lateinit var store: MerchantRegistrationStore
    private val client by lazy { GoogleAppsScriptClient(BuildConfig.GOOGLE_APPS_SCRIPT_URL) }
    private val itemInputs = mutableListOf<EditText>()
    private val priceInputs = mutableListOf<EditText>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MerchantRegistrationStore(this)
        if (!store.isMerchantLoggedIn()) {
            finish()
            return
        }

        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(248, 250, 252)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(28))
        }
        content.addView(title("Kelola Warung", 26f, ink))
        content.addView(label("Ubah daftar barang dan harga milik warung Anda saja."))

        val identityCard = card()
        val identity = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        identity.addView(fieldLabel("Nama Toko"))
        identity.addView(readOnly(store.getRegisteredShopName()))
        identity.addView(fieldLabel("Username").apply { setPadding(0, dp(12), 0, dp(4)) })
        identity.addView(readOnly(store.getMerchantUsername()))
        identityCard.addView(identity)
        content.addView(identityCard)

        val productsCard = card()
        val products = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        products.addView(fieldLabel("Daftar Barang dan Harga"))
        products.addView(label("Maksimal 5 barang. Setiap baris yang diisi wajib memiliki nama dan harga.").apply {
            setPadding(0, dp(2), 0, dp(8))
        })
        val savedItems = parseDescription(store.getMerchantDescription())
        repeat(5) { index ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val item = input("Nama barang ${index + 1}", InputType.TYPE_CLASS_TEXT)
            val price = input("Harga", InputType.TYPE_CLASS_NUMBER)
            savedItems.getOrNull(index)?.let {
                item.setText(it.name)
                price.setText(it.price)
            }
            row.addView(item, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.45f))
            row.addView(price, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { leftMargin = dp(8) })
            products.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
            itemInputs.add(item)
            priceInputs.add(price)
        }
        productsCard.addView(products)
        content.addView(productsCard)

        val save = primaryButton("Simpan Perubahan")
        content.addView(save)
        save.setOnClickListener { saveProducts(save) }
        root.addView(content)
        setContentView(root)
    }

    private fun saveProducts(saveButton: MaterialButton) {
        val items = mutableListOf<MerchantItemInput>()
        itemInputs.indices.forEach { index ->
            val name = itemInputs[index].text.toString().trim()
            val price = priceInputs[index].text.toString().trim()
            if (name.isBlank() && price.isBlank()) return@forEach
            if (name.isBlank()) {
                itemInputs[index].error = "Isi nama barang"
                itemInputs[index].requestFocus()
                return
            }
            if (price.isBlank()) {
                priceInputs[index].error = "Isi harga"
                priceInputs[index].requestFocus()
                return
            }
            items.add(MerchantItemInput(name, price))
        }
        if (items.isEmpty()) {
            itemInputs.first().error = "Isi minimal satu barang"
            return
        }
        saveButton.isEnabled = false
        saveButton.text = "Menyimpan..."
        Thread {
            val result = client.updateMerchantProducts(store.getMerchantSessionToken(), items)
            runOnUiThread {
                saveButton.isEnabled = true
                saveButton.text = "Simpan Perubahan"
                if (result.success) {
                    store.updateMerchantDescription(items.joinToString("\n") { "${it.name} - Rp ${formatPrice(it.price)}" })
                    Toast.makeText(this, "Daftar barang berhasil diperbarui.", Toast.LENGTH_LONG).show()
                    setResult(RESULT_OK)
                } else {
                    Toast.makeText(this, result.message, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun parseDescription(description: String): List<MerchantItemInput> {
        val pattern = Regex("^(?:\\d+\\.\\s*)?(.*?)\\s*-\\s*Rp\\s*(.+)$", RegexOption.IGNORE_CASE)
        return description.lineSequence().mapNotNull { line ->
            pattern.matchEntire(line.trim())?.let { MerchantItemInput(it.groupValues[1].trim(), it.groupValues[2].replace(".", "").trim()) }
        }.take(5).toList()
    }

    private fun formatPrice(value: String): String {
        val digits = value.filter { it.isDigit() }
        return digits.reversed().chunked(3).joinToString(".").reversed().ifBlank { value }
    }

    private fun card(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(18).toFloat(); cardElevation = dp(2).toFloat(); strokeColor = stroke; strokeWidth = 1; setCardBackgroundColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
    }

    private fun input(hint: String, inputType: Int): EditText = EditText(this).apply {
        this.hint = hint; this.inputType = inputType; setSingleLine(true); setTextColor(ink); setHintTextColor(muted); setPadding(dp(12), dp(4), dp(12), dp(4))
        background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.WHITE); setStroke(dp(1), stroke); cornerRadius = dp(12).toFloat() }
    }

    private fun readOnly(value: String): TextView = TextView(this).apply {
        text = value; textSize = 16f; setTextColor(ink); setPadding(dp(14), dp(12), dp(14), dp(12))
        background = android.graphics.drawable.GradientDrawable().apply { setColor(Color.rgb(249, 250, 251)); setStroke(dp(1), stroke); cornerRadius = dp(12).toFloat() }
    }

    private fun primaryButton(value: String): MaterialButton = MaterialButton(this).apply {
        text = value; isAllCaps = false; setTextColor(Color.WHITE); setTypeface(Typeface.DEFAULT, Typeface.BOLD); setBackgroundColor(orange); cornerRadius = dp(14)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun title(value: String, size: Float, color: Int) = TextView(this).apply { text = value; textSize = size; setTextColor(color); setTypeface(Typeface.DEFAULT, Typeface.BOLD) }
    private fun label(value: String) = TextView(this).apply { text = value; textSize = 14f; setTextColor(muted); setPadding(0, dp(8), 0, dp(16)) }
    private fun fieldLabel(value: String) = TextView(this).apply { text = value; textSize = 14f; setTextColor(ink); setTypeface(Typeface.DEFAULT, Typeface.BOLD); setPadding(0, 0, 0, dp(4)) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
