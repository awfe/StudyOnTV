package com.studyontv.tv

import android.content.Context
import android.content.Intent
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference

/**
 * APK 预下载、本地安装包校验与 Web 一键远程更新的线程安全状态机（ST-603 / ST-607）。
 */
internal object UpdateDownload {
    private val installSnapshotRef = AtomicReference(Updater.InstallSnapshot())

    fun checkApkReady(
        fileExists: Boolean,
        fileLength: Long,
        expectedVersion: String,
        expectedSha256: String,
        savedVersion: String,
        savedSha256: String
    ): Boolean {
        val targetVer = expectedVersion.trim()
        if (!fileExists || fileLength <= 0L || targetVer.isEmpty()) return false
        if (savedVersion.trim() != targetVer) return false
        val wantSha = expectedSha256.trim()
        if (wantSha.isNotEmpty() && !savedSha256.trim().equals(wantSha, ignoreCase = true)) return false
        return true
    }

    fun isApkReady(ctx: Context, info: Updater.Info?): Boolean {
        if (LocalOnlyGuard.enabled || info == null) return false
        val f = ApkInstaller.apkFile(ctx)
        val exists = f.exists() && f.isFile
        return checkApkReady(
            fileExists = exists,
            fileLength = if (exists) f.length() else 0L,
            expectedVersion = info.latest,
            expectedSha256 = info.sha256,
            savedVersion = Prefs.readyApkVersion(ctx),
            savedSha256 = Prefs.readyApkSha256(ctx)
        )
    }

    fun markApkReady(ctx: Context, version: String, sha256: String) {
        Prefs.setReadyApkVersion(ctx, version.trim())
        Prefs.setReadyApkSha256(ctx, sha256.trim())
    }

    fun clearReadyApk(ctx: Context) {
        Prefs.setReadyApkVersion(ctx, "")
        Prefs.setReadyApkSha256(ctx, "")
    }

    fun resolveAutoDownloadSetting(requested: Boolean, localOnly: Boolean): Boolean =
        if (localOnly) false else requested

    fun shouldPreDownload(
        localOnly: Boolean,
        autoDownload: Boolean,
        newerInfo: Updater.Info?,
        apkReady: Boolean
    ): Boolean = !localOnly && autoDownload && newerInfo != null && newerInfo.url.isNotBlank() && !apkReady

    fun currentInstallSnapshot(nowMs: Long = System.currentTimeMillis()): Updater.InstallSnapshot {
        val current = installSnapshotRef.get()
        if (current.state == "installing" && current.updatedAtMs > 0L &&
            nowMs - current.updatedAtMs >= Updater.INSTALLING_TTL_MS
        ) {
            val idle = Updater.InstallSnapshot()
            if (installSnapshotRef.compareAndSet(current, idle)) return idle
            return installSnapshotRef.get()
        }
        return current
    }

    fun resetInstallState() {
        installSnapshotRef.set(Updater.InstallSnapshot())
    }

    fun updateInstallState(
        state: String,
        progress: Int,
        message: String,
        version: String,
        nowMs: Long = System.currentTimeMillis()
    ): Updater.InstallSnapshot {
        val next = Updater.InstallSnapshot(state, progress.coerceIn(0, 100), message, version, nowMs)
        installSnapshotRef.set(next)
        return next
    }

    fun compareAndUpdateInstallState(
        expected: Updater.InstallSnapshot,
        state: String,
        progress: Int,
        message: String,
        version: String,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = installSnapshotRef.compareAndSet(
        expected,
        Updater.InstallSnapshot(state, progress.coerceIn(0, 100), message, version, nowMs)
    )

    fun onInstallPendingUserAction(nowMs: Long = System.currentTimeMillis()) {
        val cur = currentInstallSnapshot(nowMs)
        if (cur.state == "installing") {
            updateInstallState("installing", 100, "安装包已就绪，请在设备屏幕上确认安装", cur.version, nowMs)
        }
    }

    fun onInstallResult(success: Boolean, message: String = "", nowMs: Long = System.currentTimeMillis()) {
        val cur = currentInstallSnapshot(nowMs)
        if (success) {
            resetInstallState()
        } else {
            updateInstallState("error", 100, message.ifBlank { "安装失败" }, cur.version, nowMs)
        }
    }

    /** 校验是否允许发起远程在线更新；返回 null 表示通过，否则返回 (HTTP 状态码, 错误文案)。 */
    fun validateRemoteInstallPrecondition(localOnly: Boolean, newerInfo: Updater.Info?): Pair<Int, String>? = when {
        localOnly -> 403 to "纯本地版本不支持在线更新"
        newerInfo == null -> 400 to "当前没有可安装的新版本，请先检查更新"
        newerInfo.url.isBlank() -> 400 to "当前版本缺少下载地址，请先检查更新"
        else -> null
    }

    /**
     * 原子尝试进入 downloading 状态：
     * downloading 期间严格拒绝并发重入；installing 期间仅在冷却期后允许重新发起。
     */
    fun tryBeginInstall(
        version: String,
        nowMs: Long = System.currentTimeMillis(),
        message: String = "正在准备下载 v$version…"
    ): Boolean {
        while (true) {
            val current = installSnapshotRef.get()
            if (current.state == "downloading") return false
            if (current.state == "installing" &&
                (current.updatedAtMs <= 0L || nowMs - current.updatedAtMs < Updater.INSTALLING_RETRY_COOLDOWN_MS)
            ) {
                return false
            }
            val next = Updater.InstallSnapshot(
                state = "downloading",
                progress = 0,
                message = message,
                version = version,
                updatedAtMs = nowMs
            )
            if (installSnapshotRef.compareAndSet(current, next)) return true
        }
    }

    /** 发现新版本且家长开启了后台自动预下载时，静默在后台下载并校验 APK 到本地缓存。 */
    fun preDownloadIfNeeded(ctx: Context, info: Updater.Info?) {
        val newer = Updater.newerThanCurrent(ctx, info)
        val autoEnabled = resolveAutoDownloadSetting(Prefs.autoDownloadUpdate(ctx), LocalOnlyGuard.enabled)
        if (!shouldPreDownload(LocalOnlyGuard.enabled, autoEnabled, newer, isApkReady(ctx, newer))) {
            return
        }
        val targetInfo = newer!!
        if (!tryBeginInstall(targetInfo.latest, message = "正在后台预下载 v${targetInfo.latest}…")) {
            return
        }
        val appCtx = ctx.applicationContext ?: ctx
        Thread {
            try {
                Diag.info("Update", "starting background pre-download for v${targetInfo.latest}")
                val file = ApkInstaller.download(
                    appCtx,
                    targetInfo.url,
                    targetInfo.sha256,
                    onSource = { src ->
                        val cur = currentInstallSnapshot()
                        if (cur.state == "downloading") {
                            updateInstallState("downloading", cur.progress, "正在后台从 $src 预下载 v${targetInfo.latest}…", targetInfo.latest)
                        }
                    },
                    onProgress = { percent ->
                        val pct = if (percent < 0) currentInstallSnapshot().progress else percent.coerceIn(0, 100)
                        val msg = if (percent < 0) "正在后台预下载 v${targetInfo.latest}…" else "正在后台预下载 v${targetInfo.latest} ($pct%)"
                        updateInstallState("downloading", pct, msg, targetInfo.latest)
                    }
                )
                if (file != null) {
                    markApkReady(appCtx, targetInfo.latest, targetInfo.sha256.ifBlank { ApkInstaller.lastDownloadSha256 })
                    Diag.info("Update", "background pre-download ready for v${targetInfo.latest}")
                } else {
                    Diag.warn("Update", "background pre-download failed: ${ApkInstaller.lastError}")
                }
                resetInstallState()
            } catch (e: Exception) {
                Diag.warn("Update", "background pre-download error: ${e.javaClass.simpleName} ${e.message}")
                resetInstallState()
            }
        }.apply { isDaemon = true }.start()
    }

    fun appendInstallStatus(target: JSONObject, snap: Updater.InstallSnapshot = currentInstallSnapshot()): JSONObject =
        target.put("installState", snap.state)
            .put("installProgress", snap.progress)
            .put("installMessage", snap.message)
            .put("installVersion", snap.version)

    /** Web 家长端一键触发设备在线更新：校验前置条件、防并发重入、复用下载校验并触发安装。 */
    fun startRemoteInstall(ctx: Context): Pair<Int, JSONObject> {
        val info = Updater.newerThanCurrent(ctx, Updater.cachedInfo(ctx))
        validateRemoteInstallPrecondition(LocalOnlyGuard.enabled, info)?.let { (code, message) ->
            return code to JSONObject().put("error", message)
        }
        val targetInfo = info!!
        if (!tryBeginInstall(targetInfo.latest)) {
            return 200 to Updater.status(ctx)
        }
        val appCtx = ctx.applicationContext ?: ctx
        Thread {
            try {
                val cachedApk = ApkInstaller.apkFile(appCtx)
                val expectedSha = targetInfo.sha256.ifBlank { Prefs.readyApkSha256(appCtx) }
                val readyFile = if (
                    isApkReady(appCtx, targetInfo) &&
                    UpdateActivity.isCachedApkMatching(cachedApk, expectedSha, deleteOnMismatch = true)
                ) {
                    updateInstallState("installing", 100, "已命中本地预下载安装包 v${targetInfo.latest}，跳过下载直接安装", targetInfo.latest)
                    cachedApk
                } else {
                    clearReadyApk(appCtx)
                    val downloaded = ApkInstaller.download(
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
                    if (downloaded != null) {
                        markApkReady(appCtx, targetInfo.latest, targetInfo.sha256.ifBlank { ApkInstaller.lastDownloadSha256 })
                    }
                    downloaded
                }

                if (readyFile == null) {
                    val err = ApkInstaller.lastError.ifBlank { "下载失败，请检查设备网络后重试" }
                    updateInstallState("error", 0, err, targetInfo.latest)
                    return@Thread
                }

                val capability = Updater.silentCapability(appCtx)
                if (capability == Updater.SILENT_DEVICE_OWNER || capability == Updater.SILENT_SELF_UPDATE) {
                    val beforeSubmit = updateInstallState("installing", 100, "正在后台静默安装 v${targetInfo.latest}…", targetInfo.latest)
                    Prefs.setUpdateInProgress(appCtx, true)
                    val submitted = try { ApkInstaller.install(appCtx, readyFile) } catch (_: Exception) { false }
                    if (submitted) {
                        compareAndUpdateInstallState(beforeSubmit, "installing", 100, "已提交后台静默更新 v${targetInfo.latest}，安装完成后服务将自动恢复", targetInfo.latest)
                    } else {
                        Prefs.setUpdateInProgress(appCtx, false)
                        val reason = ApkInstaller.lastError.ifBlank { "后台提交安装会话失败，请在设备上手动完成安装" }
                        compareAndUpdateInstallState(beforeSubmit, "error", 100, reason, targetInfo.latest)
                    }
                    return@Thread
                }

                val beforeLaunch = updateInstallState("installing", 100, "安装包已下载完成，正在拉起设备安装界面…", targetInfo.latest)
                GateService.allowInstallPage()
                val launched = try {
                    val intent = Intent(appCtx, UpdateActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        putExtra("payload", Updater.toJson(targetInfo).toString())
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
                    compareAndUpdateInstallState(beforeLaunch, "installing", 100, "安装包已下载完成，请在设备屏幕上确认安装", targetInfo.latest)
                } else {
                    val fallbackOk = try { ApkInstaller.install(appCtx, readyFile) } catch (_: Exception) { false }
                    if (fallbackOk) {
                        compareAndUpdateInstallState(beforeLaunch, "installing", 100, "安装包已提交系统安装器，请在设备屏幕上确认安装", targetInfo.latest)
                    } else {
                        val reason = ApkInstaller.lastError.ifBlank { "系统限制了后台拉起安装界面，请在电视/平板上打开应用完成安装" }
                        compareAndUpdateInstallState(beforeLaunch, "error", 100, reason, targetInfo.latest)
                    }
                }
            } catch (e: Exception) {
                Diag.error("Update", "remote install task failed", e)
                updateInstallState("error", 0, "在线更新失败：${e.message ?: e.javaClass.simpleName}", targetInfo.latest)
            }
        }.apply { isDaemon = true }.start()
        return 200 to Updater.status(ctx)
    }
}
