# 智能跳过差异离线复现

这是只读诊断程序，不修改手机设置，也不构建或安装新 APK。它使用 NoAdMi 当前编译出的 Kotlin JAR 和公开参考订阅，核对启动补检、未知 Activity 语义和真实选择器解析。旧 reference-result.txt 保留 0.12.5 的差异复现；current-result.txt 为 0.12.6 的修正后输出。两者都不是目标 App 的实机效果证明。

参考结果使用 id667 v619，参考文件 SHA-256：`f957096ba0141be1d64482c627ca6dddea8d6d4e6ef58ccf5e1fc8b519a2c472`。参考订阅未包含在仓库中，需自行下载可信原文并核对 ID、版本与校验值；后续订阅更新后结果可能不同。

先按项目构建说明生成 `build/smart-skip.jar`，再执行：

```sh
python3 diagnostics/gkd-compat/run.py \
  --java-home /absolute/path/jdk17 \
  --kotlin-compiler-dir /absolute/path/kotlin-toolchain \
  --subscription /absolute/path/id667-v619.json5
```

可用 `--module-jar` 指定其他构建路径。报告写入 `build/gkd-compat/result.txt`。

本程序的匹配节点是合成节点；通过不等于真实淘宝 / 闲鱼已经可以跳过。真实运行问题和下一步见 [KNOWN_ISSUES.md](../../KNOWN_ISSUES.md)。

## 当前设备的正常启动对照

[device-ab-summary.json](device-ab-summary.json) 是 2026-10-28 的去除私人数据摘要：GKD 单独启用、NoAdMi 单独启用三次、两者执行关闭，以及无广告热启动。使用 MAIN / LAUNCHER / 0x10200000 从桌面启动，并同时核对动作、Activity 转换和录像。`displayedToHomeRequestSeconds` 指系统记录启动页首帧到发出首页启动请求，不等于广告像素展示时长。原始数据库、订阅和录像不包含在仓库中。

前一阶段直接组件启动的录像及颜色持续时长不足以单独认定动作失败，结论已更正；也没有将 Intent 差异认定为唯一根因。正常冷启动提前退出本机通过 3/3，画面停留仍有波动。不要据此推断所有设备、版本或其他 App 已通过。
