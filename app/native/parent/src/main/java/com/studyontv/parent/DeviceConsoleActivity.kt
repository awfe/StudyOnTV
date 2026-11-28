package com.studyontv.parent

import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.JsPromptResult
import android.webkit.JsResult
import android.webkit.URLUtil
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.Toast
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors

/**
 * 家长管理后台容器：
 * 直接加载目标电视/平板本地 HTTP 服务的 `http://<ip>:<port>/admin/`，100% 复用 `parent-ui` 全部功能，
 * 补齐原生返回键导航、文件选择器（Word/PDF/图片/题库导入）、备份导出下载、断线友好提示与一键复制网址。
 */
class DeviceConsoleActivity : Activity() {
    companion object {
        const val EXTRA_DEVICE_ID = "device_id"
        const val EXTRA_DEVICE_NAME = "device_name"
        const val EXTRA_IP = "ip"
        const val EXTRA_PORT = "port"

        private const val REQ_FILE_CHOOSER = 4101
        private const val REQ_SAVE_DOWNLOAD = 4102
    }

    private val io = Executors.newSingleThreadExecutor()

    private lateinit var views: DeviceConsoleViews
    private lateinit var targetHost: LanNet.ParsedHost
    private var deviceName: String = ""
    private var pendingFileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingDownloadUrl: String? = null
    private var pendingDownloadUserAgent: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val palette = ParentUi.palette(this)
        ParentUi.applySystemBars(this, palette)

        val ip = intent.getStringExtra(EXTRA_IP).orEmpty()
        val port = intent.getIntExtra(EXTRA_PORT, LanNet.DEFAULT_HTTP_PORT)
        val parsed = LanNet.parsePrivateLanHost("$ip:$port", LanNet.DEFAULT_HTTP_PORT)
        if (parsed == null) {
            Toast.makeText(this, "无效的局域网设备地址", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        targetHost = parsed
        deviceName = intent.getStringExtra(EXTRA_DEVICE_NAME).orEmpty().ifBlank { parsed.ip }

        views = DeviceConsoleViews(
            activity = this,
            p = palette,
            deviceName = deviceName,
            targetHost = targetHost,
            callbacks = object : DeviceConsoleViews.Callbacks {
                override fun onBack() = finish()
                override fun onReload() {
                    views.hideOffline()
                    views.webView.reload()
                }

                override fun onCopyUrl() {
                    val adminUrl = "http://${targetHost.host}/admin/"
                    ParentUi.copyToClipboard(this@DeviceConsoleActivity, "StudyOnTV Admin", adminUrl, "已复制 $adminUrl")
                }

                override fun onReconnect() {
                    views.hideOffline()
                    loadAdminHome()
                }

                override fun onOpenAdbFix() {
                    startActivity(
                        Intent(this@DeviceConsoleActivity, AdbWizardActivity::class.java)
                            .putExtra(AdbWizardActivity.EXTRA_PREFILL_IP, targetHost.ip)
                            .putExtra(AdbWizardActivity.EXTRA_FIX_ONLY, true)
                    )
                }
            }
        )
        setContentView(views.rootView)
        configureWebView()
        loadAdminHome()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun configureWebView() {
        val webView = views.webView
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false
            allowContentAccess = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return handleUrlNavigation(request.url)
            }

            @Suppress("DEPRECATION")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                return handleUrlNavigation(Uri.parse(url))
            }

            private fun handleUrlNavigation(uri: Uri?): Boolean {
                if (uri == null) return true
                val scheme = uri.scheme?.lowercase().orEmpty()
                if (scheme == "http" && uri.host == targetHost.ip && (uri.port == targetHost.port || (uri.port == -1 && targetHost.port == 80))) {
                    return false // 同一局域网目标设备内导航，留在 WebView
                }
                if (scheme == "https" || scheme == "http") {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: Exception) {
                    }
                    return true
                }
                return true
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    val desc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) error.description?.toString().orEmpty() else ""
                    showOffline(desc)
                }
            }

            @Suppress("DEPRECATION")
            override fun onReceivedError(view: WebView, errorCode: Int, description: String?, failingUrl: String?) {
                showOffline(description.orEmpty())
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                views.updateProgress(newProgress)
            }

            override fun onShowFileChooser(
                webView: WebView,
                filePathCallback: ValueCallback<Array<Uri>>,
                fileChooserParams: FileChooserParams
            ): Boolean {
                pendingFileCallback?.onReceiveValue(null)
                pendingFileCallback = filePathCallback
                val allowMultiple = fileChooserParams.mode == FileChooserParams.MODE_OPEN_MULTIPLE
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = "*/*"
                    val acceptTypes = fileChooserParams.acceptTypes
                        ?.map { it.trim() }
                        ?.filter { it.isNotEmpty() && it.contains('/') }
                        .orEmpty()
                    if (acceptTypes.isNotEmpty()) {
                        putExtra(Intent.EXTRA_MIME_TYPES, acceptTypes.toTypedArray())
                    }
                    if (allowMultiple) putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
                return try {
                    @Suppress("DEPRECATION")
                    startActivityForResult(intent, REQ_FILE_CHOOSER)
                    true
                } catch (_: Exception) {
                    pendingFileCallback = null
                    false
                }
            }

            override fun onJsAlert(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@DeviceConsoleActivity)
                    .setMessage(message)
                    .setPositiveButton("知道了") { _, _ -> result.confirm() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsConfirm(view: WebView, url: String, message: String, result: JsResult): Boolean {
                AlertDialog.Builder(this@DeviceConsoleActivity)
                    .setMessage(message)
                    .setPositiveButton("确定") { _, _ -> result.confirm() }
                    .setNegativeButton("取消") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }

            override fun onJsPrompt(
                view: WebView,
                url: String,
                message: String,
                defaultValue: String?,
                result: JsPromptResult
            ): Boolean {
                val input = EditText(this@DeviceConsoleActivity).apply {
                    setText(defaultValue.orEmpty())
                }
                AlertDialog.Builder(this@DeviceConsoleActivity)
                    .setMessage(message)
                    .setView(input)
                    .setPositiveButton("确定") { _, _ -> result.confirm(input.text?.toString().orEmpty()) }
                    .setNegativeButton("取消") { _, _ -> result.cancel() }
                    .setOnCancelListener { result.cancel() }
                    .show()
                return true
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            if (!url.startsWith("http://${targetHost.host}/")) {
                Toast.makeText(this, "请使用电脑浏览器导出该文件", Toast.LENGTH_SHORT).show()
                return@DownloadListener
            }
            val fileName = URLUtil.guessFileName(url, contentDisposition, mimeType)
            pendingDownloadUrl = url
            pendingDownloadUserAgent = userAgent
            val saveIntent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream"
                putExtra(Intent.EXTRA_TITLE, fileName)
            }
            try {
                @Suppress("DEPRECATION")
                startActivityForResult(saveIntent, REQ_SAVE_DOWNLOAD)
            } catch (_: Exception) {
                Toast.makeText(this, "当前系统不支持保存文件对话框", Toast.LENGTH_SHORT).show()
            }
        })
    }

    private fun loadAdminHome() {
        views.webView.loadUrl("http://${targetHost.host}/admin/")
    }

    private fun showOffline(reason: String) {
        runOnUiThread { views.showOffline(reason) }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE_CHOOSER) {
            val cb = pendingFileCallback
            pendingFileCallback = null
            if (cb == null) return
            if (resultCode != RESULT_OK || data == null) {
                cb.onReceiveValue(null)
                return
            }
            val uris = ArrayList<Uri>()
            val clip = data.clipData
            if (clip != null) {
                for (i in 0 until clip.itemCount) {
                    clip.getItemAt(i)?.uri?.let { uris.add(it) }
                }
            } else {
                data.data?.let { uris.add(it) }
            }
            cb.onReceiveValue(if (uris.isEmpty()) null else uris.toTypedArray())
        } else if (requestCode == REQ_SAVE_DOWNLOAD) {
            val downloadUrl = pendingDownloadUrl
            val ua = pendingDownloadUserAgent
            pendingDownloadUrl = null
            pendingDownloadUserAgent = null
            val destUri = if (resultCode == RESULT_OK) data?.data else null
            if (downloadUrl != null && destUri != null) {
                saveHttpDownload(downloadUrl, ua, destUri)
            }
        }
    }

    private fun saveHttpDownload(downloadUrl: String, userAgent: String?, destUri: Uri) {
        Toast.makeText(this, "正在保存导出文件…", Toast.LENGTH_SHORT).show()
        val cookie = CookieManager.getInstance().getCookie(downloadUrl)
        io.execute {
            var conn: HttpURLConnection? = null
            try {
                conn = (URL(downloadUrl).openConnection(Proxy.NO_PROXY) as HttpURLConnection).apply {
                    connectTimeout = 8_000
                    readTimeout = 20_000
                    instanceFollowRedirects = false
                    if (!userAgent.isNullOrBlank()) setRequestProperty("User-Agent", userAgent)
                    if (!cookie.isNullOrBlank()) setRequestProperty("Cookie", cookie)
                }
                if (conn.responseCode !in 200..299) {
                    throw Exception("HTTP ${conn.responseCode}")
                }
                val outStream = contentResolver.openOutputStream(destUri)
                    ?: throw Exception("无法写入目标位置")
                conn.inputStream.use { input ->
                    outStream.use { output -> input.copyTo(output) }
                }
                runOnUiThread { Toast.makeText(this, "文件已保存成功", Toast.LENGTH_SHORT).show() }
            } catch (e: Exception) {
                runOnUiThread { Toast.makeText(this, "导出失败：${e.message}", Toast.LENGTH_LONG).show() }
            } finally {
                conn?.disconnect()
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (this::views.isInitialized && !views.isOfflineVisible() && views.webView.canGoBack()) {
            views.webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        pendingFileCallback?.onReceiveValue(null)
        pendingFileCallback = null
        io.shutdownNow()
        if (this::views.isInitialized) {
            views.webView.destroy()
        }
        super.onDestroy()
    }
}
