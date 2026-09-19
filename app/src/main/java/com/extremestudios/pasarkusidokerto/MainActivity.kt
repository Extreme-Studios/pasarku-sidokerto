package com.extremestudios.pasarkusidokerto

import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.BitmapFactory
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.app.Dialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class MainActivity : AppCompatActivity() {
    private val orange = Color.rgb(249, 115, 22)
    private val orangeDark = Color.rgb(217, 72, 15)
    private val gold = Color.rgb(245, 158, 11)
    private val ink = Color.rgb(31, 41, 55)
    private val muted = Color.rgb(107, 114, 128)
    private val softBg = Color.rgb(248, 250, 252)
    private val stroke = Color.rgb(229, 231, 235)
    private val splashDelayMs = 2400L
    private val admobBannerUnitId = "ca-app-pub-8451272966172172/9935515550"

    private lateinit var contentFrame: FrameLayout
    private lateinit var bottomNavigation: BottomNavigationView
    private lateinit var prefs: MerchantRegistrationStore
    private val appsScriptClient by lazy {
        GoogleAppsScriptClient(BuildConfig.GOOGLE_APPS_SCRIPT_URL)
    }
    private var homeSellers: List<SellerData> = emptyList()
    private var homeState: HomeState = HomeState.LOADING
    private var promoItems: List<PromoData> = emptyList()
    private var promoState: PromoState = PromoState.LOADING
    private var selectedCategory: String = "Semua"
    private var searchQuery: String = ""
    private var isRefreshingHome: Boolean = false
    private var isRefreshingPromo: Boolean = false

    private val registrationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        homeState = HomeState.LOADING
        if (bottomNavigation.selectedItemId == R.id.nav_home) {
            showHome()
        } else if (bottomNavigation.selectedItemId == R.id.nav_about) {
            showAbout()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        prefs = MerchantRegistrationStore(this)
        showCustomSplash()
        Handler(Looper.getMainLooper()).postDelayed({
            showMainUi()
        }, splashDelayMs)
    }

    private fun showMainUi() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(softBg)
        }

        contentFrame = FrameLayout(this)
        root.addView(
            contentFrame,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )

        addAdBanner(root)

        bottomNavigation = BottomNavigationView(this).apply {
            setBackgroundColor(Color.WHITE)
            itemIconTintList = navColors()
            itemTextColor = navColors()
            inflateMenu(R.menu.bottom_nav_menu)
            selectedItemId = R.id.nav_home
            setOnItemSelectedListener { item ->
                when (item.itemId) {
                    R.id.nav_home -> {
                        showHome()
                        true
                    }
                    R.id.nav_promo -> {
                        showAdsBoard()
                        true
                    }
                    R.id.nav_about -> {
                        showAbout()
                        true
                    }
                    else -> false
                }
            }
        }

        root.addView(
            bottomNavigation,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        )
        setContentView(root)
        showHome()
    }

    private fun addAdBanner(root: LinearLayout) {
        val adView = AdView(this).apply {
            adUnitId = admobBannerUnitId
            setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                this@MainActivity,
                (resources.displayMetrics.widthPixels / resources.displayMetrics.density).toInt()
            ))
        }
        root.addView(adView, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        MobileAds.initialize(this) {}
        adView.loadAd(AdRequest.Builder().build())
    }

    private fun showCustomSplash() {
        val splashRoot = FrameLayout(this).apply {
            setBackgroundColor(orange)
        }

        val splashImage = ImageView(this).apply {
            setImageResource(R.drawable.splash)
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        splashRoot.addView(
            splashImage,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )

        val bottomInfo = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), 0, dp(28), dp(44))
        }
        bottomInfo.addView(text("Powered By Extreme Studios", 12f, Color.WHITE, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            alpha = 0.9f
        })
        bottomInfo.addView(text("Version ${BuildConfig.VERSION_NAME}", 11f, Color.WHITE, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER
            alpha = 0.82f
            setPadding(0, dp(3), 0, dp(12))
        })

        val loadingLine = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            isIndeterminate = true
            indeterminateTintList = ColorStateList.valueOf(Color.WHITE)
            progressTintList = ColorStateList.valueOf(Color.WHITE)
            progressBackgroundTintList = ColorStateList.valueOf(Color.argb(65, 255, 255, 255))
            alpha = 0.92f
        }
        bottomInfo.addView(loadingLine, LinearLayout.LayoutParams(dp(220), dp(4)))

        splashRoot.addView(
            bottomInfo,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            )
        )

        setContentView(splashRoot)
    }

    private fun navColors(): ColorStateList {
        val states = arrayOf(
            intArrayOf(android.R.attr.state_checked),
            intArrayOf(-android.R.attr.state_checked)
        )
        val colors = intArrayOf(orange, muted)
        return ColorStateList(states, colors)
    }

    private fun showHome() {
        val page = pageContainer(::refreshHome, isRefreshingHome)
        page.addView(homeTopBar())
        page.addView(searchBar())
        page.addView(sectionTitle("Kategori Dagangan"))
        page.addView(categories())
        page.addView(sectionTitle("Pedagang"))
        when (homeState) {
            HomeState.LOADING -> page.addView(loadingStateCard())
            HomeState.ERROR -> page.addView(errorStateCard())
            HomeState.READY -> {
                val filteredSellers = homeSellers.filter { seller ->
                    val categoryMatch = selectedCategory == "Semua" || seller.category.equals(selectedCategory, ignoreCase = true)
                    val text = buildString {
                        append(seller.shopName)
                        append(" ")
                        append(seller.category)
                        append(" ")
                        append(seller.description)
                        append(" ")
                        append(seller.location)
                    }
                    val queryMatch = searchQuery.isBlank() || text.contains(searchQuery, ignoreCase = true)
                    categoryMatch && queryMatch
                }

                if (filteredSellers.isEmpty()) {
                    page.addView(infoCard("Belum ada pedagang terdaftar.", "Data akan tampil setelah ada pedagang yang disetujui."))
                } else {
                    filteredSellers.forEach { seller ->
                        page.addView(sellerCard(seller))
                    }
                }
            }
        }
        setPage(page)
        if (homeState == HomeState.LOADING) {
            fetchSellers()
        }
    }

    private fun homeTopBar(): View {
        val wrapper = vertical(dp(12)).apply {
            setPadding(dp(18), dp(20), dp(18), dp(22))
            background = gradient(orangeDark, orange, 0)
        }

        wrapper.addView(text("Pasarku Sidokerto", 26f, Color.WHITE, Typeface.BOLD))
        wrapper.addView(text("Etalase digital warga Sidokerto", 14f, Color.WHITE, Typeface.NORMAL).apply {
            alpha = 0.9f
        })
        return wrapper
    }

    private fun showPromoBoard() {
        val page = pageContainer(::refreshPromo, isRefreshingPromo)
        page.addView(topBar("Papan Promo", "Informasi promo pedagang Sidokerto"))

        when (promoState) {
            PromoState.LOADING -> page.addView(loadingStateCard("Mengambil data promo..."))
            PromoState.ERROR -> page.addView(promoErrorCard())
            PromoState.READY -> {
                if (promoItems.isEmpty()) {
                    page.addView(infoCard("Belum ada promo hari ini.", "Silakan cek lagi nanti."))
                } else {
                    promoItems.forEach { promo ->
                        page.addView(promoCard(promo))
                    }
                }
            }
        }
        setPage(page)
        if (promoState == PromoState.LOADING) {
            fetchPromos()
        }
    }

    private fun showAdsBoard() {
        val page = pageContainer()
        page.addView(topBar("Iklan", "Ruang promosi pilihan warga Sidokerto"))
        page.addView(infoCard(
            "Iklan Premium",
            "Ruang ini disiapkan untuk pedagang yang ingin menampilkan dagangannya sebagai iklan premium."
        ))
        setPage(page)
    }

    private fun showAbout() {
        val page = pageContainer()
        page.addView(topBar("About", "Tentang Pasarku Sidokerto"))

        val card = card()
        val inner = vertical(dp(18))
        inner.addView(text("Pasarku Sidokerto", 24f, ink, Typeface.BOLD))
        inner.addView(text(
            "Aplikasi ini adalah etalase digital warga Sidokerto. Semua pengguna aplikasi dapat melihat dagangan dan promo warga dalam satu tempat.",
            15f, muted, Typeface.NORMAL
        ).apply { setPadding(0, dp(10), 0, dp(16)) })

        val kritiksaran = MaterialButton(this).apply {
            text = "Kritik dan Saran"
            setTextColor(orangeDark)
            textSize = 13f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAllCaps = false
            cornerRadius = dp(14)
            strokeColor = ColorStateList.valueOf(orange)
            strokeWidth = dp(1)
            setBackgroundColor(Color.WHITE)
            setOnClickListener { startActivity(Intent(this@MainActivity, FeedbackActivity::class.java)) }
        }
        inner.addView(kritiksaran)

        inner.addView(text(
            "Aplikasi hanya menampilkan informasi dagangan. Transaksi dilakukan langsung antara pembeli dan pedagang melalui WhatsApp.",
            13f, muted, Typeface.NORMAL
        ).apply { setPadding(0, dp(18), 0, 0) })

        card.addView(inner)
        page.addView(card)
        setPage(page)
    }

    private fun pageContainer(
        onRefresh: (() -> Unit)? = null,
        refreshing: Boolean = false
    ): LinearLayout {
        val scroll = ScrollView(this).apply { isFillViewport = true }
        val container: View = if (onRefresh == null) {
            scroll
        } else {
            SwipeRefreshLayout(this).apply {
                setColorSchemeColors(orange, orangeDark)
                isRefreshing = refreshing
                setOnRefreshListener { onRefresh() }
                addView(scroll)
            }
        }
        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(18))
            tag = container
        }
        scroll.addView(page)
        return page
    }

    private fun setPage(page: LinearLayout) {
        contentFrame.removeAllViews()
        contentFrame.addView(page.tag as View)
    }

    private fun topBar(title: String, subtitle: String): View {
        val wrapper = vertical(dp(18)).apply {
            setPadding(dp(18), dp(20), dp(18), dp(22))
            background = gradient(orangeDark, orange, 0)
        }
        wrapper.addView(text(title, 26f, Color.WHITE, Typeface.BOLD).apply {
            setPadding(0, 0, 0, dp(3))
        })
        wrapper.addView(text(subtitle, 14f, Color.WHITE, Typeface.NORMAL).apply {
            alpha = 0.9f
        })
        return wrapper
    }

    private fun searchBar(): View {
        val search = card().apply { cardElevation = dp(3).toFloat() }
        val row = horizontal(dp(14)).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(12), dp(16), dp(12))
        }
        row.addView(text("Cari", 13f, orangeDark, Typeface.BOLD))
        val searchInput = EditText(this).apply {
            hint = "Cari dagangan..."
            inputType = InputType.TYPE_CLASS_TEXT
            setSingleLine(true)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setText(searchQuery)
            setTextColor(ink)
            setHintTextColor(muted)
            setBackgroundColor(Color.TRANSPARENT)
            setPadding(0, 0, 0, 0)
            setOnEditorActionListener { _, _, _ ->
                searchQuery = text.toString().trim()
                if (bottomNavigation.selectedItemId == R.id.nav_home) {
                    showHome()
                }
                true
            }
        }
        row.addView(searchInput, LinearLayout.LayoutParams(
            0,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            1f
        ).apply {
            marginStart = dp(12)
        })
        search.addView(row)
        return search
    }

    private fun categories(): View {
        val categories = listOf("Semua", "Sayur", "Buah", "Makanan", "Minuman", "Sembako", "Laundry", "Gas", "Jasa")
        val scroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = horizontal(dp(10)).apply { setPadding(dp(18), 0, dp(18), dp(6)) }
        categories.forEach { cat ->
            val isSelected = cat == selectedCategory
            val chip = text(cat, 14f, ink, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = if (isSelected) {
                    fillStroke(Color.rgb(255, 237, 213), orange, dp(24))
                } else {
                    fillStroke(Color.WHITE, stroke, dp(24))
                }
                setTextColor(if (isSelected) orangeDark else ink)
                isClickable = true
                isFocusable = true
                setOnClickListener {
                    if (selectedCategory != cat) {
                        selectedCategory = cat
                        if (bottomNavigation.selectedItemId == R.id.nav_home) {
                            showHome()
                        }
                    }
                }
            }
            row.addView(chip)
        }
        scroll.addView(row)
        return scroll
    }

    private fun infoCard(title: String, body: String): View {
        val infoCard = card()
        val container = vertical(dp(8)).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        container.addView(text(title, 17f, ink, Typeface.BOLD))
        container.addView(text(body, 14f, muted, Typeface.NORMAL))
        infoCard.addView(container)
        return infoCard
    }

    private fun loadingStateCard(message: String = "Mengambil data pedagang..."): View {
        val infoCard = card()
        val container = vertical(dp(8)).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        container.addView(ProgressBar(this))
        container.addView(text(message, 14f, muted, Typeface.NORMAL))
        infoCard.addView(container)
        return infoCard
    }

    private fun errorStateCard(title: String = "Gagal memuat data pedagang."): View {
        val infoCard = card()
        val container = vertical(dp(10)).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
        }
        container.addView(text(title, 16f, ink, Typeface.BOLD))
        container.addView(text("Periksa koneksi internet lalu coba lagi.", 14f, muted, Typeface.NORMAL))
        infoCard.addView(container)
        return infoCard
    }

    private fun sellerCard(seller: SellerData): View {
        val card = card()
        val row = horizontal(dp(12)).apply {
            setPadding(dp(14), dp(14), dp(14), dp(14))
            gravity = Gravity.CENTER_VERTICAL
        }

        val photo = ImageView(this).apply {
            setBackgroundColor(Color.rgb(245, 245, 245))
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_menu_gallery)
        }
        row.addView(photo, LinearLayout.LayoutParams(dp(84), dp(96)))
        loadImageInto(photo, seller.photoUrl)

        val info = vertical(dp(5)).apply { setPadding(dp(10), 0, 0, 0) }
        val shopNameView = text(seller.shopName.ifBlank { "Tanpa Nama" }, 17f, ink, Typeface.BOLD).apply {
            isClickable = true
            isFocusable = true
            setOnClickListener { showSellerProfileBottomSheet(seller) }
        }
        info.addView(shopNameView)
        info.addView(text(seller.category.ifBlank { "-" }, 13f, orangeDark, Typeface.BOLD))
        info.addView(text(seller.description.ifBlank { "-" }, 13f, muted, Typeface.NORMAL))
        if (seller.location.isNotBlank()) {
            info.addView(text(seller.location, 12f, muted, Typeface.NORMAL))
        }

        info.addView(primaryButton("Beli") {
            openWhatsAppToSeller(seller.whatsapp)
        })

        info.addView(smallOutlineButton("Laporkan") {
            openReportForm(
                targetType = "pedagang",
                targetName = seller.shopName.ifBlank { "Pedagang Pasarku" },
                targetWhatsapp = seller.whatsapp
            )
        })
        row.addView(info, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        card.addView(row)
        card.setOnClickListener { showSellerProfileBottomSheet(seller) }
        return card
    }

    private fun showSellerProfileBottomSheet(seller: SellerData) {
        val dialog = BottomSheetDialog(this)
        val root = ScrollView(this).apply {
            isFillViewport = true
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(18), dp(18), dp(22))
            setBackgroundColor(Color.WHITE)
        }

        val photoFrame = FrameLayout(this).apply {
            background = fillStroke(Color.rgb(245, 245, 245), stroke, dp(16))
            setPadding(dp(1), dp(1), dp(1), dp(1))
        }
        val photo = ImageView(this).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageResource(android.R.drawable.ic_menu_gallery)
        }
        val noPhotoLabel = text("Foto belum tersedia", 14f, muted, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
            visibility = View.GONE
            setPadding(dp(14), dp(14), dp(14), dp(14))
        }
        photoFrame.addView(photo, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            dp(230)
        ))
        photoFrame.addView(noPhotoLabel, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        content.addView(photoFrame)
        content.addView(text(seller.shopName.ifBlank { "Tanpa Nama" }, 24f, ink, Typeface.BOLD).apply {
            setPadding(0, dp(14), 0, dp(4))
        })
        content.addView(text(seller.category.ifBlank { "-" }, 14f, orangeDark, Typeface.BOLD))
        content.addView(text(seller.description.ifBlank { "-" }, 16f, ink, Typeface.NORMAL).apply {
            setPadding(0, dp(10), 0, dp(4))
        })
        if (seller.location.isNotBlank()) {
            content.addView(text("Lokasi: ${seller.location}", 14f, muted, Typeface.NORMAL))
        }

        content.addView(primaryButton("Beli / Chat WA") {
            openWhatsAppToSeller(seller.whatsapp)
        })

        root.addView(content)
        dialog.setContentView(root)
        dialog.show()

        loadImageInto(photo, seller.photoUrl) { bitmap ->
            if (bitmap != null) {
                photo.setOnClickListener { showFullScreenPhoto(bitmap) }
                noPhotoLabel.visibility = View.GONE
            } else {
                noPhotoLabel.visibility = View.VISIBLE
            }
        }
    }

    private fun showFullScreenPhoto(bitmap: Bitmap) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.BLACK)
        }
        val image = ImageView(this).apply {
            setImageBitmap(bitmap)
            adjustViewBounds = true
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        root.addView(image, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        ))
        val close = text("X", 18f, Color.WHITE, Typeface.BOLD).apply {
            setPadding(dp(14), dp(10), dp(14), dp(10))
            background = fillStroke(Color.argb(100, 0, 0, 0), Color.argb(120, 255, 255, 255), dp(14))
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(close, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.END
        ).apply {
            topMargin = dp(22)
            marginEnd = dp(16)
        })
        dialog.setContentView(root)
        dialog.show()
    }

    private fun fetchSellers() {
        Thread {
            val result = runCatching {
                val endpoint = BuildConfig.GOOGLE_APPS_SCRIPT_URL
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 20000
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                }
                val requestBody = JSONObject().put("action", "get_sellers").toString()
                connection.outputStream.use { it.write(requestBody.toByteArray(Charsets.UTF_8)) }

                val responseText = if (connection.responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                }
                connection.disconnect()
                parseSellersResponse(responseText)
            }

            runOnUiThread {
                if (result.isSuccess) {
                    homeSellers = result.getOrNull().orEmpty()
                    homeState = HomeState.READY
                } else {
                    homeState = HomeState.ERROR
                }
                isRefreshingHome = false
                if (bottomNavigation.selectedItemId == R.id.nav_home) {
                    showHome()
                }
            }
        }.start()
    }

    private fun fetchPromos() {
        Thread {
            val result = appsScriptClient.getPromos()
            runOnUiThread {
                if (result.isSuccess) {
                    promoItems = result.getOrNull().orEmpty()
                        .filter { it.whatsapp.isNotBlank() }
                        .filter { it.type.equals("image", ignoreCase = true) || it.type.equals("text", ignoreCase = true) }
                        .filter { it.imageUrl.isNotBlank() || it.description.isNotBlank() }
                    promoState = PromoState.READY
                } else {
                    promoState = PromoState.ERROR
                }
                isRefreshingPromo = false
                if (bottomNavigation.selectedItemId == R.id.nav_promo) {
                    showPromoBoard()
                }
            }
        }.start()
    }

    private fun promoCard(promo: PromoData): View {
        val card = card()
        val body = vertical(dp(8)).apply {
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        if (promo.type.equals("image", ignoreCase = true)) {
            val preview = ImageView(this).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.rgb(245, 245, 245))
                setImageResource(android.R.drawable.ic_menu_gallery)
            }
            body.addView(preview, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(220)
            ))
            loadImageInto(preview, promo.imageUrl)
            if (promo.description.isNotBlank()) {
                body.addView(text(promo.description, 13f, ink, Typeface.NORMAL))
            }
        } else {
            body.setBackgroundColor(Color.rgb(255, 247, 237))
            body.addView(text(promo.description, 20f, ink, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            })
        }
        val promoShop = text(promo.shopName.ifBlank { "Pedagang Pasarku" }, 13f, muted, Typeface.BOLD).apply {
            gravity = Gravity.CENTER
        }
        body.addView(promoShop)
        if (promo.timestamp.isNotBlank()) {
            body.addView(text(promo.timestamp, 12f, muted, Typeface.NORMAL).apply {
                gravity = Gravity.CENTER
                alpha = 0.85f
            })
        }
        body.addView(primaryButton("Chat WA") {
            openWhatsAppWithMessage(
                promo.whatsapp,
                "Halo, saya melihat promo Anda di Pasarku Sidokerto. Apakah promonya masih ada?"
            )
        })
        body.addView(smallOutlineButton("Laporkan") {
            openReportForm(
                targetType = "promo",
                targetName = promo.shopName.ifBlank { "Pedagang Pasarku" },
                targetWhatsapp = promo.whatsapp
            )
        })
        card.addView(body)
        card.setOnClickListener { showPromoFullScreen(promo) }
        return card
    }

    private fun showPromoFullScreen(promo: PromoData) {
        val dialog = Dialog(this, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.BLACK)
            setPadding(dp(18), dp(28), dp(18), dp(22))
        }
        val close = text("X", 18f, Color.WHITE, Typeface.BOLD).apply {
            setOnClickListener { dialog.dismiss() }
        }
        root.addView(close, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            gravity = Gravity.END
        })
        if (promo.type.equals("image", ignoreCase = true)) {
            val image = ImageView(this).apply {
                scaleType = ImageView.ScaleType.FIT_CENTER
                setImageResource(android.R.drawable.ic_menu_gallery)
                adjustViewBounds = true
            }
            root.addView(image, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            ))
            loadImageInto(image, promo.imageUrl)
            if (promo.description.isNotBlank()) {
                root.addView(text(promo.description, 15f, Color.WHITE, Typeface.NORMAL).apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(0, dp(10), 0, dp(10))
                })
            }
        } else {
            root.addView(text(promo.description, 32f, Color.WHITE, Typeface.BOLD).apply {
                gravity = Gravity.CENTER
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        }
        root.addView(text(promo.shopName.ifBlank { "Pedagang Pasarku" }, 18f, Color.WHITE, Typeface.BOLD).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 0, 0, dp(14))
        })
        root.addView(primaryButton("Chat WA") {
            openWhatsAppWithMessage(
                promo.whatsapp,
                "Halo, saya melihat promo Anda di Pasarku Sidokerto. Apakah promonya masih ada?"
            )
        })
        dialog.setContentView(root)
        dialog.show()
    }

    private fun parseSellersResponse(responseText: String): List<SellerData> {
        val response = JSONObject(responseText)
        if (!response.optBoolean("success", false)) {
            throw IllegalStateException(response.optString("message", "Gagal memuat data"))
        }
        val sellers = response.optJSONArray("sellers") ?: return emptyList()
        val parsed = mutableListOf<SellerData>()
        for (i in 0 until sellers.length()) {
            val item = sellers.optJSONObject(i) ?: continue
            if (item.optString("status") != "approved") continue
            parsed.add(
                SellerData(
                    shopName = item.optString("nama_toko"),
                    whatsapp = item.optString("nomor_wa"),
                    category = item.optString("kategori"),
                    description = item.optString("deskripsi"),
                    location = item.optString("lokasi"),
                    photoUrl = item.optString("foto_url")
                )
            )
        }
        return parsed
    }

    private fun promoErrorCard(): View {
        val infoCard = card()
        val container = vertical(dp(10)).apply {
            setPadding(dp(18), dp(18), dp(18), dp(18))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        container.addView(text("Gagal memuat promo.", 16f, ink, Typeface.BOLD))
        container.addView(text("Periksa koneksi internet lalu coba lagi.", 14f, muted, Typeface.NORMAL).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        })
        infoCard.addView(container)
        return infoCard
    }

    private fun loadImageInto(imageView: ImageView, imageUrl: String, onResult: ((Bitmap?) -> Unit)? = null) {
        if (imageUrl.isBlank()) {
            onResult?.invoke(null)
            return
        }
        Thread {
            val bmp = runCatching {
                URL(imageUrl).openStream().use { BitmapFactory.decodeStream(it) }
            }.getOrNull()
            runOnUiThread {
                if (bmp != null) {
                    imageView.setImageBitmap(bmp)
                }
                onResult?.invoke(bmp)
            }
        }.start()
    }

    private fun openWhatsAppToSeller(whatsapp: String) {
        openWhatsAppWithMessage(
            whatsapp,
            "Halo, saya melihat toko Anda di Pasarku Sidokerto. Saya mau tanya dagangannya."
        )
    }

    private fun openWhatsAppWithMessage(whatsapp: String, message: String) {
        val digits = whatsapp.filter { it.isDigit() }
        if (digits.isBlank()) {
            toast("Nomor WhatsApp pedagang tidak valid.")
            return
        }
        val encoded = URLEncoder.encode(message, "UTF-8")
        val uri = Uri.parse("https://wa.me/$digits?text=$encoded")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun openReportForm(targetType: String, targetName: String, targetWhatsapp: String) {
        val intent = Intent(this, ReportItemActivity::class.java).apply {
            putExtra(ReportItemActivity.EXTRA_TARGET_TYPE, targetType)
            putExtra(ReportItemActivity.EXTRA_TARGET_NAME, targetName)
            putExtra(ReportItemActivity.EXTRA_TARGET_WHATSAPP, targetWhatsapp)
        }
        startActivity(intent)
    }

    private fun sectionTitle(value: String): TextView {
        return text(value, 18f, ink, Typeface.BOLD).apply {
            setPadding(dp(18), dp(18), dp(18), dp(10))
        }
    }

    private fun card(): MaterialCardView {
        return MaterialCardView(this).apply {
            radius = dp(18).toFloat()
            cardElevation = dp(2).toFloat()
            strokeColor = stroke
            strokeWidth = 1
            setCardBackgroundColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(18), dp(8), dp(18), dp(8)) }
        }
    }

    private fun primaryButton(label: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = label
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAllCaps = false
            cornerRadius = dp(14)
            setBackgroundColor(orange)
            setOnClickListener { onClick() }
        }
    }

    private fun smallOutlineButton(label: String, onClick: () -> Unit): MaterialButton {
        return MaterialButton(this).apply {
            text = label
            setTextColor(orangeDark)
            textSize = 11f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            isAllCaps = false
            cornerRadius = dp(12)
            strokeColor = ColorStateList.valueOf(orange)
            strokeWidth = dp(1)
            insetTop = 0
            insetBottom = 0
            minHeight = dp(34)
            minimumHeight = dp(34)
            setBackgroundColor(Color.WHITE)
            setOnClickListener { onClick() }
        }
    }

    private fun refreshHome() {
        if (isRefreshingHome) return
        isRefreshingHome = true
        homeState = HomeState.LOADING
        showHome()
    }

    private fun refreshPromo() {
        if (isRefreshingPromo) return
        isRefreshingPromo = true
        promoState = PromoState.LOADING
        showPromoBoard()
    }


    private fun vertical(gap: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = space(gap, true)
        }
    }

    private fun horizontal(gap: Int): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            dividerDrawable = space(gap, false)
        }
    }

    private fun text(value: String, sp: Float, color: Int, style: Int): TextView {
        return TextView(this).apply {
            text = value
            textSize = sp
            setTextColor(color)
            setTypeface(Typeface.DEFAULT, style)
            includeFontPadding = true
        }
    }

    private fun fillStroke(fill: Int, line: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            setStroke(dp(1), line)
        }
    }

    private fun gradient(start: Int, end: Int, radius: Int): GradientDrawable {
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(start, end)).apply {
            cornerRadius = radius.toFloat()
        }
    }

    private fun space(size: Int, vertical: Boolean): ColorDrawable {
        return ColorDrawable(Color.TRANSPARENT).apply {
            setBounds(0, 0, if (vertical) 1 else size, if (vertical) size else 1)
        }
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private enum class HomeState {
        LOADING,
        READY,
        ERROR
    }

    private enum class PromoState {
        LOADING,
        READY,
        ERROR
    }

    private data class SellerData(
        val shopName: String,
        val whatsapp: String,
        val category: String,
        val description: String,
        val location: String,
        val photoUrl: String
    )
}
