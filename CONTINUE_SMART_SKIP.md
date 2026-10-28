# 继续开发 / 验证

## 0.12.8 当前发布状态（2026-10-28）

当前 0.12.8-preview / code79（统一链接与 JSON5 入口间距，查看与本地编辑同样留出间距；本轮40项现有弹窗回归与两种截图核对通过），下一次安装修改从 code79 递增。基于本地 0.12.7 / code76，保留 GitHub main 9be017c 的执行器。本目录随 v0.12.8-preview 发布；下方版本章节是历史记录。

新增 DocumentPages / SubscriptionDocumentDialog：后台对文档做有界、无损分段，ListView 复用可视文本行；无换行文档也线性处理，避免完整 EditText 布局。查看入口也收进编辑订阅弹窗；关闭查看返回父弹窗。编辑弹窗顶部复制 / 更新、底部左侧删除需确认、右侧取消 / 保存，本地入口按适用性隐藏。SkipSources.replaceFromUrl 校验并原子提交新来源和更新文档，失败保留原值。

新增 SubscriptionEditorSmoke，并调整原界面测试到精简后的菜单。测试只使用关闭的临时订阅，不对真实订阅发起更新。实机结论见 SMART_SKIP_VERIFICATION.md 最新段落。

最终 40 + 81 + 89 共 210 项实机检查通过，3 MB 单行查看操作主线程 36ms。发现旧回归导入偶发等待是本地操作共用后台自动下载队列，已拆成本地 edits、手动 downloads 与后台 Job 队列；以占用后台队列的对照验证保存 / 删除不被阻塞。最终原订阅文档和全部用户选项一致，临时订阅清理，自身服务正常 Bound，APK 回读相同。


## 0.12.7 当前开发状态（2026-10-28，尚未发布）

基线是 GitHub main `9be017cd263fb9fc2c41bca5f3072668daef51f4` / code72。当前为 **0.12.7-preview / code76**，原签名。下方旧版章节是历史记录，下一次修改并安装 APK 应从 code76 递增。

- 应用列表使用后台包信息读取、后台搜索排序及原生 ListView 复用，开关保存独立于下载队列。三种排序、系统 / 用户 / 未安装分组及只看白名单已加入；展示筛选不改规则。
- 实机未授权应用列表权限时，批量查询仅返回调用方自身，直接按包名查询正常；用户授权后能批量读取 419 个应用。保留直接查询回退，返回列表时复用 30 秒安装元数据，重新读取实际历史时间。
- 最近使用来自启用智能跳过后的确认前台切换，仅保存包名和时间；最近触发来自本订阅最多 200 条成功动作，新日志以 sourceId 区分订阅。没有记录时按名称排列。
- SkipSource.categories 向后兼容，订阅更新保留。优先级：显式规则组选择 > 显式分类选择 > 组默认 > 分类默认 > true；上层订阅及应用开关仍生效，分类不控制全局规则。
- 链接支持复制、编辑，只接受 HTTPS；编辑保留规则和开关，下次更新仍拒绝不同 ID 和旧版本。
- 新增 SkipManagementTest、AppRuleListModelTest、AppRulesManagementSmoke。测试只添加关闭的临时订阅并清理，不能作为淘宝 / 闲鱼实际广告通过的结论。

保留 0.12.6 的执行器，服务仅新增确认前台切换后的时间记录。验证结果见 SMART_SKIP_VERIFICATION.md 最新段落。保留原签名，不修改第三方订阅。此开发版尚未推送 GitHub。

最终实机新增 80 项与原界面独立复测 87 项通过，正常进程服务 Bound，原手动开关保留。测试期间官方订阅由 619 正常更新至 620，保留更新结果；后续 UI 回归避免临时打开真实订阅自动更新。公开摘要见 diagnostics/subscription-management/verification.json；原始画面与偏好只保存在仓库外。

## 0.12.6 当前工作状态（2026-10-28）

本目录由 v0.12.5-preview 基线演进，按 main / v0.12.6-preview 同步发布，版本为 0.12.6-preview / 72。运行适配参考实机 GKD 1.12.1 / 92 对应提交 `5a00f844989471458e4cf4601acb3f4710438f9a`；选择器 / 订阅核心仍独立固定在原引入提交，见 third_party/gkd-runtime/UPSTREAM.md。

已调整事件过滤、事件节点优先、按需根节点、目标先于排除匹配、3 秒启动 / 动作补检、未知 Activity 语义、服务配置及保活视图，并加入有界原生读取和本机诊断。构建与离线回归通过，原签名覆盖安装已回读哈希。

**后续已通过本机拍机堂正常冷启动三次提前跳过验证。** 用户确认 GKD 有效后，读取其真实外部数据目录（不能只看内部 files/private-store）、动作数据库和日志，确认 GKD 使用同一条全局规则的原生 `clickNode`。统一 MAIN/LAUNCHER 启动、两者单独启用，结合录像、Activity 转换和动作记录重测；NoAdMi 三次均提前退出，关闭总开关与无广告热启动均无动作。具体时序见 diagnostics/gkd-compat/device-ab-summary.json。

上一阶段只凭直接组件启动录像中红色画面停留就认定未跳过，证据不足，结论已更正。保留原始材料，不认定启动 Intent 为唯一原因，也不把系统转换时序当作像素展示时长。仍可观察到画面停留与读取性能波动，不能声称延迟完全等同 GKD。不要新增拍机堂硬编码规则、模型 / 版本白名单或改写第三方订阅。下次修改 APK 仍从 code72 递增；本次后续复测没有再改 APK。

测试已恢复原总开关 true、方式 Shizuku、原两条订阅状态和自身无障碍服务；58 项框架设置、36 项界面偏好、7 项作用域一致。淘宝、闲鱼仍需有广告场景验收。第二台手机两轮各测 11 个 App，第二轮确认拍机堂、京东、哔哩哔哩跳过；其余 8 个本轮无可验证控件。第二台手机 GKD 全程关闭，4 个原设置/订阅文件、41 项框架配置和 2 项作用域保留。

后续若修改 APK，应从 versionCode 72 继续递增，继续使用原签名。下文为 0.12.5 历史基线；当前交接以 v0.12.6-preview 源码与验证记录为准。

## 0.12.5 历史交接


GitHub 的 main / v0.12.5-preview 与本目录为同步交接基线；项目基于原 0.11.7 提交 48d9590bb65c6b7219a955b7405c530328f9306c 演进。克隆后的仓库根目录即完整源码，不要用旧 NoAdMi-modern（0.11.2）覆盖。

## 0.12.5 历史优先事项

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

使用仓库 README.md / SMART_SKIP.md 的通用路径参数准备 JDK 17、Android SDK 35 / build-tools 36、libxposed 102、Kotlin 2.4.21 与 D8 9.5.28。diagnostics/gkd-compat/run.py 接受本机工具路径，不依赖原电脑绝对目录。后续代码修改应把 versionCode 从当前开发版本 72 递增；预览 APK 必须继续用原签名，密钥和密码通过用户已有私密备份单独传递，不能生成替代密钥或提交到仓库。
