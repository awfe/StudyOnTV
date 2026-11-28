package com.studyontv.parent

/**
 * 常见国内电视 / 盒子 / 投影仪开启无线 ADB（TCP 5555）的三步速查指南。
 */
object BrandGuides {
    data class Guide(
        val id: String,
        val brand: String,
        val steps: List<String>,
        val tip: String
    )

    val ALL: List<Guide> = listOf(
        Guide(
            id = "xiaomi",
            brand = "小米 / 红米",
            steps = listOf(
                "电视设置 → 关于 → 连续按 7 次「产品型号 / 版本号」开启开发者模式",
                "返回设置 → 账号与安全 → 开启「安装未知来源应用」与「ADB 调试」",
                "回到本页点「扫描无线 ADB」，连上后在电视弹窗选「一律允许」"
            ),
            tip = "部分澎湃 OS 小米电视会自动监听 5555 端口；若扫描不到，可在电视网络详情里查看 IP 手动输入。"
        ),
        Guide(
            id = "tcl",
            brand = "TCL / 雷鸟",
            steps = listOf(
                "电视设置 → 系统 → 关于 → 连续点击「版本号 / 系统版本」开启开发者选项",
                "进入「开发者选项」→ 开启「USB 调试 / 网络 ADB 调试」",
                "回到本页点「扫描无线 ADB」，按电视提示确认授权"
            ),
            tip = "部分雷鸟机型需同时在「安全」里放行未知来源应用；若被拦截，可先 U 盘装 APK 再用本页「仅一键修复权限」。"
        ),
        Guide(
            id = "hisense",
            brand = "海信 / Vidda",
            steps = listOf(
                "进入设置 → 系统设置 → 关于本机 → 连续按确认键开启「开发者选项」",
                "在开发者选项（或「商场模式 / 安全」）中开启「ADB 调试 / 网络调试」",
                "回到本页点「扫描无线 ADB」并输入或点选电视 IP"
            ),
            tip = "个别新款海信/Vidda 隐藏了网络 ADB，若无法开启可 U 盘安装后再通过本 App 首页直连管理后台。"
        ),
        Guide(
            id = "skyworth",
            brand = "创维 / 酷开",
            steps = listOf(
                "电视设置 → 系统信息 → 连续按 5~7 次确认键开启「开发者选项」",
                "开启「ADB 调试」（部分酷开机型需在遥控器输入工厂/调试快捷码）",
                "回到本页点「扫描无线 ADB」完成装机与权限配置"
            ),
            tip = "安装完毕后建议关闭「ADB 调试」，日常管理走 Wi-Fi 8099 端口，无需保持 ADB 开启。"
        ),
        Guide(
            id = "sony",
            brand = "索尼 Sony",
            steps = listOf(
                "设置 → 系统 → 关于 → 连续点击 7 次「Android TV 操作系统版本」",
                "返回系统 → 开发者选项 → 开启「USB 调试」（索尼系统开启后通常同步监听局域网 5555）",
                "回到本页点「扫描无线 ADB」，在电视屏幕按遥控器允许调试"
            ),
            tip = "索尼原生 Android TV 兼容性极佳，悬浮窗、使用情况和无障碍权限均可一步到位。"
        ),
        Guide(
            id = "box",
            brand = "盒子 / 投影仪",
            steps = listOf(
                "当贝 / 极米 / 坚果 / 芝杜等：设置 → 关于 → 连续点击「版本号」开启开发者选项",
                "进入开发者选项 → 开启「ADB 调试 / 网络调试」",
                "回到本页点「扫描无线 ADB」即可自动发现并一键配好权限"
            ),
            tip = "安卓盒子与投影仪普遍直接开放 5555 端口，成功率最高。"
        ),
        Guide(
            id = "phone",
            brand = "手机 / 平板",
            steps = listOf(
                "本功能同样支持给孩子的安卓平板/旧手机一键修复无障碍与悬浮窗权限",
                "Android 11+ 可在「开发者选项 → 无线调试」查看当前 IP 与端口（非 5555 时请按 IP:端口 格式手动输入）",
                "或在电脑曾执行过 `adb tcpip 5555` 后直接输入设备局域网 IP"
            ),
            tip = "注意：Android 11+ 全新配对需 6 位配对码；如设备已经允许 5555 端口或已配对，可直接一键配置权限。"
        )
    )
}
