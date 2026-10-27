# 智能跳过差异离线复现

这是只读诊断程序，不修改手机设置，也不构建或安装新 APK。它使用 NoAdMi 当前编译出的 Kotlin JAR 和公开参考订阅，复现淘宝首次未匹配后不继续轮询的行为，并核对 Activity 未知时的过滤与真实选择器的解析。

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
