# 继续开发 / 验证

GitHub 的 main / v0.12.5-preview 与本目录为同步交接基线；项目基于原 0.11.7 提交 48d9590bb65c6b7219a955b7405c530328f9306c 演进。克隆后的仓库根目录即完整源码，不要用旧 NoAdMi-modern（0.11.2）覆盖。

## 当前优先事项

用户实测淘宝（com.taobao.taobao）、闲鱼（com.taobao.idlefish）没有执行跳过且记录为空。0.12.5-preview 保留此问题，发布用于另一台电脑继续验证开发。优先阅读 [KNOWN_ISSUES.md](KNOWN_ISSUES.md)，运行 diagnostics/gkd-compat 复现；不能把服务已连接、选择器能解析或简单夹具通过当作真实 App 通过。当前尚未采集用户这次运行的设备日志，不能认定唯一根因。最后一次历史快照总开关为 false，需要读取新设备当前值，不能直接推断用户测试时未启用。

## 已完成的功能与边界

交接 APK 为 0.12.5-preview / 57，已在原测试机覆盖安装。智能跳过内直接显示“启用”和三个默认折叠分组：订阅管理、设置、执行记录。自动更新、移动网络更新和各订阅开关集中在上方；下方只有一个“详情与规则”入口，进入独立页面后按订阅分卡片展示信息，点击卡片原地向下展开 / 收起详情，不再跳转三级详情；添加、导入、立即更新三个操作保持最下方。独立 Activity 支持 source_list=true 打开卡片列表，也兼容 source_id 直接打开某条订阅。DialogUi 统一模块弹窗的配色、文字、输入框和按钮。设置仍只有无障碍服务与无障碍方式两行，移动网络更新仍显示 !wifi_only，沿用旧偏好与键。

0.12.5 新增服务恢复：AccessibilityControlService 在开启已选中自身组件时先移除自身，等待 AccessibilityBindingPolicy 确认系统已处理断开，再按最新列表加回自身，其他服务保留。AccessibilityAccess 使用独立 CONTROL 队列和请求序号，清理自身 Shizuku worker；服务连接 / 断开观察者实时刷新 UI。测试最后必须正常重连服务，不能只检查设置已启用。本轮 Root 单次恢复 Crashed 状态及真实连接回调已验证；Shizuku 曾因两个主服务并存而无法完成 UserService 回连，用户重启 Shizuku 后恢复，最终独立进程实测关闭 / 开启 / 重连通过，当前保留 Shizuku 方式与服务连接。

0.12.4 已修复订阅开关竞争：LatestToggleWrites 独立串行保存、合并过时请求，并由 SubscriptionToggles 在应用生命周期持有。SmartSkipPanel 按 ID 复用开关，观察 / 取消观察保存结果，不禁用待保存开关；同一文档的标题不重复解析。验证应保留现有订阅、总开关、服务启用状态和控制方式，不应强制套用历史测试的 Root / 服务开启状态。SubscriptionActions 仍由宿主 Activity 持有并接收 SAF 结果。


0.12.2 历史验证：Root 直接开关与真正的无障碍跳过已通过 ExternalSkipSmoke 的 53 项实机检查；ShizukuAccessSmoke 已通过授权识别、UserService、直接关闭 / 开启及真实服务重连检查。这些是历史记录，后续测试应读取并恢复用户当时的实际配置。

服务启动只改自身组件；Android 13+ 通过所选 Root / Shizuku 授权允许 NoAdMi 自身 ACCESS_RESTRICTED_SETTINGS。用户本轮明确要求直接开启与接入 Shizuku，此操作已纳入实际功能。不要改其他应用权限、数据库或整体保护。

原测试机 MIX Fold 3 的亮屏外屏为显示屏 0；换机或切屏需重新核对显示屏映射。Android 16 内部 instrumentation 会停止目标进程，不适合测试本进程无障碍服务。ExternalSkipSmoke 和 ShizukuAccessSmoke 的 instrumentation 目标为 io.github.miclean.skipfixture，使用 FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES，避免停止 NoAdMi。模块界面专项测试仍可使用原 UiSmoke / SmartSkipUiSmoke / UiAlignmentSmoke，完成后按需正常重连服务。

后续 APK 必须递增 versionCode 并使用原密钥。证书 SHA-256 为 9baa7091b7b84e4d76c662656a432b2f03d1a8f4322b3762792613ac11daf642。源码包没有密钥或密码，不能新建替代。本次按 GitHub v0.12.5-preview 预发布同步，README、发布说明与 KNOWN_ISSUES.md 均标注未修复问题。

构建见 SMART_SKIP.md，来源与许可见 THIRD_PARTY_NOTICES.md，详细证据见 SMART_SKIP_VERIFICATION.md 及外部验证 JSON。


## 跨电脑构建

使用仓库 README.md / SMART_SKIP.md 的通用路径参数准备 JDK 17、Android SDK 35 / build-tools 36、libxposed 102、Kotlin 2.4.21 与 D8 9.5.28。diagnostics/gkd-compat/run.py 接受本机工具路径，不依赖原电脑绝对目录。后续代码修改应把 versionCode 从 57 递增；预览 APK 必须继续用原签名，密钥和密码通过用户已有私密备份单独传递，不能生成替代密钥或提交到仓库。
