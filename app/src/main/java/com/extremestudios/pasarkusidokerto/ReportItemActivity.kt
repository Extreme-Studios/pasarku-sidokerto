package com.extremestudios.pasarkusidokerto

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class ReportItemActivity : AppCompatActivity() {
    private val orange = Color.rgb(249, 115, 22)
    private val ink = Color.rgb(31, 41, 55)
    private val muted = Color.rgb(107, 114, 128)
    private val softBg = Color.rgb(248, 250, 252)
    private val stroke = Color.rgb(229, 231, 235)

    private lateinit var reasonInput: AutoCompleteTextView
    private lateinit var detailInput: EditText
    private lateinit var submitButton: MaterialButton

    private val appsScriptClient by lazy {
        GoogleAppsScriptClient(BuildConfig.GOOGLE_APPS_SCRIPT_URL)
    }

    private val targetType by lazy { intent.getStringExtra(EXTRA_TARGET_TYPE).orEmpty() }
    private val targetName by lazy { intent.getStringExtra(EXTRA_TARGET_NAME).orEmpty() }
    private val targetWhatsapp by lazy { intent.getStringExtra(EXTRA_TARGET_WHATSAPP).orEmpty() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (targetType.isBlank() || targetWhatsapp.isBlank()) {
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

        container.addView(text("Laporkan Item", 24f, ink, Typeface.BOLD))
        container.addView(text(targetName.ifBlank { "Pedagang Pasarku" }, 14f, muted, Typeface.BOLD).apply {
            setPadding(0, dp(6), 0, dp(14))
        })

        val formCard = card()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(16))
        }
        formCard.addView(form)
        container.addView(formCard)

        reasonInput = reasonField(form)
        detailInput = inputField(form, "Detail Tambahan (Opsional)", false, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE).apply {
            minLines = 4
            maxLines = 6
            isSingleLine = false
            hint = "Tambahkan detail jika perlu"
        }

        submitButton = primaryButton("Kirim Laporan") {
            submitReport()
        }
        container.addView(submitButton)
        return scroll
    }

    private fun submitReport() {
        val reason = reasonInput.text.toString().trim()
        val detail = detailInput.text.toString().trim()

        if (reason.isBlank()) {
            reasonInput.error = "Pilih alasan laporan"
            reasonInput.requestFocus()
            return
        }

        submitButton.isEnabled = false
        submitButton.text = "Mengirim laporan..."
        Thread {
            val result = appsScriptClient.reportItem(
                targetType = targetType,
                targetName = targetName.ifBlank { "Pedagang Pasarku" },
                targetWhatsapp = targetWhatsapp,
                reason = reason,
                detail = detail
            )
            runOnUiThread {
                submitButton.isEnabled = true
                submitButton.text = "Kirim Laporan"
                if (result.success) {
                    AlertDialog.Builder(this)
                        .setTitle("Berhasil")
                        .setMessage("Laporan berhasil dikirim. Terima kasih.")
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

    private fun reasonField(parent: LinearLayout): AutoCompleteTextView {
        parent.addView(text("Alasan *", 14f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(8), 0, dp(6))
        })
        val reasons = listOf("Spam", "Tidak pantas", "Penipuan", "Informasi salah", "Lainnya")
        val field = AutoCompleteTextView(this).apply {
            setTextColor(ink)
            setHintTextColor(muted)
            hint = "Pilih alasan"
            inputType = InputType.TYPE_NULL
            keyListener = null
            isFocusable = true
            isClickable = true
            setPadding(dp(12), dp(12), dp(12), dp(12))
            setAdapter(ArrayAdapter(this@ReportItemActivity, android.R.layout.simple_list_item_1, reasons))
            setOnClickListener { showDropDown() }
            setOnFocusChangeListener { _, hasFocus -> if (hasFocus) showDropDown() }
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

    private fun text(value: String, sp: Float, color: Int, style: Int): TextView {
        return TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
            setTypeface(Typeface.DEFAULT, style)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_TARGET_TYPE = "target_type"
        const val EXTRA_TARGET_NAME = "target_name"
        const val EXTRA_TARGET_WHATSAPP = "target_whatsapp"
    }
}
