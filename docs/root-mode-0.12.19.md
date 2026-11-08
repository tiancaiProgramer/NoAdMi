# Root 模式实测：0.12.19-preview / code104

2026-11-08，在 MIX Fold 3（2308CPXD0C / Android 16）验证当前原签名 code104，Root专项没有修改生产代码或重装模块。发布包沿用同一已测APK。

默认 Root 路径的 10 组隔离检查全部通过：坐标点击、长按、连续滑动、短距离停留、总开关关闭、全局白名单、来源关闭、切页取消、组级范围阻断与放行。使用 Root 控制自身无障碍，输入选择为默认兼容模式0；未选择 Shizuku 增强，也没有运行 NoAdMi 特权工作进程。实际触摸由测试页面 MotionEvent 核对，点击约97ms、长按504ms、连续滑动1228ms、停留1015ms；均为从DOWN至UP的触摸时长，不是广告延迟。夹具使用350ms页面稳定等待，没有改用户订阅规则。正式设置界面另外验证了 Root 开启、关闭、再开启，均回报系统设置成功且实际连接符合预期。

拍机堂（com.aihuishou.opt）真实开屏三轮均在“3 跳过”倒数结束前进入主页，NoAdMi 每轮仅一次 `clickNode` 动作。关闭总开关的对照轮完整显示3→2→1，再自然进入主页；没有新增 NoAdMi 动作。四轮均有独立录像，GKD保持关闭，未增加拍机堂专用规则，未点击推广详情或进行账户/购买操作。这证明当前设备上的 Root 模式实际跳过可用，不代表所有应用或所有随机广告均通过，也不是与新版GKD的速度对比。

本机 GKD 仍为1.12.1 / 92，没有为本次测试升级。参考的是官方 [GKD 1.13.0 发布记录](https://github.com/gkd-kit/gkd/releases/tag/v1.13.0) 和固定提交 `0ee5d146a382d1066cddfdb6d0ace9d9563a8464` 的源码；未把源码对照当成同机新版GKD实测。

| 能力 | 当前 NoAdMi Root | GKD 1.13 参考实现 |
| --- | --- | --- |
| Root 启动用途 | 修改自身无障碍组件状态，保留其他服务 | 独立特权服务，可通过Root/ADB/手动启动，授权管理由priv-kit提供 |
| 坐标点击/长按/滑动 | 使用Android无障碍手势；拍机堂本轮命中节点点击 | 特权输入权限可用时优先调用特权输入，未尝试输入时可回退无障碍；已尝试失败不盲目重放 |
| 前台任务增强 | 当前仅Shizuku工作进程提供；Root依靠原无障碍识别 | 特权上下文提供任务读取与监听 |
| 连接恢复 | Root控制为短期操作，无同类常驻Root输入服务；Shizuku恢复保留 | 独立特权上下文恢复，失败有冷却控制 |

对应官方源码为 `gkd-app/src/androidMain/kotlin/li/gkd/app/priv/{PrivilegeContext,PrivilegeRecovery,PrivilegeUiIntegration}.kt` 及 `matching/action/ActionPerformer.kt`；1.13依赖priv-kit 0.17.11。NoAdMi尚未接入该独立Root服务、特权输入和Root前台监听；当前可用不等于已经完整对齐新版GKD。

结束时恢复本轮开始的13个配置/订阅/执行记录文件，新进程逐个核对哈希一致；原Shizuku选项、总开关和菜单展开状态恢复，手机备份缓存及临时工具清除。NoAdMi和原有其他无障碍服务实际绑定正常，原服务列表一致。原Shizuku增强连接的待确认项没有因Root测试被标记为通过；未重启全局Shizuku。

仍需测试锁屏/熄屏、平台中断和释放拒绝后的恢复，更多真实广告和长期运行；后续若补齐GKD新Root服务，需要单独构建并验证其权限、实际输入和断线恢复。

[机器可读摘要](../diagnostics/gkd-compat/root-validation-0.12.19.json)。本地原始记录位于 `testing/gkd-root-20261108/device-check`，包含初始备份、10组触摸结果、4轮录像和恢复审计；私有配置不提交。
