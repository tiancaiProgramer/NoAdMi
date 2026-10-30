package io.github.miclean.module.skip

import li.gkd.app.subscription.RawSubscription

data class SkipManagedGroup(val source: SkipSource, val app: String, val group: RawSubscription.RawGroupProps,
    val global: Boolean, val compatible: Boolean) {
    val preference: Boolean get() = if (global) source.globalApps[source.groupKey(app, group.key)]
        ?: source.groupPreference("*", group) else source.groupPreference(app, group)
    val parentEnabled: Boolean get() = source.enabled && source.apps[app] != false &&
        (!global || source.groupPreference("*", group))
}

/** Both management views point to the same subscription/group identities. */
object SkipAppRules {
    @JvmStatic fun groups(sources: List<SkipSource>, app: String, code: Int?, name: String?, system: Boolean,
        launcher: Boolean): List<SkipManagedGroup> = buildList {
        for (source in sources) {
            for (group in source.subscription.getAppGroups(app)) add(SkipManagedGroup(source, app, group, false,
                group.rules.any { SkipScope.matchesApp(group, it, app, null, code, name, system, launcher) }))
            for (group in source.subscription.globalGroups) {
                val compatible = group.rules.any { SkipScope.matchesApp(group, it, app, null, code, name, system, launcher) }
                if (compatible) add(SkipManagedGroup(source, app, group, true, true))
            }
        }
    }
}
