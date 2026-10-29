# GKD Android execution reference

Source: https://github.com/gkd-kit/gkd
Commit: 5a00f844989471458e4cf4601acb3f4710438f9a (installed GKD 1.12.1 / 92)
License: GPL-3.0-only; see the project LICENSE and THIRD_PARTY_NOTICES.md.

0.12.13 additionally follows the service component class declared in the pinned
[upstream manifest](https://github.com/gkd-kit/gkd/blob/5a00f844989471458e4cf4601acb3f4710438f9a/app/src/main/AndroidManifest.xml):
`com.google.android.accessibility.selecttospeak.SelectToSpeakService`.
NoAdMi registers this class under its own application package and label and
delegates to its existing engine. The former NoAdMi component is recognized only
for own-setting cleanup/migration; no second engine is registered. GKD's own
component remains a different package and must never be removed by this migration.
The [upstream author's explanation](https://github.com/orgs/gkd-kit/discussions/1338)
describes WeChat's accessibility-node obfuscation when its expected service is
absent. Device comparison and scope are documented in
[the 0.12.13 verification](../../docs/wechat-service-compat-0.12.13.md).

Unmodified source files in reference/ are supplied for review, not compiled as a second service.
Original paths: app/src/main/kotlin/li/songe/gkd/{a11y,service,data,shizuku}/.

NoAdMi adapts the upstream default-display event filtering, event/source grouping, event-node-first querying, root fallback, positive-before-exclusion checks, 300 ms follow-up within 3 s of app/action changes, forced/delayed rule windows, unknown-Activity fallback, screen broadcasts, transparent non-touchable 1px accessibility keep-alive view and click/gesture selection to its existing settings and selector-core interfaces.

The existing selector/subscription core remains pinned separately under gkd-selector/ and gkd-core/. No GKD UI, subscription content, HTTP server, screenshot service is bundled. NoAdMi keeps foreground/master/scope checks and bounded local diagnostics. Android RPC reads use a bounded host executor with stale-result disposal; these platform guards are not third-party rule changes.

Accessibility metadata is aligned with the installed GKD service: state/content events, all feedback, default/report-ID/retrieve-window/include-unimportant flags and accessibility-tool status. NoAdMi does not add screenshot capability. In 0.12.9 Shizuku still controlled only NoAdMi service enablement. Current-device PJT normal cold start passed 3/3 with an independent GKD and master-off comparison. Frame retention/latency still varies; a second device repeat observed PJT, JD and Bilibili ad skips; the remaining eight apps had no verifiable skip affordance in that trial. Taobao/Xianyu ad-scene acceptance remains open. See SMART_SKIP_VERIFICATION.md.

0.12.9 additionally adapts same-Activity scene generations and one-second state-event deduplication from A11yState.kt, ordered event identity grouping/self-event filtering from A11yRuleEngine.kt, and short service-owned accessibility feedback from util/Toast.kt at the same pinned commit. That version did not include a task-stack listener or privileged coordinate-input bridge. See docs/wechat-miniapp-investigation.md for the known remaining differences.

0.12.10 adapts retained action-delay state / whole-window deadline queries and independent future inspections from A11yRuleEngine.kt / ResolvedRule.kt. The separately authorized runtime worker implements default-display task observation (ActivityTaskManager / TaskStackListener reference) and IInputManager tap/swipe injection (InputManager / InputShellCommand reference), with fixed input commands as an API-unavailable fallback (UserService reference), with owner-UID, foreground, screen, user and coordinate checks. It does not expose GKD's generic execCommand or screenshot methods. Root mode continues using accessibility input. The unmodified input/worker references are review material only.

0.12.11 adapts A11yRuleEngine's ordered continuation after synchronous accepted actions. Before another eligible rule, NoAdMi obtains and refreshes the current window and discards the pre-action snapshot; it retains epoch, package, revision and enabled checks. Asynchronous gestures still await completion. Each rule is visited once per pass; this is not an unconditional second click. The virtual-device-0 comparison did not deliver touches on the connected foldable, so the final package retains 0.12.10's tested input route.

0.12.12 exposes explicit reversible input comparisons, referencing the same pinned UserServiceWrapper/UserService fixed `input tap/swipe` command route and InputShellCommand virtual touchscreen device 0 route. Mode selection is local; the default route is retained. Another mode retains authorized task observation but uses accessibility gestures for coordinates. Commands are generated only from validated typed finite coordinates and bounded duration; no generic shell IPC or subscription-supplied command text is exposed. Accepted calls are not claimed as actual ad closure. See docs/wechat-input-comparison-0.12.12.md.
