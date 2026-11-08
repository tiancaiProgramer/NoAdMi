package io.github.miclean.module.skip

private var checks = 0
private fun verify(value: Boolean) { checks++; check(value) { "GKD 1.13 scope assertion $checks" } }
fun main() {
    fun source(group: String = "", parent: String = "", child: String = ""): SkipSource = SkipSource(7,
        """{id:7,name:'scope',version:1,globalGroups:[{key:1,name:'global',$group ${if(parent.isEmpty()) "" else "apps:[$parent],"}rules:[{matches:'[text="目标"]'${if(child.isEmpty()) "" else ",apps:[$child]"}}]}]}""", enabled = true)
    fun allows(s: SkipSource, app: String = "test.app", activity: String? = null, system: Boolean = false, launcher: Boolean = false, code: Int = 1): Boolean {
        val g = s.subscription.globalGroups.single()
        return SkipScope.applicable(s, g, g.rules.single(), app, activity, code, "1", system, launcher)
    }
    val child = "{id:'test.app',activityIds:['.Allowed'],versionCodes:[2]}"
    verify(!allows(source("matchAnyApp:false,", child = child), activity = "test.app.Allowed", code = 2))
    verify(!allows(source(child = child), activity = "test.app.Allowed", code = 2, system = true))
    verify(!allows(source(child = child), activity = "test.app.Allowed", code = 2, launcher = true))
    verify(allows(source(child = child), activity = "test.app.Allowed", code = 2))
    verify(!allows(source(child = child), activity = "test.app.Other", code = 2))
    verify(!allows(source(child = child), activity = "test.app.Allowed", code = 1))
    verify(allows(source("matchAnyApp:false,", "{id:'test.app'}", child), activity = "test.app.Allowed", code = 2, system = true, launcher = true))
    verify(!allows(source("matchAnyApp:true,", "{id:'test.app',enable:false}", child), activity = "test.app.Allowed", code = 2))
    verify(!allows(source("matchAnyApp:false,matchLauncher:true,"), launcher = true))
    verify(!allows(source("matchAnyApp:true,matchSystemApp:false,"), system = true))
    verify(allows(source("matchAnyApp:true,matchSystemApp:true,"), system = true))
    verify(!allows(source("matchAnyApp:true,matchLauncher:false,"), launcher = true))
    verify(allows(source("matchAnyApp:true,matchLauncher:true,"), launcher = true))
    val explicit = source("matchAnyApp:false,", "{id:'test.app',activityIds:['.Parent']}", "{id:'test.app',activityIds:['.Child']}")
    verify(!allows(explicit, activity = "test.app.Parent")); verify(allows(explicit, activity = "test.app.Child"))
    verify(allows(explicit, activity = null))
    verify(!allows(explicit.copy(enabled = false), activity = "test.app.Child"))
    verify(!allows(explicit.copy(apps = mapOf("test.app" to false)), activity = "test.app.Child"))
    verify(!allows(explicit.copy(globalApps = mapOf("test.app/1" to false)), activity = "test.app.Child"))
    verify(!allows(explicit, app = "other.app", activity = "other.app.Child"))
    // The management projection must use the same scope decision as execution.
    verify(SkipAppRules.groups(listOf(source("matchAnyApp:false,", child = child)), "test.app", 2, "1", false, false).isEmpty())
    println("PASS: $checks GKD 1.13 group-only application scope, child overrides and saved-off isolation checks")
}
