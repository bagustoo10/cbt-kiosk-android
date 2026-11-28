package com.bagustoo.cbtkiosk

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.InputType
import android.util.Base64
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

class BdevWaveHeaderView(
    context: Context,
    private var primaryHex: String = "#059669"
) : View(context) {
    private val mainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val subWavePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(28, 255, 255, 255)
    }
    private val path = Path()
    private val subPath = Path()

    private fun safeColor(hex: String, fb: String): Int = try {
        Color.parseColor(hex.trim())
    } catch (_: Exception) {
        Color.parseColor(fb)
    }

    private fun blendColor(c1: Int, c2: Int, ratio: Float): Int {
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    fun updateAccent(hex: String) {
        if (hex.startsWith("#") && hex.length == 7) {
            primaryHex = hex
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val cStart = safeColor(primaryHex, "#059669")
        val cMid = blendColor(cStart, Color.parseColor("#0EA5E9"), 0.38f)
        val cEnd = blendColor(cStart, Color.parseColor("#14B8A6"), 0.55f)

        mainPaint.shader = LinearGradient(
            0f, 0f, w, h,
            intArrayOf(cStart, cMid, cEnd),
            floatArrayOf(0f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        subWavePaint.color = Color.argb(46, 255, 255, 255)

        path.reset()
        path.moveTo(0f, 0f)
        path.lineTo(w, 0f)
        path.lineTo(w, h * 0.72f)
        path.cubicTo(w * 0.72f, h * 1.02f, w * 0.30f, h * 0.56f, 0f, h * 0.86f)
        path.close()
        canvas.drawPath(path, mainPaint)

        subPath.reset()
        subPath.moveTo(0f, h * 0.62f)
        subPath.cubicTo(w * 0.28f, h * 0.42f, w * 0.68f, h * 0.88f, w, h * 0.58f)
        subPath.lineTo(w, h * 0.72f)
        subPath.cubicTo(w * 0.72f, h * 1.02f, w * 0.30f, h * 0.56f, 0f, h * 0.86f)
        subPath.close()
        canvas.drawPath(subPath, subWavePaint)

        canvas.drawCircle(w * 0.88f, h * 0.22f, h * 0.34f, bubblePaint)
        canvas.drawCircle(w * 0.12f, h * 0.14f, h * 0.20f, bubblePaint)
    }
}

class MainActivity : AppCompatActivity() {

    companion object {
        private const val URL_SHIELD_KEY = "BGT2026_CBT_URL_SHIELD"
        private const val COPYRIGHT_FOOTER = "Copyright 2026 © BDEV By Bagus too"
    }

    private lateinit var webView: WebView
    private lateinit var floatingExitBtn: TextView
    private lateinit var overlayBlockerView: LinearLayout
    private lateinit var overlayReasonText: TextView
    private lateinit var pinningGateView: FrameLayout
    private lateinit var keyboardGateView: FrameLayout
    private lateinit var kbDetectedNameText: TextView
    private lateinit var entryGateView: FrameLayout
    private lateinit var entryWaveHeader: BdevWaveHeaderView
    private lateinit var entryClockText: TextView
    private lateinit var entryGreetText: TextView
    private lateinit var entryLogoView: ImageView
    private lateinit var entryLogoWrap: FrameLayout
    private lateinit var entryAppTitle: TextView
    private lateinit var entrySchoolSub: TextView
    private lateinit var entryNoticeText: TextView
    private lateinit var entryQrStatusText: TextView
    private lateinit var entryPinInput: EditText
    private lateinit var entryTogglePwdBtn: TextView
    private lateinit var entryErrorText: TextView
    private lateinit var entrySubmitBtn: Button
    private lateinit var licenseGateView: FrameLayout
    private lateinit var licWaveHeader: BdevWaveHeaderView
    private lateinit var licLogoView: ImageView
    private lateinit var licTitleText: TextView
    private lateinit var licDescText: TextView
    private lateinit var licMetaText: TextView
    private lateinit var licWaBtn: Button
    private lateinit var licRetryBtn: Button

    private var activeCbtUrl: String = ""
    private var activeExitPin: String = BuildConfig.EXIT_ADMIN_PIN
    private var activeEntryPin: String = BuildConfig.ENTRY_PIN
    private var activeThemeColor: String = BuildConfig.THEME_COLOR
    private var activeOwnerWa: String = BuildConfig.OWNER_WA
    private var activeSchoolName: String = ""
    private var activeAppName: String = ""
    private var activeNotice: String = ""
    private var activeExpiredAt: String = "-"
    private var lastRemoteHostsCsv: String = ""
    private var cachedLogoBitmap: Bitmap? = null
    private val dynamicAllowedHosts = mutableSetOf<String>()

    private var licenseApproved: Boolean = !BuildConfig.REQUIRE_LICENSE_CHECK
    private var entryCheckReady: Boolean = !BuildConfig.REQUIRE_LICENSE_CHECK
    private var entryUnlocked: Boolean = BuildConfig.ENTRY_PIN.isEmpty()
    private var isEntryPwdVisible: Boolean = false
    private var isInternalDialogOpen = false
    private var lastOverlayViolationTime = 0L
    private var hasLoadedExamUrl = false
    private var wasPinnedBefore = false
    private var isKeyboardBlocked = false
    private var wasKeyboardSafeBefore = false
    private var needsReopenAuth = false
    private var lastRemoteSyncAt = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    private val qrScannerLauncher = registerForActivityResult(ScanContract()) { result ->
        isInternalDialogOpen = false
        enableImmersiveFullscreen()
        val scanned = result?.contents?.trim().orEmpty()
        if (scanned.isNotEmpty()) {
            handleScannedQrPayload(scanned)
        }
    }

    private val pinCheckRunnable = object : Runnable {
        override fun run() {
            if (!isFinishing) {
                updateLiveClock()
                enforceGates()
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    private fun updateLiveClock() {
        if (!::entryClockText.isInitialized) return
        val now = Calendar.getInstance()
        val fmt = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        entryClockText.text = fmt.format(now.time)
        val hour = now.get(Calendar.HOUR_OF_DAY)
        entryGreetText.text = when (hour) {
            in 4..10 -> "Selamat Pagi, Semangat Ujian!"
            in 11..14 -> "Selamat Siang, Tetap Fokus!"
            in 15..17 -> "Selamat Sore, Semoga Sukses!"
            else -> "Selamat Malam, Tetap Semangat!"
        }
    }

    private fun decodeHiddenUrl(raw: String): String {
        val trimmed = raw.trim()
        if (!trimmed.startsWith("ENC1:")) return trimmed
        return try {
            val cipherBytes = Base64.decode(trimmed.substring(5), Base64.DEFAULT)
            val keyBytes = URL_SHIELD_KEY.toByteArray(Charsets.UTF_8)
            val out = ByteArray(cipherBytes.size)
            for (i in cipherBytes.indices) {
                out[i] = (cipherBytes[i].toInt() xor keyBytes[i % keyBytes.size].toInt()).toByte()
            }
            String(out, Charsets.UTF_8).trim()
        } catch (_: Exception) {
            ""
        }
    }

    private fun parseSafeColor(hex: String, fallback: String = "#059669"): Int {
        return try {
            Color.parseColor(hex.trim())
        } catch (_: Exception) {
            Color.parseColor(fallback)
        }
    }

    private fun blendColorHex(hex: String, targetHex: String, ratio: Float): Int {
        val c1 = parseSafeColor(hex)
        val c2 = parseSafeColor(targetHex)
        val inv = 1f - ratio
        val r = (Color.red(c1) * inv + Color.red(c2) * ratio).toInt().coerceIn(0, 255)
        val g = (Color.green(c1) * inv + Color.green(c2) * ratio).toInt().coerceIn(0, 255)
        val b = (Color.blue(c1) * inv + Color.blue(c2) * ratio).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun makeGradientButtonBg(primaryHex: String, radiusDp: Float = 18f): GradientDrawable {
        val dp = resources.displayMetrics.density
        val c1 = parseSafeColor(primaryHex)
        val c2 = blendColorHex(primaryHex, "#0EA5E9", 0.42f)
        return GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(c1, c2)).apply {
            cornerRadius = radiusDp * dp
        }
    }

    private fun applyRemoteLogoDataUrl(dataUrl: String) {
        if (dataUrl.isBlank()) return
        try {
            val b64 = if (dataUrl.contains(",")) dataUrl.substringAfter(",") else dataUrl
            val bytes = Base64.decode(b64, Base64.DEFAULT)
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return
            cachedLogoBitmap = bmp
            mainHandler.post {
                entryLogoView.setImageBitmap(bmp)
                licLogoView.setImageBitmap(bmp)
            }
        } catch (_: Exception) {
        }
    }

    private fun updateEntryScreenBranding() {
        mainHandler.post {
            val titleTxt = activeAppName.ifEmpty { getString(R.string.app_name) }
            val schoolTxt = activeSchoolName.ifEmpty { "Exam Browser Terlindungi" }
            entryAppTitle.text = titleTxt
            entrySchoolSub.text = schoolTxt
            entrySchoolSub.setTextColor(parseSafeColor(activeThemeColor))
            entryWaveHeader.updateAccent(activeThemeColor)
            licWaveHeader.updateAccent(activeThemeColor)
            entryLogoWrap.background = makeRoundedBg("#FFFFFF", 24f, activeThemeColor, 2f)
            entrySubmitBtn.background = makeGradientButtonBg(activeThemeColor, 18f)
            entryTogglePwdBtn.setTextColor(parseSafeColor(activeThemeColor))
            if (activeNotice.isNotEmpty()) {
                entryNoticeText.text = activeNotice
                entryNoticeText.visibility = View.VISIBLE
            } else {
                entryNoticeText.visibility = View.GONE
            }
        }
    }

    private fun rebuildAllowedHosts(extraHostsCsv: String = "") {
        dynamicAllowedHosts.clear()
        val baseHost = Uri.parse(activeCbtUrl).host?.lowercase().orEmpty()
        if (baseHost.isNotEmpty()) dynamicAllowedHosts.add(baseHost)
        listOf(
            "web.app",
            "firebaseapp.com",
            "docs.google.com",
            "forms.gle",
            "accounts.google.com",
            "gstatic.com",
            "googleusercontent.com"
        ).forEach { dynamicAllowedHosts.add(it) }

        extraHostsCsv.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.forEach {
            dynamicAllowedHosts.add(it)
        }
    }

    private fun launchQrScanner() {
        isInternalDialogOpen = true
        val options = ScanOptions().apply {
            setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            setPrompt("Arahkan kamera ke QR Code Ujian dari Pengawas")
            setCameraId(0)
            setBeepEnabled(true)
            setBarcodeImageEnabled(false)
            setOrientationLocked(true)
        }
        try {
            qrScannerLauncher.launch(options)
        } catch (_: Exception) {
            isInternalDialogOpen = false
            Toast.makeText(this, "Kamera QR tidak tersedia di perangkat ini.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun handleScannedQrPayload(raw: String) {
        var resolvedUrl = ""
        var scannedPin = ""

        if (raw.startsWith("sebs://", ignoreCase = true) || raw.startsWith("seb://", ignoreCase = true)) {
            entryQrStatusText.text = "QR Code ini khusus iPhone (SEB). Gunakan QR Android atau tekan Masuk Ujian."
            entryQrStatusText.setTextColor(Color.parseColor("#B45309"))
            entryQrStatusText.background = makeRoundedBg("#FFFBEB", 14f, "#F59E0B", 1f)
            entryQrStatusText.visibility = View.VISIBLE
            return
        }

        if (raw.startsWith("ENC1:")) {
            resolvedUrl = decodeHiddenUrl(raw)
        } else if (raw.startsWith("{")) {
            try {
                val obj = JSONObject(raw)
                val u = obj.optString("url", obj.optString("cbtUrl", "")).trim()
                resolvedUrl = if (u.startsWith("ENC1:")) decodeHiddenUrl(u) else u
                scannedPin = obj.optString("pin", obj.optString("entryPin", "")).trim()
            } catch (_: Exception) {
                resolvedUrl = ""
            }
        } else if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
            resolvedUrl = raw
        }

        if (!resolvedUrl.startsWith("http://", ignoreCase = true) && !resolvedUrl.startsWith("https://", ignoreCase = true)) {
            entryQrStatusText.text = "QR Code tidak valid! Pastikan Anda memindai QR Code Ujian resmi sekolah."
            entryQrStatusText.setTextColor(Color.parseColor("#B91C1C"))
            entryQrStatusText.background = makeRoundedBg("#FEF2F2", 14f, "#F87171", 1f)
            entryQrStatusText.visibility = View.VISIBLE
            playNativeAlarm()
            return
        }

        activeCbtUrl = resolvedUrl
        rebuildAllowedHosts(lastRemoteHostsCsv)
        hasLoadedExamUrl = false

        if (scannedPin.isNotEmpty()) {
            entryPinInput.setText(scannedPin)
        }

        if (activeEntryPin.isEmpty()) {
            entryUnlocked = true
            enforceGates()
            return
        }

        entryQrStatusText.text = "✓ QR Code Ujian berhasil dipindai! Masukkan Password Masuk lalu tekan Masuk."
        entryQrStatusText.setTextColor(Color.parseColor("#047857"))
        entryQrStatusText.background = makeRoundedBg("#ECFDF5", 14f, "#10B981", 1f)
        entryQrStatusText.visibility = View.VISIBLE
        entryErrorText.text = ""
        entryPinInput.requestFocus()
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        activeAppName = getString(R.string.app_name)
        activeCbtUrl = decodeHiddenUrl(BuildConfig.CBT_URL_ENC)
        rebuildAllowedHosts()

        // 1. Anti-Screenshot, Anti-Screen Recording, dan layar tetap menyala
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        // 2. Blokir aplikasi mengambang pihak ketiga (Android 12 / API 31+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                window.setHideOverlayWindows(true)
            } catch (_: Exception) {
            }
        }

        val rootLayout = FrameLayout(this).apply {
            filterTouchesWhenObscured = true
        }

        webView = WebView(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            filterTouchesWhenObscured = true
        }
        rootLayout.addView(webView)

        overlayBlockerView = buildOverlayBlockerView()
        rootLayout.addView(overlayBlockerView)

        entryGateView = buildEntryGateView()
        rootLayout.addView(entryGateView)

        keyboardGateView = buildKeyboardGateView()
        rootLayout.addView(keyboardGateView)

        pinningGateView = buildPinningGateView()
        rootLayout.addView(pinningGateView)

        licenseGateView = buildLicenseGateView()
        rootLayout.addView(licenseGateView)

        val dp = resources.displayMetrics.density
        floatingExitBtn = TextView(this).apply {
            text = "⏻  Keluar"
            setTextColor(Color.parseColor("#E11D48"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = makeRoundedBg("#F5FFFFFF", 22f, "#FDA4AF", 1.2f)
            elevation = 6f * dp
            setPadding((14 * dp).toInt(), (7 * dp).toInt(), (14 * dp).toInt(), (7 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.TOP or Gravity.END
                topMargin = (34 * dp).toInt()
                marginEnd = (14 * dp).toInt()
            }
            setOnClickListener { showExitPinDialog() }
        }
        rootLayout.addView(floatingExitBtn)

        setContentView(rootLayout)

        enableImmersiveFullscreen()
        configureWebView()
        updateEntryScreenBranding()
        updateLiveClock()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Tombol Back diblokir diam-diam tanpa notifikasi/dialog keluar
            }
        })

        if (BuildConfig.REQUIRE_LICENSE_CHECK) {
            verifyRemoteLicense()
        } else {
            licenseApproved = true
            entryCheckReady = false
            licenseGateView.visibility = View.GONE
            fetchWebCbtBrandingAndEntryPin()
            startKioskLockMode()
            enforceGates()
        }
        mainHandler.postDelayed(pinCheckRunnable, 1000L)
    }

    private fun fetchWebCbtBrandingAndEntryPin() {
        Thread {
            try {
                val urlStr = "https://firestore.googleapis.com/v1/projects/${BuildConfig.SCHOOL_FIREBASE_PROJECT}/databases/(default)/documents/license/main"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 6000
                    readTimeout = 6000
                    setRequestProperty("User-Agent", "CBT-Kiosk-APK/2.6")
                }
                if (conn.responseCode == 200) {
                    val body = conn.inputStream.bufferedReader().use { it.readText() }
                    val fields = JSONObject(body).optJSONObject("fields") ?: JSONObject()
                    fun getStr(key: String): String = fields.optJSONObject(key)?.optString("stringValue", "")?.trim().orEmpty()

                    val rApp = getStr("appName")
                    val rSchool = getStr("schoolName")
                    val rColor = getStr("themeColor")
                    val rLogo = getStr("logoDataUrl")
                    val rEntryPin = getStr("entryPin")
                    val rExp = getStr("expiredAt")

                    if (rApp.isNotEmpty()) activeAppName = rApp
                    if (rSchool.isNotEmpty()) activeSchoolName = rSchool
                    if (rExp.isNotEmpty()) activeExpiredAt = rExp
                    if (rColor.startsWith("#") && rColor.length == 7) activeThemeColor = rColor
                    if (rEntryPin.isNotEmpty()) {
                        activeEntryPin = rEntryPin
                    } else if (fields.has("entryPin") && BuildConfig.ENTRY_PIN.isEmpty()) {
                        activeEntryPin = ""
                    }
                    entryUnlocked = activeEntryPin.isEmpty()
                    if (rLogo.isNotEmpty()) applyRemoteLogoDataUrl(rLogo)
                    updateEntryScreenBranding()
                }
                val cfgUrl = "https://firestore.googleapis.com/v1/projects/${BuildConfig.SCHOOL_FIREBASE_PROJECT}/databases/(default)/documents/config/main"
                val cfgConn = (URL(cfgUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 5000
                    readTimeout = 5000
                    setRequestProperty("User-Agent", "CBT-Kiosk-APK/2.6")
                }
                if (cfgConn.responseCode == 200) {
                    val cfgBody = cfgConn.inputStream.bufferedReader().use { it.readText() }
                    val supPin = JSONObject(cfgBody)
                        .optJSONObject("fields")
                        ?.optJSONObject("settings")
                        ?.optJSONObject("mapValue")
                        ?.optJSONObject("fields")
                        ?.optJSONObject("supervisorPin")
                        ?.optString("stringValue", "")
                        ?.trim()
                        .orEmpty()
                    if (supPin.isNotEmpty()) activeExitPin = supPin
                }
            } catch (_: Exception) {
                entryUnlocked = activeEntryPin.isEmpty()
            } finally {
                mainHandler.post {
                    entryCheckReady = true
                    enforceGates()
                }
            }
        }.start()
    }

    private fun verifyRemoteLicense() {
        licenseApproved = false
        entryCheckReady = false
        licenseGateView.visibility = View.VISIBLE
        licTitleText.text = "Memeriksa Lisensi..."
        licDescText.text = "Menghubungkan ke server lisensi resmi sekolah. Pastikan koneksi internet aktif."
        licMetaText.text = "ID Aplikasi: ${BuildConfig.SCHOOL_SLUG}"
        licRetryBtn.visibility = View.GONE
        licWaBtn.visibility = View.GONE

        Thread {
            try {
                val urlStr = "https://firestore.googleapis.com/v1/projects/${BuildConfig.LICENSE_SERVER_PROJECT}/databases/(default)/documents/apk_clients/${BuildConfig.SCHOOL_SLUG}"
                val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10000
                    readTimeout = 10000
                    setRequestProperty("User-Agent", "CBT-Kiosk-APK/2.6")
                }

                val code = conn.responseCode
                val serverDateHeader = conn.getHeaderField("Date")
                val todayYmd = parseServerDateYmd(serverDateHeader)

                if (code == 404) {
                    showLicenseBlocked(
                        "Lisensi Belum Terdaftar",
                        "Kode aplikasi '${BuildConfig.SCHOOL_SLUG}' belum didaftarkan di Panel Owner (${BuildConfig.LICENSE_SERVER_PROJECT}).",
                        "-"
                    )
                    return@Thread
                }
                if (code != 200) {
                    showLicenseBlocked(
                        "Gagal Memeriksa Lisensi",
                        "Server lisensi merespons kode $code. Periksa koneksi internet lalu tekan Cek Ulang.",
                        "-"
                    )
                    return@Thread
                }

                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val fields = JSONObject(body).optJSONObject("fields") ?: JSONObject()
                fun getStr(key: String): String = fields.optJSONObject(key)?.optString("stringValue", "")?.trim().orEmpty()

                val status = getStr("status").lowercase().ifEmpty { "active" }
                val expiredAt = getStr("expiredAt")
                val remoteUrlEnc = getStr("targetUrlEnc")
                val remoteHosts = getStr("allowedHosts")
                val remotePin = getStr("exitPin")
                val remoteEntryPin = getStr("entryPin")
                val remoteWa = getStr("ownerWa")
                val remoteSchool = getStr("schoolName")
                val remoteAppName = getStr("appName")
                val remoteColor = getStr("themeColor")
                val remoteLogo = getStr("logoDataUrl")
                val remoteNotice = getStr("notice")

                if (remoteWa.isNotEmpty()) activeOwnerWa = remoteWa
                if (remoteSchool.isNotEmpty()) activeSchoolName = remoteSchool
                if (remoteAppName.isNotEmpty()) activeAppName = remoteAppName
                if (remotePin.isNotEmpty()) activeExitPin = remotePin
                if (expiredAt.isNotEmpty()) activeExpiredAt = expiredAt
                if (remoteColor.startsWith("#") && remoteColor.length == 7) activeThemeColor = remoteColor
                activeNotice = remoteNotice
                lastRemoteHostsCsv = remoteHosts
                if (remoteEntryPin.isNotEmpty()) {
                    activeEntryPin = remoteEntryPin
                } else if (fields.has("entryPin") && BuildConfig.ENTRY_PIN.isEmpty()) {
                    activeEntryPin = ""
                }
                entryUnlocked = activeEntryPin.isEmpty()

                if (remoteLogo.isNotEmpty()) applyRemoteLogoDataUrl(remoteLogo)
                updateEntryScreenBranding()

                if (remoteUrlEnc.isNotEmpty()) {
                    val decoded = decodeHiddenUrl(remoteUrlEnc)
                    if (decoded.startsWith("http://") || decoded.startsWith("https://")) {
                        activeCbtUrl = decoded
                    }
                }
                rebuildAllowedHosts(remoteHosts)

                if (status == "suspended") {
                    showLicenseBlocked(
                        "Exam Browser Dinonaktifkan",
                        remoteNotice.ifEmpty { "Akses Exam Browser untuk sekolah ini sedang ditangguhkan oleh penyedia." },
                        expiredAt.ifEmpty { "-" }
                    )
                    return@Thread
                }

                if (expiredAt.isNotEmpty() && todayYmd > expiredAt) {
                    showLicenseBlocked(
                        "Masa Aktif Exam Browser Berakhir",
                        remoteNotice.ifEmpty { "Langganan Exam Browser telah berakhir. Hubungi penyedia untuk perpanjangan (otomatis aktif tanpa instal ulang)." },
                        expiredAt
                    )
                    return@Thread
                }

                mainHandler.post {
                    licenseApproved = true
                    entryCheckReady = true
                    licenseGateView.visibility = View.GONE
                    if (remoteNotice.isNotEmpty() && entryUnlocked) {
                        Toast.makeText(this@MainActivity, remoteNotice, Toast.LENGTH_LONG).show()
                    }
                    startKioskLockMode()
                    enforceGates()
                }
            } catch (_: Exception) {
                showLicenseBlocked(
                    "Tidak Ada Koneksi Internet",
                    "Exam Browser membutuhkan koneksi internet untuk memverifikasi lisensi sekolah sebelum memulai ujian.",
                    "-"
                )
            }
        }.start()
    }

    private fun parseServerDateYmd(dateHeader: String?): String {
        val outFmt = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("Asia/Jakarta")
        }
        if (!dateHeader.isNullOrBlank()) {
            try {
                val rfcFmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
                val parsed = rfcFmt.parse(dateHeader)
                if (parsed != null) return outFmt.format(parsed)
            } catch (_: Exception) {
            }
        }
        return outFmt.format(Date())
    }

    private fun showLicenseBlocked(title: String, desc: String, expiredAt: String) {
        mainHandler.post {
            licenseApproved = false
            licenseGateView.visibility = View.VISIBLE
            pinningGateView.visibility = View.GONE
            keyboardGateView.visibility = View.GONE
            entryGateView.visibility = View.GONE
            overlayBlockerView.visibility = View.GONE
            floatingExitBtn.visibility = View.VISIBLE
            licTitleText.text = title
            licDescText.text = desc
            val inst = activeSchoolName.ifEmpty { BuildConfig.SCHOOL_SLUG }
            licMetaText.text = "Instansi: $inst  •  Masa Berlaku: $expiredAt"
            licRetryBtn.visibility = View.VISIBLE
            licWaBtn.visibility = if (activeOwnerWa.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun isAppPinned(): Boolean {
        return try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            am.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        } catch (_: Exception) {
            false
        }
    }

    private fun getBlockedThirdPartyKeyboardName(): String? {
        return try {
            val defaultIme = Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD).orEmpty().trim()
            if (defaultIme.isEmpty()) return null
            val imePkg = defaultIme.substringBefore("/").lowercase(Locale.US)
            if (imePkg.isEmpty()) return null

            val appLabel = try {
                val ai = packageManager.getApplicationInfo(imePkg, 0)
                packageManager.getApplicationLabel(ai).toString().trim().ifEmpty { imePkg }
            } catch (_: Exception) {
                imePkg
            }

            // 1. Daftar hitam keyboard AI / pihak ketiga (tetap diblokir meski pra-instal di beberapa HP)
            val aiBlacklist = listOf(
                "swiftkey", "touchtype", "grammarly", "clevertype", "writeassist",
                "chatgpt", "openai", "gpt", "copilot", "aikeyboard", "ai.keyboard",
                "typeai", "paragraphai", "prowriting", "quillbot", "perplexity",
                "yandex", "fleksy", "ginger", "kika", "facemoji", "bobble",
                "cheetah", "clevkey", "go.keyboard", "smartkeyboard", "typewise",
                "chrooma", "gingerpage", "wordtune", "jasper"
            )
            if (aiBlacklist.any { imePkg.contains(it) || appLabel.lowercase(Locale.US).contains(it) }) {
                return "$appLabel ($imePkg)"
            }

            // 2. Izinkan keyboard sistem bawaan resmi (Google Gboard, AOSP, Samsung, Huawei/Honor, Xiaomi, Oppo/Realme, Vivo, Infinix/Tecno, ASUS)
            val safeStockPrefixes = listOf(
                "com.google.android.inputmethod",
                "com.android.inputmethod",
                "com.samsung.android.honeyboard",
                "com.sec.android.inputmethod",
                "com.huawei.",
                "com.baidu.input_huawei",
                "com.hihonor.",
                "com.miui.",
                "com.sohu.inputmethod.sogou.xiaomi",
                "com.iflytek.inputmethod.miui",
                "com.coloros.",
                "com.oplus.",
                "com.vivo.",
                "com.bbk.",
                "com.transsion.",
                "com.asus.ime",
                "com.sonyericsson.textinput",
                "com.lge.ime",
                "com.motorola."
            )
            if (safeStockPrefixes.any { imePkg.startsWith(it) }) return null

            // 3. Periksa apakah aplikasi sistem bawaan HP; selain itu = keyboard pihak ketiga hasil instalasi user -> BLOKIR
            val isSystemApp = try {
                val ai = packageManager.getApplicationInfo(imePkg, 0)
                (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0 ||
                    (ai.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            } catch (_: Exception) {
                false
            }
            if (isSystemApp) null else "$appLabel ($imePkg)"
        } catch (_: Exception) {
            null
        }
    }

    private fun enforceGates() {
        if (!licenseApproved) {
            pinningGateView.visibility = View.GONE
            keyboardGateView.visibility = View.GONE
            entryGateView.visibility = View.GONE
            overlayBlockerView.visibility = View.GONE
            licenseGateView.visibility = View.VISIBLE
            floatingExitBtn.visibility = View.VISIBLE
            return
        }
        licenseGateView.visibility = View.GONE

        if (BuildConfig.REQUIRE_PINNING) {
            val pinned = isAppPinned()
            if (pinned) {
                wasPinnedBefore = true
                pinningGateView.visibility = View.GONE
            } else {
                if (wasPinnedBefore) {
                    wasPinnedBefore = false
                    triggerNativeOverlayViolation("Melepas Sematkan Aplikasi (Unpin) saat ujian berlangsung")
                }
                overlayBlockerView.visibility = View.GONE
                keyboardGateView.visibility = View.GONE
                entryGateView.visibility = View.GONE
                pinningGateView.visibility = View.VISIBLE
                floatingExitBtn.visibility = View.VISIBLE
                return
            }
        } else {
            pinningGateView.visibility = View.GONE
        }

        // Cek Keyboard Pihak Ketiga / Keyboard AI secara real-time sebelum dan selama ujian
        val blockedKb = getBlockedThirdPartyKeyboardName()
        if (blockedKb != null) {
            isKeyboardBlocked = true
            if (wasKeyboardSafeBefore && entryUnlocked) {
                wasKeyboardSafeBefore = false
                triggerNativeOverlayViolation("Terdeteksi keyboard pihak ketiga / AI: $blockedKb")
            }
            kbDetectedNameText.text = "Terdeteksi: $blockedKb"
            overlayBlockerView.visibility = View.GONE
            entryGateView.visibility = View.GONE
            keyboardGateView.visibility = View.VISIBLE
            floatingExitBtn.visibility = View.VISIBLE
            return
        } else {
            isKeyboardBlocked = false
            wasKeyboardSafeBefore = true
            keyboardGateView.visibility = View.GONE
        }

        if (!entryCheckReady) {
            entryGateView.visibility = View.GONE
            floatingExitBtn.visibility = View.VISIBLE
            return
        }

        if (activeEntryPin.isNotEmpty() && !entryUnlocked) {
            entryGateView.visibility = View.VISIBLE
            floatingExitBtn.visibility = View.GONE
            return
        }

        entryGateView.visibility = View.GONE
        floatingExitBtn.visibility = View.VISIBLE
        ensureWebLoaded()
    }

    private fun ensureWebLoaded() {
        if (!hasLoadedExamUrl && activeCbtUrl.isNotEmpty()) {
            hasLoadedExamUrl = true
            webView.loadUrl(activeCbtUrl)
        }
    }

    private fun makeRoundedBg(colorHex: String, radiusDp: Float = 16f, strokeColorHex: String? = null, strokeWidthDp: Float = 1f): GradientDrawable {
        val dp = resources.displayMetrics.density
        return GradientDrawable().apply {
            setColor(parseSafeColor(colorHex))
            cornerRadius = radiusDp * dp
            if (strokeColorHex != null) {
                setStroke((strokeWidthDp * dp).toInt().coerceAtLeast(1), parseSafeColor(strokeColorHex, "#CBD5E1"))
            }
        }
    }

    private fun makeCopyrightFooter(): TextView {
        val dp = resources.displayMetrics.density
        return TextView(this).apply {
            text = COPYRIGHT_FOOTER
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (12 * dp).toInt(), 0, (14 * dp).toInt())
        }
    }

    private fun makeActionTile(
        iconText: String,
        labelText: String,
        tileBgHex: String,
        tileBorderHex: String,
        iconBgHex: String,
        accentHex: String,
        onClick: () -> Unit
    ): LinearLayout {
        val dp = resources.displayMetrics.density
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            background = makeRoundedBg(tileBgHex, 18f, tileBorderHex, 1f)
            setPadding((8 * dp).toInt(), (12 * dp).toInt(), (8 * dp).toInt(), (11 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginStart = (4 * dp).toInt()
                marginEnd = (4 * dp).toInt()
            }
            isClickable = true
            isFocusable = true
            setOnClickListener { onClick() }

            val iconBadge = TextView(this@MainActivity).apply {
                text = iconText
                setTextColor(parseSafeColor(accentHex))
                textSize = 18f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
                background = makeRoundedBg(iconBgHex, 14f)
                layoutParams = LinearLayout.LayoutParams((42 * dp).toInt(), (42 * dp).toInt()).apply {
                    bottomMargin = (7 * dp).toInt()
                }
            }
            val label = TextView(this@MainActivity).apply {
                text = labelText
                setTextColor(Color.parseColor("#1E293B"))
                textSize = 10.5f
                setTypeface(null, Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            addView(iconBadge)
            addView(label)
        }
    }

    private fun buildEntryGateView(): FrameLayout {
        val dp = resources.displayMetrics.density
        val outer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F4F7FB"))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }

        // Gelombang gradien modern di bagian atas
        entryWaveHeader = BdevWaveHeaderView(this, activeThemeColor).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (235 * dp).toInt()
            ).apply { gravity = Gravity.TOP }
        }
        outer.addView(entryWaveHeader)

        val root = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
        }

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val contentCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((20 * dp).toInt(), (36 * dp).toInt(), (20 * dp).toInt(), (16 * dp).toInt())
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Top bar jam live + status pill di atas gelombang
        val heroRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (18 * dp).toInt() }
        }
        val clockBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        entryClockText = TextView(this).apply {
            text = "00:00:00"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
        }
        entryGreetText = TextView(this).apply {
            text = "Selamat Datang, Semangat Ujian!"
            setTextColor(Color.parseColor("#E0F2FE"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
        }
        clockBox.addView(entryClockText)
        clockBox.addView(entryGreetText)

        val shieldPill = TextView(this).apply {
            text = "●  KIOSK AKTIF"
            setTextColor(Color.WHITE)
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            background = makeRoundedBg("#38FFFFFF", 20f, "#66FFFFFF", 1f)
            setPadding((11 * dp).toInt(), (5 * dp).toInt(), (11 * dp).toInt(), (5 * dp).toInt())
        }
        heroRow.addView(clockBox)
        heroRow.addView(shieldPill)
        contentCol.addView(heroRow)

        // Kartu Putih Melayang Utama
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = makeRoundedBg("#FFFFFF", 28f, "#E2E8F0", 1f)
            elevation = 10f * dp
            setPadding((22 * dp).toInt(), (26 * dp).toInt(), (22 * dp).toInt(), (22 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        entryLogoWrap = FrameLayout(this).apply {
            background = makeRoundedBg("#FFFFFF", 24f, activeThemeColor, 2f)
            elevation = 6f * dp
            setPadding((10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams((92 * dp).toInt(), (92 * dp).toInt()).apply {
                bottomMargin = (14 * dp).toInt()
            }
        }
        entryLogoView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        entryLogoWrap.addView(entryLogoView)

        entryAppTitle = TextView(this).apply {
            text = getString(R.string.app_name)
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 21f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        entrySchoolSub = TextView(this).apply {
            text = "Exam Browser Terlindungi"
            setTextColor(parseSafeColor(activeThemeColor))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (3 * dp).toInt(), 0, (4 * dp).toInt())
        }

        val tagline = TextView(this).apply {
            text = "“Ujian Jujur, Prestasi Unggul”"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 11.5f
            setTypeface(null, Typeface.ITALIC)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (16 * dp).toInt())
        }

        entryNoticeText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#92400E"))
            textSize = 12f
            gravity = Gravity.CENTER
            background = makeRoundedBg("#FFFBEB", 14f, "#F59E0B", 1f)
            setPadding((14 * dp).toInt(), (10 * dp).toInt(), (14 * dp).toInt(), (10 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
            visibility = View.GONE
        }

        entryQrStatusText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#047857"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = makeRoundedBg("#ECFDF5", 14f, "#10B981", 1f)
            setPadding((12 * dp).toInt(), (9 * dp).toInt(), (12 * dp).toInt(), (9 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
            visibility = View.GONE
        }

        val inputLabelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * dp).toInt() }
        }
        val inputLabel = TextView(this).apply {
            text = "Password Masuk Ujian"
            setTextColor(Color.parseColor("#334155"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        entryTogglePwdBtn = TextView(this).apply {
            text = "Tampilkan"
            setTextColor(parseSafeColor(activeThemeColor))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                isEntryPwdVisible = !isEntryPwdVisible
                entryPinInput.inputType = if (isEntryPwdVisible) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                entryPinInput.setSelection(entryPinInput.text?.length ?: 0)
                text = if (isEntryPwdVisible) "Sembunyikan" else "Tampilkan"
            }
        }
        inputLabelRow.addView(inputLabel)
        inputLabelRow.addView(entryTogglePwdBtn)

        entryPinInput = EditText(this).apply {
            hint = "Masukkan password dari pengawas"
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = makeRoundedBg("#F8FAFC", 16f, "#CBD5E1", 1.4f)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        entryErrorText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#DC2626"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setPadding((4 * dp).toInt(), (7 * dp).toInt(), (4 * dp).toInt(), (7 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        entrySubmitBtn = Button(this).apply {
            text = "Masuk ke Ruang Ujian  →"
            setTextColor(Color.WHITE)
            textSize = 14.5f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            elevation = 5f * dp
            background = makeGradientButtonBg(activeThemeColor, 18f)
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = (2 * dp).toInt(); bottomMargin = (18 * dp).toInt() }
            setOnClickListener {
                val entered = entryPinInput.text.toString().trim()
                if (entered == activeEntryPin) {
                    entryErrorText.text = ""
                    entryPinInput.setText("")
                    entryQrStatusText.visibility = View.GONE
                    entryUnlocked = true
                    enforceGates()
                } else {
                    entryErrorText.text = "Password masuk salah! Silakan cek kembali ke pengawas."
                    playNativeAlarm()
                }
            }
        }

        // Divider + 3 Tombol Menu Cepat Modern (Scan QR | Info | Keluar)
        val menuLabel = TextView(this).apply {
            text = "MENU CEPAT EXAM BROWSER"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (10 * dp).toInt())
        }

        val actionRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        actionRow.addView(
            makeActionTile(
                iconText = "⊞",
                labelText = "Scan QR Ujian",
                tileBgHex = "#F0F9FF",
                tileBorderHex = "#BAE6FD",
                iconBgHex = "#E0F2FE",
                accentHex = "#0284C7"
            ) { launchQrScanner() }
        )
        actionRow.addView(
            makeActionTile(
                iconText = "ⓘ",
                labelText = "Info Aplikasi",
                tileBgHex = "#EEF2FF",
                tileBorderHex = "#C7D2FE",
                iconBgHex = "#E0E7FF",
                accentHex = "#4F46E5"
            ) { showAppInfoDialog() }
        )
        actionRow.addView(
            makeActionTile(
                iconText = "⏻",
                labelText = "Keluar",
                tileBgHex = "#FFF1F2",
                tileBorderHex = "#FECDD3",
                iconBgHex = "#FFE4E6",
                accentHex = "#E11D48"
            ) { showExitPinDialog() }
        )

        card.addView(entryLogoWrap)
        card.addView(entryAppTitle)
        card.addView(entrySchoolSub)
        card.addView(tagline)
        card.addView(entryNoticeText)
        card.addView(entryQrStatusText)
        card.addView(inputLabelRow)
        card.addView(entryPinInput)
        card.addView(entryErrorText)
        card.addView(entrySubmitBtn)
        card.addView(menuLabel)
        card.addView(actionRow)

        contentCol.addView(card)
        scroll.addView(contentCol)
        root.addView(scroll)
        root.addView(makeCopyrightFooter())
        outer.addView(root)
        return outer
    }

    private fun buildLicenseGateView(): FrameLayout {
        val dp = resources.displayMetrics.density
        val outer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F4F7FB"))
            isClickable = true
            isFocusable = true
            visibility = if (BuildConfig.REQUIRE_LICENSE_CHECK) View.VISIBLE else View.GONE
        }

        licWaveHeader = BdevWaveHeaderView(this, activeThemeColor).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (215 * dp).toInt()
            ).apply { gravity = Gravity.TOP }
        }
        outer.addView(licWaveHeader)

        val container = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((22 * dp).toInt(), (72 * dp).toInt(), (22 * dp).toInt(), (10 * dp).toInt())
        }

        val centerWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = makeRoundedBg("#FFFFFF", 28f, "#E2E8F0", 1f)
            elevation = 10f * dp
            setPadding((24 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val licLogoWrap = FrameLayout(this).apply {
            background = makeRoundedBg("#FFFFFF", 22f, activeThemeColor, 2f)
            elevation = 5f * dp
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams((84 * dp).toInt(), (84 * dp).toInt()).apply {
                bottomMargin = (14 * dp).toInt()
            }
        }
        licLogoView = ImageView(this).apply {
            setImageResource(R.mipmap.ic_launcher)
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        licLogoWrap.addView(licLogoView)

        val badge = TextView(this).apply {
            text = "VERIFIKASI LISENSI EXAM BROWSER"
            setTextColor(Color.parseColor("#047857"))
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = makeRoundedBg("#ECFDF5", 20f, "#A7F3D0", 1f)
            setPadding((14 * dp).toInt(), (5 * dp).toInt(), (14 * dp).toInt(), (5 * dp).toInt())
        }

        licTitleText = TextView(this).apply {
            text = "Memeriksa Lisensi..."
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, (8 * dp).toInt())
        }

        licDescText = TextView(this).apply {
            text = "Menghubungkan ke server lisensi resmi..."
            setTextColor(Color.parseColor("#475569"))
            textSize = 13.5f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (12 * dp).toInt())
        }

        licMetaText = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#B45309"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (20 * dp).toInt())
        }

        licRetryBtn = Button(this).apply {
            text = "Cek Ulang Status Lisensi"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = makeGradientButtonBg("#2563EB", 16f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
            setPadding((16 * dp).toInt(), (13 * dp).toInt(), (16 * dp).toInt(), (13 * dp).toInt())
            visibility = View.GONE
            setOnClickListener { verifyRemoteLicense() }
        }

        licWaBtn = Button(this).apply {
            text = "Hubungi Penyedia via WhatsApp"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = makeGradientButtonBg("#059669", 16f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding((16 * dp).toInt(), (13 * dp).toInt(), (16 * dp).toInt(), (13 * dp).toInt())
            visibility = View.GONE
            setOnClickListener {
                val wa = activeOwnerWa.replace(Regex("[^0-9]"), "")
                val msg = Uri.encode("Halo, saya ingin mengaktifkan/memperpanjang Exam Browser (${activeSchoolName.ifEmpty { BuildConfig.SCHOOL_SLUG }})")
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$wa?text=$msg")))
                } catch (_: Exception) {
                    Toast.makeText(this@MainActivity, "WhatsApp tidak terpasang.", Toast.LENGTH_SHORT).show()
                }
            }
        }

        card.addView(licLogoWrap)
        card.addView(badge)
        card.addView(licTitleText)
        card.addView(licDescText)
        card.addView(licMetaText)
        card.addView(licRetryBtn)
        card.addView(licWaBtn)

        centerWrap.addView(card)
        container.addView(centerWrap)
        container.addView(makeCopyrightFooter())
        outer.addView(container)
        return outer
    }

    private fun buildPinningGateView(): FrameLayout {
        val dp = resources.displayMetrics.density
        val outer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F4F7FB"))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }

        val pinWave = BdevWaveHeaderView(this, "#D97706").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (210 * dp).toInt()
            ).apply { gravity = Gravity.TOP }
        }
        outer.addView(pinWave)

        val container = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((22 * dp).toInt(), (72 * dp).toInt(), (22 * dp).toInt(), (10 * dp).toInt())
        }

        val centerWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = makeRoundedBg("#FFFFFF", 28f, "#E2E8F0", 1f)
            elevation = 10f * dp
            setPadding((24 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val badge = TextView(this).apply {
            text = "KEAMANAN KIOSK WAJIB"
            setTextColor(Color.parseColor("#B45309"))
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = makeRoundedBg("#FFFBEB", 20f, "#FCD34D", 1f)
            setPadding((14 * dp).toInt(), (5 * dp).toInt(), (14 * dp).toInt(), (5 * dp).toInt())
        }

        val title = TextView(this).apply {
            text = "Wajib Sematkan Aplikasi\n(App Pinning)"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 20.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, (10 * dp).toInt())
        }

        val desc = TextView(this).apply {
            text = "Ujian tidak dapat dibuka sebelum HP/Tablet Anda dalam mode Sematkan Aplikasi (Screen Pinning) agar tombol Home, Recent Apps, dan Sidebar terkunci.\n\n1. Tekan tombol hijau di bawah lalu pilih OKE / MENGERTI.\n2. Jika tidak muncul pop-up, aktifkan 'Penyematan Aplikasi / App Pinning' lewat Pengaturan Keamanan HP."
            setTextColor(Color.parseColor("#475569"))
            textSize = 13.5f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (22 * dp).toInt())
        }

        val btnPinNow = Button(this).apply {
            text = "Aktifkan Sematkan Layar Sekarang"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = makeGradientButtonBg("#059669", 16f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            setOnClickListener {
                startKioskLockMode()
                mainHandler.postDelayed({ enforceGates() }, 400L)
            }
        }

        val btnOpenSettings = Button(this).apply {
            text = "Buka Pengaturan Keamanan HP"
            setTextColor(Color.parseColor("#1D4ED8"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = makeRoundedBg("#EFF6FF", 16f, "#BFDBFE", 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS))
                } catch (_: Exception) {
                    try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
                }
            }
        }

        card.addView(badge)
        card.addView(title)
        card.addView(desc)
        card.addView(btnPinNow)
        card.addView(btnOpenSettings)

        centerWrap.addView(card)
        container.addView(centerWrap)
        container.addView(makeCopyrightFooter())
        outer.addView(container)
        return outer
    }

    private fun buildKeyboardGateView(): FrameLayout {
        val dp = resources.displayMetrics.density
        val outer = FrameLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setBackgroundColor(Color.parseColor("#F4F7FB"))
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }

        val kbWave = BdevWaveHeaderView(this, "#DC2626").apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                (210 * dp).toInt()
            ).apply { gravity = Gravity.TOP }
        }
        outer.addView(kbWave)

        val container = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding((22 * dp).toInt(), (72 * dp).toInt(), (22 * dp).toInt(), (10 * dp).toInt())
        }

        val centerWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = makeRoundedBg("#FFFFFF", 28f, "#E2E8F0", 1f)
            elevation = 10f * dp
            setPadding((24 * dp).toInt(), (28 * dp).toInt(), (24 * dp).toInt(), (24 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val badge = TextView(this).apply {
            text = "KEAMANAN KEYBOARD WAJIB"
            setTextColor(Color.parseColor("#BE123C"))
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = makeRoundedBg("#FFF1F2", 20f, "#FDA4AF", 1f)
            setPadding((14 * dp).toInt(), (5 * dp).toInt(), (14 * dp).toInt(), (5 * dp).toInt())
        }

        val title = TextView(this).apply {
            text = "Keyboard Pihak Ketiga / AI\nTerdeteksi!"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (16 * dp).toInt(), 0, (10 * dp).toInt())
        }

        kbDetectedNameText = TextView(this).apply {
            text = "Terdeteksi: Keyboard non-standar"
            setTextColor(Color.parseColor("#B91C1C"))
            textSize = 12f
            setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = makeRoundedBg("#FEF2F2", 14f, "#FECACA", 1f)
            setPadding((12 * dp).toInt(), (9 * dp).toInt(), (12 * dp).toInt(), (9 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (12 * dp).toInt() }
        }

        val desc = TextView(this).apply {
            text = "Demi kejujuran ujian, keyboard pihak ketiga / keyboard berfitur AI tidak diizinkan.\n\nSilakan ganti sementara ke Keyboard Bawaan HP (Gboard / Samsung / Huawei / Standar), lalu halaman ujian akan terbuka otomatis."
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, (20 * dp).toInt())
        }

        val btnSwitchKb = Button(this).apply {
            text = "Ganti Keyboard Sekarang"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = makeGradientButtonBg("#059669", 16f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (10 * dp).toInt() }
            setPadding((16 * dp).toInt(), (14 * dp).toInt(), (16 * dp).toInt(), (14 * dp).toInt())
            setOnClickListener {
                try {
                    val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                    imm.showInputMethodPicker()
                } catch (_: Exception) {
                    try { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) } catch (_: Exception) {}
                }
            }
        }

        val btnKbSettings = Button(this).apply {
            text = "Buka Pengaturan Keyboard HP"
            setTextColor(Color.parseColor("#1D4ED8"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = makeRoundedBg("#EFF6FF", 16f, "#BFDBFE", 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding((16 * dp).toInt(), (12 * dp).toInt(), (16 * dp).toInt(), (12 * dp).toInt())
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                } catch (_: Exception) {
                    try { startActivity(Intent(Settings.ACTION_SETTINGS)) } catch (_: Exception) {}
                }
            }
        }

        card.addView(badge)
        card.addView(title)
        card.addView(kbDetectedNameText)
        card.addView(desc)
        card.addView(btnSwitchKb)
        card.addView(btnKbSettings)

        centerWrap.addView(card)
        container.addView(centerWrap)
        container.addView(makeCopyrightFooter())
        outer.addView(container)
        return outer
    }

    private fun buildOverlayBlockerView(): LinearLayout {
        val container = LinearLayout(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#B91C1C"))
            setPadding(64, 64, 64, 64)
            isClickable = true
            isFocusable = true
            visibility = View.GONE
        }
        val title = TextView(this).apply {
            text = "PERINGATAN KEAMANAN!"
            setTextColor(Color.WHITE)
            textSize = 22f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        overlayReasonText = TextView(this).apply {
            text = "Terdeteksi Aplikasi Mengambang (Floating Window / Sidebar)."
            setTextColor(Color.parseColor("#FEE2E2"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 28, 0, 0)
        }
        container.addView(title)
        container.addView(overlayReasonText)
        return container
    }

    private fun showOverlayBlocker(reason: String) {
        if (!licenseApproved || !entryUnlocked || isKeyboardBlocked || (BuildConfig.REQUIRE_PINNING && !isAppPinned())) return
        mainHandler.post {
            overlayReasonText.text = "$reason\n\nTutup semua jendela mengambang (Floating Window / Sidebar / Split-Screen) agar soal muncul kembali!"
            overlayBlockerView.visibility = View.VISIBLE
        }
    }

    private fun hideOverlayBlocker() {
        mainHandler.post { overlayBlockerView.visibility = View.GONE }
    }

    private fun playNativeAlarm() {
        try {
            val toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)
            toneGen.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 450)
            mainHandler.postDelayed({ try { toneGen.release() } catch (_: Exception) {} }, 600)
        } catch (_: Exception) {}
    }

    private fun triggerNativeOverlayViolation(reason: String) {
        val now = System.currentTimeMillis()
        if (now - lastOverlayViolationTime < 2200L) return
        lastOverlayViolationTime = now
        playNativeAlarm()
        val safeReason = reason.replace("\\", "").replace("'", "").replace("\n", " ")
        mainHandler.post {
            webView.evaluateJavascript(
                "if (typeof window.__onNativeKioskViolation === 'function') { window.__onNativeKioskViolation('$safeReason'); }",
                null
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.userAgentString = "${settings.userAgentString} ${BuildConfig.KIOSK_UA_KEY}"

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        cookieManager.setAcceptThirdPartyCookies(webView, true)

        webView.isLongClickable = false
        webView.setOnLongClickListener { true }
        webView.isHapticFeedbackEnabled = false

        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return true
                val host = uri.host?.lowercase() ?: ""

                if (host.contains("wa.me") || host.contains("whatsapp.com") || uri.scheme == "whatsapp") {
                    try { startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (_: Exception) {}
                    return true
                }

                val isAllowed = dynamicAllowedHosts.any { host == it || host.endsWith(".$it") }
                if (isAllowed) return false

                Toast.makeText(this@MainActivity, "Akses situs luar diblokir!", Toast.LENGTH_SHORT).show()
                return true
            }
        }
    }

    private fun startKioskLockMode() {
        try {
            val am = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            if (am.lockTaskModeState == ActivityManager.LOCK_TASK_MODE_NONE) startLockTask()
        } catch (_: Exception) {}
    }

    private fun enableImmersiveFullscreen() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.setDecorFitsSystemWindows(false)
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            )
        }
    }

    private fun resetEntryLockOnReopen() {
        if (activeEntryPin.isNotEmpty()) {
            entryUnlocked = false
            mainHandler.post {
                entryPinInput.setText("")
                entryErrorText.text = ""
                overlayBlockerView.visibility = View.GONE
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        enforceGates()
        if (!hasFocus) {
            val gatePassed = licenseApproved && entryUnlocked && !isKeyboardBlocked && (!BuildConfig.REQUIRE_PINNING || isAppPinned())
            if (gatePassed && !isInternalDialogOpen && !isFinishing) {
                showOverlayBlocker("Aplikasi Mengambang / Sidebar / Notifikasi Terdeteksi!")
                triggerNativeOverlayViolation("Terdeteksi aplikasi mengambang (Floating Window / Sidebar)")
            }
            return
        }
        enableImmersiveFullscreen()
        val inMultiWindow = Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && (isInMultiWindowMode || isInPictureInPictureMode)
        if (inMultiWindow) {
            showOverlayBlocker("Mode Split-Screen / Layar Belah Dilarang!")
            triggerNativeOverlayViolation("Terdeteksi mode Split-Screen / Picture-in-Picture")
        } else {
            hideOverlayBlocker()
        }
    }

    override fun onPause() {
        super.onPause()
        val gatePassed = licenseApproved && entryUnlocked && !isKeyboardBlocked && (!BuildConfig.REQUIRE_PINNING || isAppPinned())
        if (gatePassed && !isInternalDialogOpen && !isFinishing) {
            showOverlayBlocker("Anda keluar dari layar ujian atau membuka jendela lain!")
            triggerNativeOverlayViolation("Keluar fokus aplikasi / membuka jendela mengambang")
        }
    }

    private fun handleReopenAuthIfNeeded() {
        if (!needsReopenAuth) return
        needsReopenAuth = false
        resetEntryLockOnReopen()
        val now = System.currentTimeMillis()
        if (now - lastRemoteSyncAt < 1500L) {
            enforceGates()
            return
        }
        lastRemoteSyncAt = now
        if (BuildConfig.REQUIRE_LICENSE_CHECK) {
            verifyRemoteLicense()
        } else {
            entryCheckReady = false
            fetchWebCbtBrandingAndEntryPin()
        }
        enforceGates()
    }

    override fun onStop() {
        super.onStop()
        if (!isInternalDialogOpen && !isFinishing) {
            needsReopenAuth = true
            resetEntryLockOnReopen()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        needsReopenAuth = true
        handleReopenAuthIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        enableImmersiveFullscreen()
        handleReopenAuthIfNeeded()
        enforceGates()
    }

    override fun onDestroy() {
        super.onDestroy()
        mainHandler.removeCallbacks(pinCheckRunnable)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isInternalDialogOpen && event.keyCode == KeyEvent.KEYCODE_BACK) {
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun showAppInfoDialog() {
        if (isInternalDialogOpen) return
        val dp = resources.displayMetrics.density

        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = makeRoundedBg("#FFFFFF", 26f, "#E2E8F0", 1f)
            setPadding((22 * dp).toInt(), (24 * dp).toInt(), (22 * dp).toInt(), (20 * dp).toInt())
        }

        val logoWrap = FrameLayout(this).apply {
            background = makeRoundedBg("#FFFFFF", 20f, activeThemeColor, 2f)
            elevation = 4f * dp
            setPadding((8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt(), (8 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams((78 * dp).toInt(), (78 * dp).toInt()).apply {
                bottomMargin = (12 * dp).toInt()
            }
        }
        val logoImg = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            if (cachedLogoBitmap != null) setImageBitmap(cachedLogoBitmap) else setImageResource(R.mipmap.ic_launcher)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        logoWrap.addView(logoImg)

        val appTitle = TextView(this).apply {
            text = activeAppName.ifEmpty { getString(R.string.app_name) }
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 17.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val verBadge = TextView(this).apply {
            text = "VERSI ${BuildConfig.VERSION_NAME}  •  EXAM BROWSER PRO"
            setTextColor(Color.parseColor("#64748B"))
            textSize = 10.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (3 * dp).toInt(), 0, (14 * dp).toInt())
        }

        val infoBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedBg("#F8FAFC", 16f, "#E2E8F0", 1f)
            setPadding((14 * dp).toInt(), (12 * dp).toInt(), (14 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (14 * dp).toInt() }
        }
        fun addRow(label: String, value: String) {
            val row = TextView(this).apply {
                text = "$label: $value"
                setTextColor(Color.parseColor("#334155"))
                textSize = 11.5f
                setPadding(0, (3 * dp).toInt(), 0, (3 * dp).toInt())
            }
            infoBox.addView(row)
        }
        addRow("Instansi", activeSchoolName.ifEmpty { BuildConfig.SCHOOL_SLUG })
        addRow("ID Paket", "com.bagustoo.cbt.${BuildConfig.SCHOOL_SLUG}")
        addRow("Masa Aktif", activeExpiredAt)
        addRow("Status", if (licenseApproved) "Lisensi Resmi Aktif" else "Menunggu Verifikasi")

        val devLabel = TextView(this).apply {
            text = "DEVELOPER"
            setTextColor(parseSafeColor(activeThemeColor))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        val devName = TextView(this).apply {
            text = "BDEV By Bagus too"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 13.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (2 * dp).toInt(), 0, (16 * dp).toInt())
        }

        val btnClose = Button(this).apply {
            text = "Tutup"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = makeGradientButtonBg(activeThemeColor, 14f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding((14 * dp).toInt(), (11 * dp).toInt(), (14 * dp).toInt(), (11 * dp).toInt())
        }

        card.addView(logoWrap)
        card.addView(appTitle)
        card.addView(verBadge)
        card.addView(infoBox)
        card.addView(devLabel)
        card.addView(devName)
        card.addView(btnClose)

        isInternalDialogOpen = true
        val dialog = AlertDialog.Builder(this)
            .setView(card)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnDismissListener {
            isInternalDialogOpen = false
            enableImmersiveFullscreen()
        }
        btnClose.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showExitPinDialog() {
        if (isInternalDialogOpen) return
        val dp = resources.displayMetrics.density
        var showPwd = false

        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = makeRoundedBg("#FFFFFF", 26f, "#E2E8F0", 1f)
            elevation = 12f * dp
            setPadding(0, 0, 0, (18 * dp).toInt())
        }

        // Header lembut berwarna crimson-rose modern
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#FFF1F2"), Color.parseColor("#FFE4E6"))
            ).apply {
                cornerRadii = floatArrayOf(26 * dp, 26 * dp, 26 * dp, 26 * dp, 0f, 0f, 0f, 0f)
            }
            setPadding((22 * dp).toInt(), (20 * dp).toInt(), (22 * dp).toInt(), (16 * dp).toInt())
        }

        val iconCircle = TextView(this).apply {
            text = "⏻"
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(Color.parseColor("#E11D48"), Color.parseColor("#DC2626"))
            ).apply { cornerRadius = 24f * dp }
            elevation = 4f * dp
            layoutParams = LinearLayout.LayoutParams((48 * dp).toInt(), (48 * dp).toInt()).apply {
                bottomMargin = (10 * dp).toInt()
            }
        }

        val badge = TextView(this).apply {
            text = "KUNCI KELUAR PENGAWAS"
            setTextColor(Color.parseColor("#BE123C"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            background = makeRoundedBg("#FFFFFF", 16f, "#FDA4AF", 1f)
            setPadding((12 * dp).toInt(), (4 * dp).toInt(), (12 * dp).toInt(), (4 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (8 * dp).toInt() }
        }

        val title = TextView(this).apply {
            text = "Keluar dari Exam Browser"
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 18.5f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        val sub = TextView(this).apply {
            text = "Masukkan Password Keluar Pengawas untuk melepas kunci layar ujian."
            setTextColor(Color.parseColor("#64748B"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(0, (4 * dp).toInt(), 0, 0)
        }

        header.addView(iconCircle)
        header.addView(badge)
        header.addView(title)
        header.addView(sub)

        val bodyWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding((22 * dp).toInt(), (18 * dp).toInt(), (22 * dp).toInt(), 0)
        }

        val labelRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = (6 * dp).toInt() }
        }
        val lbl = TextView(this).apply {
            text = "Password Keluar"
            setTextColor(Color.parseColor("#334155"))
            textSize = 12f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val inputPin = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            hint = "Masukkan password keluar"
            setHintTextColor(Color.parseColor("#94A3B8"))
            setTextColor(Color.parseColor("#0F172A"))
            textSize = 15f
            setTypeface(null, Typeface.BOLD)
            background = makeRoundedBg("#F8FAFC", 14f, "#CBD5E1", 1.3f)
            setPadding((15 * dp).toInt(), (13 * dp).toInt(), (15 * dp).toInt(), (13 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val toggleBtn = TextView(this).apply {
            text = "Tampilkan"
            setTextColor(Color.parseColor("#0284C7"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setOnClickListener {
                showPwd = !showPwd
                inputPin.inputType = if (showPwd) {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                } else {
                    InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                }
                inputPin.setSelection(inputPin.text?.length ?: 0)
                text = if (showPwd) "Sembunyikan" else "Tampilkan"
            }
        }
        labelRow.addView(lbl)
        labelRow.addView(toggleBtn)

        val errTxt = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#DC2626"))
            textSize = 11.5f
            setTypeface(null, Typeface.BOLD)
            setPadding((4 * dp).toInt(), (7 * dp).toInt(), (4 * dp).toInt(), (10 * dp).toInt())
        }

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val btnCancel = Button(this).apply {
            text = "Batal"
            setTextColor(Color.parseColor("#475569"))
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = makeRoundedBg("#F1F5F9", 14f, "#CBD5E1", 1f)
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = (8 * dp).toInt()
            }
        }
        val btnExit = Button(this).apply {
            text = "Keluar Aplikasi"
            setTextColor(Color.WHITE)
            textSize = 13f
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.parseColor("#DC2626"), Color.parseColor("#E11D48"))
            ).apply { cornerRadius = 14f * dp }
            setPadding((12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt(), (12 * dp).toInt())
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.35f)
        }
        btnRow.addView(btnCancel)
        btnRow.addView(btnExit)

        val footer = TextView(this).apply {
            text = COPYRIGHT_FOOTER
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 10f
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, (14 * dp).toInt(), 0, 0)
        }

        bodyWrap.addView(labelRow)
        bodyWrap.addView(inputPin)
        bodyWrap.addView(errTxt)
        bodyWrap.addView(btnRow)
        bodyWrap.addView(footer)

        box.addView(header)
        box.addView(bodyWrap)

        isInternalDialogOpen = true
        val dialog = AlertDialog.Builder(this)
            .setView(box)
            .setCancelable(true)
            .create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnDismissListener {
            isInternalDialogOpen = false
            enableImmersiveFullscreen()
        }
        btnCancel.setOnClickListener { dialog.dismiss() }
        btnExit.setOnClickListener {
            if (inputPin.text.toString().trim() == activeExitPin) {
                dialog.dismiss()
                resetEntryLockOnReopen()
                try { stopLockTask() } catch (_: Exception) {}
                try { finishAndRemoveTask() } catch (_: Exception) {}
                finishAffinity()
            } else {
                errTxt.text = "Password keluar salah! Hanya pengawas yang dapat keluar."
                playNativeAlarm()
            }
        }
        dialog.show()
    }
}
