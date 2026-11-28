package com.studyontv.parent

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 家长端首页：
 * 1. 自动扫描并记住家里运行「题了个题」的电视与平板（支持按 deviceId 自动跟进 IP 变化）；
 * 2. 一键打开任意设备的 `/admin/` 家长管理后台；
 * 3. 一键进入「无线 ADB 一键装电视 / 修权限」向导；
 * 4. 支持手动输入局域网 IP 探测与复制电脑管理网址。
 */
class ParentHomeActivity : Activity() {
    private val io = Executors.newSingleThreadExecutor()
    private val scanning = AtomicBoolean(false)

    private lateinit var p: ParentUi.Palette
    private lateinit var subtitleView: TextView
    private lateinit var scanStatusView: TextView
    private lateinit var scanBtn: TextView
    private lateinit var deviceListContainer: LinearLayout

    private var devices: List<DeviceStore.SavedDevice> = emptyList()
    private var onlineIds: Set<String> = emptySet()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        p = ParentUi.palette(this)
        ParentUi.applySystemBars(this, p)
        devices = DeviceStore.load(this)
        setContentView(buildContentView())
        renderDevices()
        triggerLanScan(silent = false)
    }

    override fun onResume() {
        super.onResume()
        devices = DeviceStore.load(this)
        updateNetworkSubtitle()
        renderDevices()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun buildContentView(): View {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(p.bg)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = ParentUi.dp(this@ParentHomeActivity, 16)
            setPadding(pad, ParentUi.dp(this@ParentHomeActivity, 18), pad, ParentUi.dp(this@ParentHomeActivity, 28))
        }
        scroll.addView(root)

        // 顶部标题栏
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleCol = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        titleCol.addView(TextView(this).apply {
            text = "题了个题 · 家长端"
            textSize = 22f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.textPrimary)
        })
        subtitleView = TextView(this).apply {
            textSize = 12f
            setTextColor(p.textMuted)
            setPadding(0, ParentUi.dp(this@ParentHomeActivity, 3), 0, 0)
        }
        titleCol.addView(subtitleView)
        header.addView(titleCol)
        header.addView(ParentUi.badge(this, "v${BuildConfig.VERSION_NAME}", p.accentSoft, p.accentText))
        root.addView(header)
        updateNetworkSubtitle()

        root.addView(ParentUi.spacer(this, 16))

        // 无线 ADB 一键装机卡片（单色克制容器，仅主按钮使用强调蓝）
        val adbCard = ParentUi.card(this, p, paddingDp = 16)
        adbCard.addView(TextView(this).apply {
            text = "无线 ADB 一键装电视 / 修权限"
            textSize = 15.5f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.textPrimary)
        })
        adbCard.addView(TextView(this).apply {
            text = "免 U 盘、免电脑：推送安装包并自动配置悬浮窗、使用情况、无障碍与省电白名单。"
            textSize = 13f
            setTextColor(p.textMuted)
            setPadding(0, ParentUi.dp(this@ParentHomeActivity, 6), 0, ParentUi.dp(this@ParentHomeActivity, 12))
        })
        adbCard.addView(ParentUi.primaryButton(this, p, "打开无线 ADB 向导 →") {
            openAdbWizard(prefillIp = null, fixOnly = false)
        })
        root.addView(adbCard)

        root.addView(ParentUi.spacer(this, 14))

        // 快捷操作栏：扫描局域网 + 手动添加 IP
        val actionsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        scanBtn = ParentUi.secondaryButton(this, p, "扫描局域网设备") {
            triggerLanScan(silent = false)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        actionsRow.addView(scanBtn)
        actionsRow.addView(ParentUi.spacer(this, heightDp = 1, widthDp = 10))
        actionsRow.addView(ParentUi.secondaryButton(this, p, "+ 手动添加 IP") {
            showManualAddDialog()
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        root.addView(actionsRow)

        // 设备列表标题 & 扫描状态
        val listHeader = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(ParentUi.dp(this@ParentHomeActivity, 2), ParentUi.dp(this@ParentHomeActivity, 18), 0, ParentUi.dp(this@ParentHomeActivity, 8))
        }
        listHeader.addView(TextView(this).apply {
            text = "家里的学习设备"
            textSize = 14f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.textPrimary)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        scanStatusView = TextView(this).apply {
            textSize = 12f
            setTextColor(p.textMuted)
        }
        listHeader.addView(scanStatusView)
        root.addView(listHeader)

        deviceListContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
        }
        root.addView(deviceListContainer)

        // 底部轻提示
        root.addView(ParentUi.spacer(this, 14))
        root.addView(TextView(this).apply {
            text = "家长端仅申请基础局域网访问权限，零后台常驻、不读相册定位；所有数据均直接与家里电视本地通信。"
            textSize = 11.5f
            setTextColor(p.textMuted)
            setPadding(ParentUi.dp(this@ParentHomeActivity, 4), 0, ParentUi.dp(this@ParentHomeActivity, 4), 0)
        })

        return scroll
    }

    private fun updateNetworkSubtitle() {
        val ips = LanNet.localPrivateIpv4Addresses()
        subtitleView.text = if (ips.isNotEmpty()) {
            "本机局域网 IP：${ips.first()} · 纯局域网直连"
        } else {
            "未检测到 Wi-Fi 局域网，请先连上与电视相同的 Wi-Fi"
        }
    }

    private fun triggerLanScan(silent: Boolean) {
        if (!scanning.compareAndSet(false, true)) return
        runOnUiThread {
            scanBtn.text = "扫描中…"
            scanBtn.isEnabled = false
            scanStatusView.text = "正在扫描家庭 Wi-Fi…"
        }
        io.execute {
            try {
                // 1. UDP 广播发现
                val udpFound = LanNet.discoverStudyOnTvDevices(timeoutMs = 1200L)
                val foundMap = LinkedHashMap<String, LanNet.DiscoveredDevice>()
                for (d in udpFound) foundMap[d.id] = d

                // 2. 对已保存但未响应 UDP 的设备做一次 HTTP /api/hello 探测（兼容跨 AP 隔离 UDP 广播）
                val savedSnapshot = DeviceStore.load(this)
                for (saved in savedSnapshot) {
                    if (saved.id in foundMap) continue
                    val probed = LanNet.probeHello(saved.ip, saved.port, timeoutMs = 1200)
                    if (probed != null) foundMap[probed.id] = probed
                }

                val merged = if (foundMap.isNotEmpty()) {
                    DeviceStore.recordDiscovered(this, foundMap.values.toList())
                } else {
                    savedSnapshot
                }
                val liveIds = foundMap.keys.toSet()

                runOnUiThread {
                    devices = merged
                    onlineIds = liveIds
                    scanBtn.text = "扫描局域网设备"
                    scanBtn.isEnabled = true
                    scanStatusView.text = when {
                        liveIds.isNotEmpty() -> "在线 ${liveIds.size} 台"
                        merged.isNotEmpty() -> "未发现新在线设备"
                        else -> "暂未发现设备"
                    }
                    updateNetworkSubtitle()
                    renderDevices()
                    if (!silent && liveIds.isEmpty() && merged.isEmpty()) {
                        Toast.makeText(this, "未发现运行中的电视，可尝试「手动添加 IP」或「无线 ADB 一键装电视」", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (_: Exception) {
                runOnUiThread {
                    scanBtn.text = "扫描局域网设备"
                    scanBtn.isEnabled = true
                    scanStatusView.text = "扫描中断"
                }
            } finally {
                scanning.set(false)
            }
        }
    }

    private fun renderDevices() {
        deviceListContainer.removeAllViews()
        if (devices.isEmpty()) {
            val emptyCard = ParentUi.card(this, p, paddingDp = 18)
            emptyCard.addView(TextView(this).apply {
                text = "还没有发现已安装的设备"
                textSize = 15f
                setTypeface(typeface, Typeface.BOLD)
                setTextColor(p.textPrimary)
            })
            emptyCard.addView(TextView(this).apply {
                text = "• 如果电视已安装「题了个题」，请先在电视上打开它，并确认手机与电视连在同一个 Wi-Fi，再点「扫描局域网设备」。\n" +
                    "• 如果电视还没安装，可直接点击上方的「无线 ADB 一键装电视 / 修权限」，手机自动把安装包推到电视并配好全部权限。"
                textSize = 13f
                setTextColor(p.textSecondary)
                setLineSpacing(0f, 1.25f)
                setPadding(0, ParentUi.dp(this@ParentHomeActivity, 8), 0, 0)
            })
            deviceListContainer.addView(emptyCard)
            return
        }

        for ((idx, dev) in devices.withIndex()) {
            if (idx > 0) deviceListContainer.addView(ParentUi.spacer(this, 10))
            deviceListContainer.addView(buildDeviceCard(dev))
        }
    }

    private fun buildDeviceCard(dev: DeviceStore.SavedDevice): View {
        val isOnline = dev.id in onlineIds
        val card = ParentUi.card(this, p, paddingDp = 15)

        // 第一行：名称 + 状态徽章 + 菜单
        val topRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val titleWrap = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val nameLine = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val prefix = if (dev.pinned) "★ " else ""
        val typeIcon = if (dev.isTv) "📺 " else "📱 "
        nameLine.addView(TextView(this).apply {
            text = "$prefix$typeIcon${dev.name}"
            textSize = 16f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.textPrimary)
        })
        nameLine.addView(ParentUi.spacer(this, 1, 8))
        nameLine.addView(
            ParentUi.badge(
                this,
                if (isOnline) "在线" else "上次记录",
                if (isOnline) p.greenSoft else p.surfaceAlt,
                if (isOnline) p.green else p.textMuted
            )
        )
        titleWrap.addView(nameLine)

        val metaParts = ArrayList<String>()
        metaParts.add(dev.host)
        if (dev.version.isNotBlank()) metaParts.add("v${dev.version}")
        if (dev.questions > 0) metaParts.add("题库 ${dev.questions} 题")
        titleWrap.addView(TextView(this).apply {
            text = metaParts.joinToString(" · ")
            textSize = 12.5f
            setTextColor(p.textMuted)
            setPadding(0, ParentUi.dp(this@ParentHomeActivity, 4), 0, 0)
        })
        topRow.addView(titleWrap)

        val moreBtn = TextView(this).apply {
            text = "⋯"
            textSize = 20f
            setTypeface(typeface, Typeface.BOLD)
            setTextColor(p.textMuted)
            val ph = ParentUi.dp(this@ParentHomeActivity, 10)
            val pv = ParentUi.dp(this@ParentHomeActivity, 4)
            setPadding(ph, pv, ph, pv)
            isClickable = true
            isFocusable = true
            setOnClickListener { v -> showDeviceMenu(v, dev) }
        }
        topRow.addView(moreBtn)
        card.addView(topRow)

        card.addView(ParentUi.spacer(this, 12))

        // 第二行：主操作「进入管理」+ 「ADB 修权限」+ 「复制电脑网址」
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        btnRow.addView(ParentUi.primaryButton(this, p, "进入管理后台") {
            openDeviceConsole(dev)
        }.apply {
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.3f)
        })
        btnRow.addView(ParentUi.spacer(this, 1, 8))
        btnRow.addView(ParentUi.secondaryButton(this, p, "修权限") {
            openAdbWizard(prefillIp = dev.ip, fixOnly = true)
        })
        btnRow.addView(ParentUi.spacer(this, 1, 8))
        btnRow.addView(ParentUi.secondaryButton(this, p, "复制网址") {
            ParentUi.copyToClipboard(
                this,
                "StudyOnTV Admin URL",
                dev.adminUrl,
                "已复制 ${dev.adminUrl}，可在电脑浏览器直接打开"
            )
        })
        card.addView(btnRow)

        return card
    }

    private fun showDeviceMenu(anchor: View, dev: DeviceStore.SavedDevice) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 1, 0, if (dev.pinned) "取消置顶" else "置顶此设备")
        menu.menu.add(0, 2, 1, "立即探测连通性")
        menu.menu.add(0, 3, 2, "无线 ADB 重装/升级此设备")
        menu.menu.add(0, 4, 3, "从列表中移除")
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                1 -> {
                    devices = DeviceStore.togglePin(this, dev.id)
                    renderDevices()
                }
                2 -> probeSingleHost(dev.host, openOnSuccess = false)
                3 -> openAdbWizard(prefillIp = dev.ip, fixOnly = false)
                4 -> {
                    devices = DeviceStore.remove(this, dev.id)
                    renderDevices()
                }
            }
            true
        }
        menu.show()
    }

    private fun showManualAddDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = ParentUi.dp(this@ParentHomeActivity, 20)
            setPadding(pad, ParentUi.dp(this@ParentHomeActivity, 12), pad, 0)
        }
        container.addView(TextView(this).apply {
            text = "输入电视上「题了个题」显示的局域网地址（默认端口 8099，可只填 IP）："
            textSize = 13f
            setTextColor(p.textSecondary)
        })
        val input = EditText(this).apply {
            hint = "例如 192.168.1.88 或 192.168.1.88:8099"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            isSingleLine = true
        }
        container.addView(input)

        AlertDialog.Builder(this)
            .setTitle("手动添加家庭设备")
            .setView(container)
            .setNegativeButton("取消", null)
            .setPositiveButton("探测并添加") { _, _ ->
                val raw = input.text?.toString().orEmpty().trim()
                val parsed = LanNet.parsePrivateLanHost(raw, LanNet.DEFAULT_HTTP_PORT)
                if (parsed == null) {
                    Toast.makeText(this, "请输入有效的私有局域网 IP（如 192.168.x.x）", Toast.LENGTH_LONG).show()
                } else {
                    probeSingleHost(parsed.host, openOnSuccess = true)
                }
            }
            .show()
    }

    private fun probeSingleHost(rawHost: String, openOnSuccess: Boolean) {
        val parsed = LanNet.parsePrivateLanHost(rawHost, LanNet.DEFAULT_HTTP_PORT) ?: return
        Toast.makeText(this, "正在连接 ${parsed.host}…", Toast.LENGTH_SHORT).show()
        io.execute {
            val found = LanNet.probeHello(parsed.ip, parsed.port, timeoutMs = 3000)
            runOnUiThread {
                if (found != null) {
                    devices = DeviceStore.recordDiscovered(this, listOf(found))
                    onlineIds = onlineIds + found.id
                    renderDevices()
                    Toast.makeText(this, "已连上「${found.name}」", Toast.LENGTH_SHORT).show()
                    if (openOnSuccess) {
                        devices.firstOrNull { it.id == found.id }?.let { openDeviceConsole(it) }
                    }
                } else {
                    Toast.makeText(
                        this,
                        "连不上 ${parsed.host}：请确认电视已打开「题了个题」且与手机在同一 Wi-Fi",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    private fun openDeviceConsole(dev: DeviceStore.SavedDevice) {
        startActivity(
            Intent(this, DeviceConsoleActivity::class.java)
                .putExtra(DeviceConsoleActivity.EXTRA_DEVICE_ID, dev.id)
                .putExtra(DeviceConsoleActivity.EXTRA_DEVICE_NAME, dev.name)
                .putExtra(DeviceConsoleActivity.EXTRA_IP, dev.ip)
                .putExtra(DeviceConsoleActivity.EXTRA_PORT, dev.port)
        )
    }

    private fun openAdbWizard(prefillIp: String?, fixOnly: Boolean) {
        startActivity(
            Intent(this, AdbWizardActivity::class.java)
                .putExtra(AdbWizardActivity.EXTRA_PREFILL_IP, prefillIp.orEmpty())
                .putExtra(AdbWizardActivity.EXTRA_FIX_ONLY, fixOnly)
        )
    }
}
