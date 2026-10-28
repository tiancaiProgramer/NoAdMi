# GKD Android execution reference

Source: https://github.com/gkd-kit/gkd
Commit: 5a00f844989471458e4cf4601acb3f4710438f9a (installed GKD 1.12.1 / 92)
License: GPL-3.0-only; see the project LICENSE and THIRD_PARTY_NOTICES.md.

Unmodified source files in reference/ are supplied for review, not compiled as a second service.
Original paths: app/src/main/kotlin/li/songe/gkd/{a11y,service,data}/.

NoAdMi adapts the upstream default-display event filtering, event/source grouping, event-node-first querying, root fallback, positive-before-exclusion checks, 300 ms follow-up within 3 s of app/action changes, forced/delayed rule windows, unknown-Activity fallback, screen broadcasts, transparent non-touchable 1px accessibility keep-alive view and click/gesture selection to its existing settings and selector-core interfaces.

The existing selector/subscription core remains pinned separately under gkd-selector/ and gkd-core/. No GKD UI, subscription content, HTTP server, screenshot service or additional privileged input interface is bundled. NoAdMi keeps foreground/master/scope checks and bounded local diagnostics. Android RPC reads use a bounded host executor with stale-result disposal; these platform guards are not third-party rule changes.

Accessibility metadata is aligned with the installed GKD service: state/content events, all feedback, default/report-ID/retrieve-window/include-unimportant flags and accessibility-tool status. NoAdMi does not add screenshot capability. Privileged touch injection remains unimplemented; Shizuku still controls only NoAdMi service enablement. Current-device PJT normal cold start passed 3/3 with an independent GKD and master-off comparison. Frame retention/latency still varies; a second device repeat observed PJT, JD and Bilibili ad skips; the remaining eight apps had no verifiable skip affordance in that trial. Taobao/Xianyu ad-scene acceptance remains open. See SMART_SKIP_VERIFICATION.md.
