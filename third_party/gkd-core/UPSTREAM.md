# GKD subscription core

https://github.com/gkd-kit/gkd/tree/fe6736fb083ba90d7bace124e30b61ff6e5701f2
GPL-3.0-only. Source retained verbatim; Android-free error text is adapted during the NoAdMi build.

0.12.19 retains this subscription model/parser pin. GKD v1.13.0's group-only
global application inclusion policy is adapted in NoAdMi SkipScope (child apps
override only activity/version restrictions), with independent application and
subscription saved-off controls still enforced. Current policy references are
under ../gkd-runtime/reference/v1.13.0. This is not a wholesale replacement with
the upstream UI/editor/privilege modules.
