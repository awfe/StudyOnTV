package com.studyontv.tv

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class UpdaterTest {
    @Before
    fun resetState() {
        Updater.resetInstallState()
    }

    @Test
    fun releaseVersionIsNewerThanSameCoreBeta() {
        assertTrue(Updater.isNewer("0.4.4", "0.4.4-beta"))
        assertTrue(Updater.isNewer("v0.4.4", "0.4.4-beta"))
        assertFalse(Updater.isNewer("0.4.4-beta", "0.4.4"))
        assertFalse(Updater.isNewer("0.4.4-beta", "0.4.4-beta"))
    }

    @Test
    fun comparesCoreMajorMinorPatchAndPreReleaseSegments() {
        assertTrue(Updater.isNewer("0.4.5", "0.4.4"))
        assertTrue(Updater.isNewer("0.4.5-beta", "0.4.4"))
        assertTrue(Updater.isNewer("0.5.0", "0.4.99"))
        assertTrue(Updater.isNewer("1.0.0", "0.9.9-beta"))
        assertFalse(Updater.isNewer("0.4.4", "0.4.4"))
        assertFalse(Updater.isNewer("0.4.3", "0.4.4-beta"))
        assertTrue(Updater.isNewer("0.4.4-beta.2", "0.4.4-beta.1"))
        assertFalse(Updater.isNewer("0.4.4-beta.1", "0.4.4-beta.2"))
        assertFalse(Updater.isNewer("", "0.4.4"))
    }

    @Test
    fun remoteInstallPreconditionBlocksLocalOnlyAndMissingUpdate() {
        val validInfo = Updater.Info(
            latest = "0.5.5",
            current = "0.5.4",
            force = false,
            notes = "notes",
            url = "https://www.pgyer.com/app/installUpdate/test"
        )
        val localOnlyRes = Updater.validateRemoteInstallPrecondition(localOnly = true, newerInfo = validInfo)
        assertEquals(403, localOnlyRes?.first)
        assertTrue(localOnlyRes!!.second.contains("纯本地版本"))

        val noUpdateRes = Updater.validateRemoteInstallPrecondition(localOnly = false, newerInfo = null)
        assertEquals(400, noUpdateRes?.first)
        assertTrue(noUpdateRes!!.second.contains("没有可安装的新版本"))

        val blankUrlRes = Updater.validateRemoteInstallPrecondition(localOnly = false, newerInfo = validInfo.copy(url = ""))
        assertEquals(400, blankUrlRes?.first)

        assertNull(Updater.validateRemoteInstallPrecondition(localOnly = false, newerInfo = validInfo))
    }

    @Test
    fun remoteInstallStateMachinePreventsReentryAndSerializesStatusFields() {
        val t0 = 1_000_000L
        assertEquals("idle", Updater.currentInstallSnapshot(t0).state)
        assertTrue(Updater.tryBeginInstall("0.5.5", nowMs = t0))
        assertEquals("downloading", Updater.currentInstallSnapshot(t0).state)
        assertEquals("0.5.5", Updater.currentInstallSnapshot(t0).version)

        // Concurrent re-entry while downloading must be rejected
        assertFalse(Updater.tryBeginInstall("0.5.5", nowMs = t0 + 1_000L))

        Updater.updateInstallState("installing", 150, "请在设备上确认安装", "0.5.5", nowMs = t0 + 5_000L)
        val json = Updater.appendInstallStatus(JSONObject(), Updater.currentInstallSnapshot(t0 + 6_000L))
        assertEquals("installing", json.getString("installState"))
        assertEquals(100, json.getInt("installProgress"))
        assertEquals("请在设备上确认安装", json.getString("installMessage"))
        assertEquals("0.5.5", json.getString("installVersion"))

        // Concurrent re-entry while installing within cooldown (15s) must be rejected
        assertFalse(Updater.tryBeginInstall("0.5.5", nowMs = t0 + 5_000L + Updater.INSTALLING_RETRY_COOLDOWN_MS - 1L))

        // After cooldown elapses (e.g. user canceled installer or Android 10+ silently blocked background start), retry is allowed
        assertTrue(Updater.tryBeginInstall("0.5.5", nowMs = t0 + 5_000L + Updater.INSTALLING_RETRY_COOLDOWN_MS))
        assertEquals("downloading", Updater.currentInstallSnapshot(t0 + 5_000L + Updater.INSTALLING_RETRY_COOLDOWN_MS).state)

        // After error, retry is allowed immediately
        Updater.updateInstallState("error", 0, "网络中断", "0.5.5", nowMs = t0 + 30_000L)
        assertTrue(Updater.tryBeginInstall("0.5.5", nowMs = t0 + 30_001L))
        assertEquals("downloading", Updater.currentInstallSnapshot(t0 + 30_001L).state)
    }

    @Test
    fun installingStateAutoConvergesToIdleAfterTtl() {
        val t0 = 2_000_000L
        Updater.updateInstallState("installing", 100, "请在设备屏幕上确认安装", "0.5.5", nowMs = t0)
        // Before TTL (60s): remains installing
        assertEquals("installing", Updater.currentInstallSnapshot(t0 + Updater.INSTALLING_TTL_MS - 1L).state)
        // At/after TTL: automatically converges back to idle so Web UI stops polling and unlocks button
        val expired = Updater.currentInstallSnapshot(t0 + Updater.INSTALLING_TTL_MS)
        assertEquals("idle", expired.state)
        assertEquals(0, expired.progress)
        assertEquals("", expired.message)
        assertTrue(Updater.tryBeginInstall("0.5.5", nowMs = t0 + Updater.INSTALLING_TTL_MS + 1L))
    }
}
