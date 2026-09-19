package com.extremestudios.pasarkusidokerto

import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

class MerchantLoginActivity : AppCompatActivity() {
    private val orange = Color.rgb(249, 115, 22)
    private val orangeDark = Color.rgb(217, 72, 15)
    private val ink = Color.rgb(31, 41, 55)
    private val muted = Color.rgb(107, 114, 128)
    private val stroke = Color.rgb(229, 231, 235)
    private lateinit var store: MerchantRegistrationStore
    private val client by lazy { GoogleAppsScriptClient(BuildConfig.GOOGLE_APPS_SCRIPT_URL) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = MerchantRegistrationStore(this)

        val root = ScrollView(this).apply { setBackgroundColor(Color.rgb(248, 250, 252)) }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(24), dp(18), dp(28))
        }
        content.addView(title("Login Pedagang", 26f, ink))
        content.addView(label("Masuk untuk mengelola daftar barang dan harga warung Anda."))

        val card = card()
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        val username = field("Username", InputType.TYPE_CLASS_TEXT)
        val password = field("Password", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD)
        val login = primaryButton("Login Pedagang")
        form.addView(fieldLabel("Username"))
        form.addView(username)
        form.addView(fieldLabel("Password").apply { setPadding(0, dp(12), 0, dp(4)) })
        form.addView(password)
        form.addView(login.apply { setPadding(0, dp(12), 0, 0) })
        card.addView(form)
        content.addView(card)

        login.setOnClickListener {
            val userValue = username.text.toString().trim().lowercase()
            val passwordValue = password.text.toString()
            if (userValue.isBlank()) {
                username.error = "Username wajib diisi"
                return@setOnClickListener
            }
            if (passwordValue.isBlank()) {
                password.error = "Password wajib diisi"
                return@setOnClickListener
            }
            login.isEnabled = false
            login.text = "Memeriksa akun..."
            Thread {
                val result = client.loginMerchant(userValue, passwordValue)
                runOnUiThread {
                    if (result.success && result.sessionToken.isNotBlank()) {
                        store.saveMerchantSession(result)
                        setResult(RESULT_OK)
                        finish()
                    } else {
                        login.isEnabled = true
                        login.text = "Login Pedagang"
                        AlertDialog.Builder(this)
                            .setTitle("Login gagal")
                            .setMessage(result.message)
                            .setPositiveButton("OK", null)
                            .show()
                    }
                }
            }.start()
        }

        root.addView(content)
        setContentView(root)
    }

    private fun card(): MaterialCardView = MaterialCardView(this).apply {
        radius = dp(18).toFloat()
        cardElevation = dp(2).toFloat()
        strokeColor = stroke
        strokeWidth = 1
        setCardBackgroundColor(Color.WHITE)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun field(hint: String, inputType: Int): EditText = EditText(this).apply {
        this.hint = hint
        this.inputType = inputType
        setSingleLine(true)
        setTextColor(ink)
        setHintTextColor(muted)
        setPadding(dp(14), dp(4), dp(14), dp(4))
        background = android.graphics.drawable.GradientDrawable().apply {
            setColor(Color.WHITE)
            setStroke(dp(1), stroke)
            cornerRadius = dp(12).toFloat()
        }
    }

    private fun primaryButton(value: String): MaterialButton = MaterialButton(this).apply {
        text = value
        isAllCaps = false
        setTextColor(Color.WHITE)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setBackgroundColor(orange)
        cornerRadius = dp(14)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    private fun title(value: String, size: Float, color: Int) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(color)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
    }

    private fun label(value: String) = TextView(this).apply {
        text = value
        textSize = 15f
        setTextColor(muted)
        setPadding(0, dp(8), 0, dp(18))
    }

    private fun fieldLabel(value: String) = TextView(this).apply {
        text = value
        textSize = 14f
        setTextColor(ink)
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setPadding(0, 0, 0, dp(4))
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
