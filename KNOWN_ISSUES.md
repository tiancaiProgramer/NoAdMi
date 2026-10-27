# 已知问题：智能跳过执行器

状态：**0.12.5-preview / versionCode 57 尚未修复**。更新日期：2026-10-27。

用户实测淘宝、闲鱼打开 App 后没有跳过，执行记录为空。本版发布用于同步开发与验证；Root / Shizuku 能开启无障碍、系统显示服务已连接，不能替代这两个 App 的实际跳过验证。

## 已确认的实现差异

| 编号 | 问题与证据 | 当前结论 |
|---|---|---|
| SKIP-01 | id667 v619 的淘宝专用开屏规则 `matchTime=10000`、`forcedTime=0`。当前 `SkipSession.needsPoll()` 在首次未匹配后返回 false。GKD 在应用切换后会继续安排启动补检。 | 离线复现通过；若广告晚于首次检查出现且没有后续事件，就可能漏过。 |
| SKIP-02 | `SkipAccessibilityService.inspect()` 先完整读取节点树，超过 300ms / 2500 节点 / 80 层，或任一子节点为空时抛异常。补检只在 try 内末尾安排，catch 结束后不再安排。 | 中断路径已由源码确认；需要手机日志确认用户那次是否触发。闲鱼当前默认使用的两个全局开屏规则都有 `forcedTime=10000`，仍会受这个中断路径影响。 |
| SKIP-03 | `SkipScope.activity()` 在 Activity 未知且规则有页面限制时直接拒绝；对照 GKD 的 AppRule，未知 Activity 时仍允许进入匹配，并另有特权前台页面回退。 | 语义差异已确认。淘宝专用开屏不带 Activity 限制，不能把其失败直接归因于这一项。 |
| SKIP-04 | 当前执行记录只保存成功动作与手势取消。总开关关闭、无候选规则、读取超时、未匹配、节点校验失败、点击返回 false 均未显示原因。 | 空记录不足以判断失败发生在哪一层。诊断信息需节流、仅保存在本机，不记录用户屏幕文字。 |

当前复用的是 GKD 选择器、订阅解析与参数核心，运行调度、节点读取和动作适配仍由 NoAdMi 实现；不是完整 GKD 引擎。此前简单测试页的成功不能视为淘宝、闲鱼通过。

## 离线证据

- 对照 GKD 提交：`fe6736fb083ba90d7bace124e30b61ff6e5701f2`（本模块引入版本）。
- 公开参考订阅：id667，version 619，988 个应用。
- 淘宝专用开屏选择器可以在合成节点树中选中关闭节点：说明该例选择器语法可解析，不代表真实 App 点击已验证。
- 默认规则条件下：淘宝有 1 条可用专用规则；闲鱼开屏依赖 2 条全局规则。
- 完整输出：[reference-result.txt](diagnostics/gkd-compat/reference-result.txt)。复现程序：[diagnostics/gkd-compat](diagnostics/gkd-compat/README.md)。

来源：[参考订阅](https://github.com/Lin-arm/GKD_subscription)、[淘宝规则](https://github.com/Lin-arm/GKD_subscription/blob/main/src/apps/com.taobao.taobao.ts)、[闲鱼规则](https://github.com/Lin-arm/GKD_subscription/blob/main/src/apps/com.taobao.idlefish.ts)、[GKD 调度](https://github.com/gkd-kit/gkd/blob/fe6736fb083ba90d7bace124e30b61ff6e5701f2/gkd-app/src/androidMain/kotlin/li/gkd/app/a11y/A11yRuleEngine.kt)。不随源码或 APK 分发该第三方订阅。

## 另一台电脑的排查顺序

1. 先只读核对当前的“启用”、订阅启用、应用 / 规则组开关及真实服务连接。最后一次历史快照的总开关为 false，但不能推断用户实际测试时仍关闭；必须读当前值。不要自动打开所有功能规则。
2. 加入节流的诊断状态：收到事件、前台包名 / Activity 是否可用、候选规则数、读取终止原因、匹配结果、动作返回值；避免保存账号、屏幕文字和完整节点树。
3. 修复启动补检及节点读取失败后的重试。保留窗口切换、权限界面排除和执行前校验，按预算分轮处理，避免长时间阻塞或误触。
4. 对齐 Activity 回退、规则参数和全局 / 应用规则控制语义；不要通过去掉应用或页面边界来追求命中。
5. 在真实淘宝、闲鱼验证冷启动 / 热启动、有广告 / 无广告、开关关闭不执行及执行记录；再回归原有 Hook、订阅编辑和 Root / Shizuku 控制。

## 服务测试注意

Android 16 对以 NoAdMi 自身为目标的 instrumentation 会停止模块进程，可能留下已启用但断开的状态。服务实测应使用独立夹具进程或正常应用进程，结束后确认真实 `onServiceConnected` 与系统 Bound，不能只看设置值。Root 恢复和 Shizuku 控制的已有证据见 [SMART_SKIP_VERIFICATION.md](SMART_SKIP_VERIFICATION.md)。
