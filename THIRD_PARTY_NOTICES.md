# Third-party notices

This integrated NoAdMi version is distributed under GPL-3.0-only. The complete license is in `LICENSE` and the APK asset `licenses/GPL-3.0.txt`.

## GKD

- Project: https://github.com/gkd-kit/gkd
- Authors: GKD contributors.
- Commit: `fe6736fb083ba90d7bace124e30b61ff6e5701f2`.
- License: GPL-3.0-only. Upstream README additionally states that commercial use is prohibited.
- Original selector sources and license are retained in `third_party/gkd-selector/`.
- Original subscription model, parser, expression and execution parameter sources are retained in `third_party/gkd-core/`.
- NoAdMi's build removes JavaScript export annotations for JVM compilation, uses the upstream JVM regex implementation, and adapts resource-based error messages into ordinary exceptions. It does not change selector syntax or JSON5 parsing.
- NoAdMi supplies its own native UI, storage, Android accessibility adapter and scheduling. This is not the complete GKD application. GKD's Compose UI, HTTP service, inspection tools and snapshots are not included. NoAdMi adapts bounded task observation and touch execution through its own authorized Shizuku worker.

## Runtime dependencies

| Dependency | Version | License | Source |
| --- | --- | --- | --- |
| Kotlin standard library | 2.4.21 | Apache-2.0 | https://github.com/JetBrains/kotlin |
| kotlinx.serialization core / JSON | 1.11.0 | Apache-2.0 | https://github.com/Kotlin/kotlinx.serialization |
| kotlin-json5 | 0.8.0 | Apache-2.0 | https://github.com/lisonge/kotlin-json5 |
| exp4j | 0.4.8 | Apache-2.0 | https://github.com/fasseg/exp4j |
| libxposed service / interface | 102.0.0 | Apache-2.0 | https://github.com/libxposed |

Dependency versions, original Maven URLs and SHA-256 checksums are recorded in `third_party/dependencies/manifest.json`. The Apache license is included as `src/main/assets/licenses/Apache-2.0.txt`. Build-only Kotlin and R8 tools are pinned separately in `tools/smart-skip-toolchain.json`.

Subscription rules are separate works with their own licenses. No third-party subscription is bundled or silently enabled.

## Shizuku API

Shizuku API / provider / aidl / shared 13.1.5, MIT License, copyright Rikka. Source: https://github.com/RikkaApps/Shizuku-API . Unmodified Maven binaries and SHA-256 values are retained in `third_party/shizuku/`; the full license is also embedded in the APK as `licenses/Shizuku-MIT.txt`. Shizuku controls the module's own accessibility service and, when selected and authorized, a separate runtime worker for default-display foreground tasks and bounded touch input. No generic command execution IPC is exposed.

## GKD Android runtime reference

Android execution adaptation additionally references installed GKD 1.12.1 / 92, upstream commit `5a00f844989471458e4cf4601acb3f4710438f9a`, GPL-3.0-only. Unmodified reference files and original paths are recorded in [third_party/gkd-runtime/UPSTREAM.md](third_party/gkd-runtime/UPSTREAM.md). These files are review material and are not compiled as a second service. No third-party subscriptions are distributed.
