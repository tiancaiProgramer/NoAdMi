# GKD selector

Source: https://github.com/gkd-kit/gkd
Tag: v1.13.0 (released 2026-11-06)
Commit: 0ee5d146a382d1066cddfdb6d0ace9d9563a8464
License: GPL-3.0-only. Original source is retained in src.
The Kotlin 2.4.21 JVM build strips JavaScript export annotations and substitutes the JVM regex implementation for its expect declaration. Selector behavior is retained. See tools/build_smart_skip.py.

0.12.19 updates the complete commonMain/JVM production selector sources to this
stable tag, including upstream query/trace consistency, relation-offset handling,
fast-query self filtering and bounded observer support. All source bytes are
retained unchanged; SOURCE.json records each original path and SHA-256. Platform
adapters and privacy-preserving diagnostic counters remain NoAdMi code. This pin
does not imply that GKD's Android application or privileged-service stack is bundled.
