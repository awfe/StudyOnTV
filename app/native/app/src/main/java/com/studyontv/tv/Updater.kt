package com.studyontv.tv

import android.content.Context
import android.content.Intent
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * 版本检查：只使用蒲公英应用内更新接口（appKey + 当前版本）。
 * 不再访问 GitHub 或第三方镜像；蒲公英失败时由更新页引导用户手动下载。
 */
object Updater {
    const val OWNER_REPO = "chadyi/StudyOnTV"
    val DEFAULT_MIRRORS = emptyList<String>()
    private const val CACHE_MS = 6L * 60 * 60 * 1000

    data class Info(
        val latest: String,
        val current: String,
        val force: Boolean,
        val notes: String,
        val url: String,
        val sha256: String = "",
        val source: String = ""
    )

    fun api(ctx: Context): String = Prefs.updateApi(ctx)

    fun mirrors(ctx: Context): List<String> {
        val saved = Prefs.updateMirrors(ctx) ?: return DEFAULT_MIRRORS
        return saved.split('\n', ',', ' ').map { it.trim() }.filter { it.startsWith("https://") }
            .map { if (it.endsWith("/")) it else "$it/" }.distinct()
    }

    /** 可用的 APK 下载地址 */
    fun downloadCandidates(ctx: Context, url: String): List<Pair<String, String>> =
        listOf("蒲公英" to url)

    fun check(ctx: Context, forceRefresh: Boolean = false, onResult: (Info?) -> Unit) {
        if (LocalOnlyGuard.enabled) {
            onResult(null)
            return
        }
        val cachedAt = Prefs.updateCheckedAt(ctx)
        val cached = cachedInfo(ctx)
        if (!forceRefresh && cached != null && System.currentTimeMillis() - cachedAt < CACHE_MS) {
            onResult(cached)
            return
        }
        Thread { onResult(checkNow(ctx).first) }.apply { isDaemon = true }.start()
    }

    private val checkingFlag = java.util.concurrent.atomic.AtomicBoolean(false)
    val checking: Boolean get() = checkingFlag.get()

    /** Web 端一键在线更新的线程安全状态机快照（ST-603 / #184）。 */
    data class InstallSnapshot(
        val state: String = "idle",      // "idle" | "downloading" | "installing" | "error"
        val progress: Int = 0,           // 0..100
        val message: String = "",
        val version: String = "",
        val updatedAtMs: Long = 0L
    )

    internal const val INSTALLING_TTL_MS = 60_000L
    internal const val INSTALLING_RETRY_COOLDOWN_MS = 15_000L

    private val installSnapshotRef = AtomicReference(InstallSnapshot())

    internal fun currentInstallSnapshot(nowMs: Long = System.currentTimeMillis()): InstallSnapshot {
        val current = installSnapshotRef.get()
        if (current.state == "installing" && current.updatedAtMs > 0L && nowMs - current.updatedAtMs >= INSTALLING_TTL_MS) {
            val idle = InstallSnapshot()
            if (installSnapshotRef.compareAndSet(current, idle)) return idle
            return installSnapshotRef.get()
        }
        return current
    }

    internal fun resetInstallState() {
        installSnapshotRef.set(InstallSnapshot())
    }

    internal fun updateInstallState(
        state: String,
        progress: Int,
        message: String,
        version: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        installSnapshotRef.set(InstallSnapshot(state, progress.coerceIn(0, 100), message, version, nowMs))
    }

    /** 校验是否允许发起远程在线更新；返回 null 表示通过，否则返回 (HTTP 状态码, 错误文案)。 */
    internal fun validateRemoteInstallPrecondition(localOnly: Boolean, newerInfo: Info?): Pair<Int, String>? = when {
        localOnly -> 403 to "纯本地版本不支持在线更新"
        newerInfo == null -> 400 to "当前没有可安装的新版本，请先检查更新"
        newerInfo.url.isBlank() -> 400 to "当前版本缺少下载地址，请先检查更新"
        else -> null
    }

    /**
     * 原子尝试进入 downloading 状态：
     * - downloading 期间严格拒绝并发重入；
     * - installing 期间在冷却期内拒绝重入，超过冷却期（用户取消安装或系统静默拦截后台拉起）允许重新发起。
     */
    internal fun tryBeginInstall(version: String, nowMs: Long = System.currentTimeMillis()): Boolean {
        while (true) {
            val current = installSnapshotRef.get()
            if (current.state == "downloading") return false
            if (current.state == "installing" &&
                (current.updatedAtMs <= 0L || nowMs - current.updatedAtMs < INSTALLING_RETRY_COOLDOWN_MS)
            ) {
                return false
            }
            val next = InstallSnapshot(
                state = "downloading",
                progress = 0,
                message = "正在准备下载 v$version…",
                version = version,
                updatedAtMs = nowMs
            )
            if (installSnapshotRef.compareAndSet(current, next)) return true
        }
    }

    internal fun appendInstallStatus(target: JSONObject, snap: InstallSnapshot = currentInstallSnapshot()): JSONObject =
        target.put("installState", snap.state)
            .put("installProgress", snap.progress)
            .put("installMessage", snap.message)
            .put("installVersion", snap.version)

    /**
     * Web 家长端一键触发设备在线更新（ST-603 / #184）：
     * 1. EU Local-Only 直接返回 403；
     * 2. 无新版本返回 400；
     * 3. 防并发重入，后台复用 ApkInstaller.download + 校验，完成后拉起 UpdateActivity（带本地 apkPath）或回退系统安装会话。
     */
    fun startRemoteInstall(ctx: Context): Pair<Int, JSONObject> {
        val info = newerThanCurrent(ctx, cachedInfo(ctx))
        validateRemoteInstallPrecondition(LocalOnlyGuard.enabled, info)?.let { (code, message) ->
            return code to JSONObject().put("error", message)
        }
        val targetInfo = info!!
        if (!tryBeginInstall(targetInfo.latest)) {
            return 200 to status(ctx)
        }
        val appCtx = ctx.applicationContext ?: ctx
        Thread {
            try {
                val cachedApk = ApkInstaller.apkFile(appCtx)
                val readyFile = if (
                    ApkInstaller.alreadyDownloaded(appCtx, 0) &&
                    UpdateActivity.isCachedApkMatching(cachedApk, targetInfo.sha256, deleteOnMismatch = true)
                ) {
                    updateInstallState("downloading", 100, "已命中本地校验通过的安装包 v${targetInfo.latest}", targetInfo.latest)
                    cachedApk
                } else {
                    ApkInstaller.download(
                        appCtx,
                        targetInfo.url,
                        targetInfo.sha256,
                        onSource = { src ->
                            val cur = currentInstallSnapshot()
                            if (cur.state == "downloading") {
                                updateInstallState("downloading", cur.progress, "正在从 $src 下载 v${targetInfo.latest}…", targetInfo.latest)
                            }
                        },
                        onProgress = { percent ->
                            val pct = if (percent < 0) currentInstallSnapshot().progress else percent.coerceIn(0, 100)
                            val msg = if (percent < 0) "正在下载 v${targetInfo.latest}…" else "正在下载 v${targetInfo.latest} ($pct%)"
                            updateInstallState("downloading", pct, msg, targetInfo.latest)
                        }
                    )
                }

                if (readyFile == null) {
                    val err = ApkInstaller.lastError.ifBlank { "下载失败，请检查设备网络后重试" }
                    updateInstallState("error", 0, err, targetInfo.latest)
                    return@Thread
                }

                updateInstallState("installing", 100, "安装包已下载完成，正在拉起设备安装界面…", targetInfo.latest)
                GateService.allowInstallPage()
                val launched = try {
                    val intent = Intent(appCtx, UpdateActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("payload", toJson(targetInfo).toString())
                        putExtra("apkPath", readyFile.absolutePath)
                        putExtra("auto", true)
                    }
                    appCtx.startActivity(intent)
                    true
                } catch (e: Exception) {
                    Diag.warn("Update", "remote startActivity(UpdateActivity) blocked: ${e.javaClass.simpleName} ${e.message}")
                    false
                }

                if (launched) {
                    updateInstallState("installing", 100, "安装包已下载完成，请在设备屏幕上确认安装", targetInfo.latest)
                } else {
                    val fallbackOk = try { ApkInstaller.install(appCtx, readyFile) } catch (_: Exception) { false }
                    if (fallbackOk) {
                        updateInstallState("installing", 100, "安装包已提交系统安装器，请在设备屏幕上确认安装", targetInfo.latest)
                    } else {
                        val reason = ApkInstaller.lastError.ifBlank { "系统限制了后台拉起安装界面，请在电视/平板上打开应用完成安装" }
                        updateInstallState("error", 100, reason, targetInfo.latest)
                    }
                }
            } catch (e: Exception) {
                Diag.error("Update", "remote install task failed", e)
                updateInstallState("error", 0, "在线更新失败：${e.message ?: e.javaClass.simpleName}", targetInfo.latest)
            }
        }.apply { isDaemon = true }.start()
        return 200 to status(ctx)
    }

    /** 管理面板“立即检查”：后台执行，面板轮询 status()（不能加锁，否则会等正在进行的检查） */
    fun startCheck(ctx: Context) {
        if (LocalOnlyGuard.enabled) return
        if (!checkingFlag.compareAndSet(false, true)) return
        Thread { try { checkNow(ctx) } finally { checkingFlag.set(false) } }.apply { isDaemon = true }.start()
    }

    /** 同步检查；返回结果和每个来源的尝试记录（给管理面板显示） */
    @Synchronized
    fun checkNow(ctx: Context): Pair<Info?, JSONArray> {
        val log = JSONArray()
        if (LocalOnlyGuard.enabled) {
            log.put(JSONObject().put("source", "LocalOnly").put("ok", false).put("ms", 0)
                .put("message", "仅本地隐私版（EU Local-Only）已禁用在线更新"))
            Prefs.setUpdateInfo(ctx, "")
            Prefs.setUpdateCheckedAt(ctx, System.currentTimeMillis())
            Prefs.setUpdateLog(ctx, JSONObject().put("at", System.currentTimeMillis()).put("items", log).toString())
            return null to log
        }
        val current = currentVersion(ctx)
        val started = System.currentTimeMillis()
        var info: Info? = null
        try {
            info = Pgyer.info(ctx, current, currentVersionCode(ctx))
            log.put(JSONObject().put("source", "蒲公英").put("ok", info != null).put("ms", System.currentTimeMillis() - started)
                .put("message", if (info == null) "当前已是最新版本" else "v" + info.latest))
        } catch (e: Exception) {
            Diag.error("Update", "Pgyer check failed", e)
            log.put(JSONObject().put("source", "蒲公英").put("ok", false).put("ms", System.currentTimeMillis() - started)
                .put("message", e.message?.take(160) ?: e.javaClass.simpleName))
        }
        Prefs.setUpdateInfo(ctx, info?.let { toJson(it).toString() } ?: "")
        Prefs.setUpdateCheckedAt(ctx, System.currentTimeMillis())
        Prefs.setUpdateLog(ctx, JSONObject().put("at", System.currentTimeMillis()).put("items", log).toString())
        return info to log
    }

    fun toJson(info: Info): JSONObject = JSONObject()
        .put("latest", info.latest).put("current", info.current).put("force", info.force)
        .put("notes", info.notes).put("url", info.url).put("sha256", info.sha256).put("source", info.source)

    internal fun effectiveCachedInfo(cached: Info?, localOnly: Boolean): Info? = if (localOnly) null else cached

    fun cachedInfo(ctx: Context): Info? {
        if (LocalOnlyGuard.enabled) return null
        val raw = Prefs.updateInfo(ctx)
        if (raw.isEmpty()) return null
        return try {
            val o = JSONObject(raw)
            Info(o.optString("latest"), o.optString("current"), o.optBoolean("force"),
                o.optString("notes"), o.optString("url"), o.optString("sha256"), o.optString("source"))
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 比较远程版本是否比本地版本更新（遵循语义化版本 SemVer）：
     * 主版本号段 `[major, minor, patch]` 与先行版后缀（如 `-beta`）分开解析，
     * 保证正式版 `0.4.4` 比同号内测包 `0.4.4-beta` 新，而 `0.4.4-beta` 不会被判成比正式版 `0.4.4` 新。
     */
    fun isNewer(latest: String, current: String): Boolean {
        val remoteClean = latest.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        val localClean = current.trim().removePrefix("v").removePrefix("V").substringBefore('+')
        if (remoteClean.isEmpty()) return false
        val remoteCore = remoteClean.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val localCore = localClean.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteCore.size, localCore.size)) {
            val x = remoteCore.getOrElse(i) { 0 }
            val y = localCore.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        val remotePre = remoteClean.substringAfter('-', "")
        val localPre = localClean.substringAfter('-', "")
        if (remotePre.isEmpty() && localPre.isEmpty()) return false
        if (remotePre.isEmpty() && localPre.isNotEmpty()) return true
        if (remotePre.isNotEmpty() && localPre.isEmpty()) return false
        val rParts = remotePre.split('.', '-')
        val lParts = localPre.split('.', '-')
        for (i in 0 until minOf(rParts.size, lParts.size)) {
            val rp = rParts[i]
            val lp = lParts[i]
            if (rp == lp) continue
            val rn = rp.toIntOrNull()
            val ln = lp.toIntOrNull()
            return when {
                rn != null && ln != null -> rn > ln
                rn != null -> false
                ln != null -> true
                else -> rp > lp
            }
        }
        return rParts.size > lParts.size
    }

    fun currentVersion(ctx: Context): String =
        try { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0.0.0" } catch (_: Exception) { "0.0.0" }

    fun currentVersionCode(ctx: Context): Int = try {
        val info = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) info.longVersionCode.toInt() else @Suppress("DEPRECATION") info.versionCode
    } catch (_: Exception) { 0 }

    fun newerThanCurrent(ctx: Context, info: Info?): Info? =
        if (info != null && isNewer(info.latest, currentVersion(ctx))) info else null

    /** 管理面板用：当前状态 */
    fun status(ctx: Context): JSONObject {
        val info = effectiveCachedInfo(cachedInfo(ctx), LocalOnlyGuard.enabled)
        val hasUpdate = newerThanCurrent(ctx, info) != null
        val snap = currentInstallSnapshot().let { cur ->
            if (!hasUpdate && cur.state == "installing") {
                resetInstallState()
                InstallSnapshot()
            } else cur
        }
        val log = try { JSONObject(Prefs.updateLog(ctx)) } catch (_: Exception) { JSONObject() }
        return appendInstallStatus(
            JSONObject().put("current", currentVersion(ctx))
                .put("latest", info?.latest ?: "").put("hasUpdate", hasUpdate)
                .put("notes", info?.notes ?: "").put("source", info?.source ?: "")
                .put("url", info?.url ?: "")
                .put("checkedAt", Prefs.updateCheckedAt(ctx)).put("checking", checking)
                .put("logAt", log.optLong("at"))
                .put("api", api(ctx)).put("mirrors", JSONArray(mirrors(ctx))).put("defaultMirrors", JSONArray(DEFAULT_MIRRORS))
                .put("lastDownloadSource", Prefs.updateLastSource(ctx))
                .put("canInstall", ApkInstaller.canInstall(ctx))
                .put("installError", Prefs.updateInstallError(ctx))
                .put("log", log.optJSONArray("items") ?: JSONArray()),
            snap
        )
    }

    fun saveSettings(ctx: Context, input: JSONObject): JSONObject {
        if (input.has("api")) {
            val api = input.optString("api").trim()
            require(api.isEmpty() || api.startsWith("https://")) { "自定义更新地址需要以 https:// 开头" }
            Prefs.setUpdateApi(ctx, api)
        }
        if (input.has("mirrors")) {
            val raw = input.get("mirrors")
            val list = if (raw is JSONArray) (0 until raw.length()).map { raw.optString(it) } else raw.toString().split('\n', ',')
            val clean = list.map { it.trim() }.filter { it.isNotEmpty() }
            clean.forEach { require(it.startsWith("https://")) { "镜像地址需要以 https:// 开头：$it" } }
            require(clean.size <= 10) { "最多填写 10 个镜像" }
            Prefs.setUpdateMirrors(ctx, if (input.optBoolean("resetMirrors")) null else clean.joinToString("\n"))
        }
        Prefs.setUpdateCheckedAt(ctx, 0)
        return status(ctx)
    }
}
