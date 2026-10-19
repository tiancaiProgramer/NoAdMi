package io.github.miclean.module;

import android.app.Activity;
import android.content.Context;
import android.content.ComponentName;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

public final class MainActivity extends Activity {
    private SharedPreferences prefs, uiPrefs;
    private boolean frameworkReady;
    private Switch launcherIconSwitch;
    private boolean updatingLauncherIcon;
    private LinearLayout content;
    private final List<Runnable> refreshers = new ArrayList<>();
    private static final int INK = Color.rgb(36, 44, 40);
    private static final int MUTED = Color.rgb(102, 112, 106);
    private static final int ORANGE = Color.rgb(202, 88, 27);

    // Replaced only when LSPosed has injected this module's own process.
    public static int getFrameworkApiVersion() { return 0; }
    @Override public void onCreate(Bundle state) { super.onCreate(state); render(); }
    @Override public void onResume() {
        super.onResume();
        if (frameworkReady != (getFrameworkApiVersion() >= 93)) render();
        syncLauncherIconSwitch();
    }

    @SuppressWarnings("deprecation") private void render() {
        frameworkReady = getFrameworkApiVersion() >= 93;
        refreshers.clear();
        try {
            prefs = getSharedPreferences(Rules.PREFS,
                frameworkReady ? Context.MODE_WORLD_READABLE : Context.MODE_PRIVATE);
            SharedPreferences.Editor edit = prefs.edit();
            String[] old = {Rules.HEALTH_SWITCH, Rules.HOME_SWITCH, Rules.HOME_BANNER_SWITCH,
                Rules.HOME_PRODUCT_TAB_SWITCH, Rules.HEALTH_BANNER_SWITCH, Rules.HEALTH_HOME_BANNER_SWITCH,
                Rules.HEALTH_DEVICE_BANNER_SWITCH, Rules.HEALTH_MEMBER_SWITCH,
                Rules.HEALTH_SPORT_GROUP_SWITCH, Rules.HEALTH_SPORT_ACTIVITY_SWITCH, Rules.HEALTH_SPORT_BANNER_SWITCH};
            for (String key : old) edit.putBoolean(key, prefs.getBoolean(key, true));
            edit.putBoolean(Rules.HOME_REGION_SWITCH, prefs.getBoolean(Rules.HOME_REGION_SWITCH, false));
            edit.putBoolean(Rules.HEALTH_REGION_SWITCH, prefs.getBoolean(Rules.HEALTH_REGION_SWITCH, false));
            for (String id : Rules.HEALTH_MINE_IDS) edit.putBoolean(Rules.healthMineKey(id),
                prefs.getBoolean(Rules.healthMineKey(id), true));
            boolean independent = prefs.getBoolean(Rules.HOME_INDEPENDENT_OPTIONS, false);
            for (String id : Rules.HOME_TAB_IDS) edit.putBoolean(Rules.tabKey(id),
                Rules.effectiveHomeOption(independent, prefs.getBoolean(Rules.HOME_TABS_ENABLED, true),
                    prefs.getBoolean(Rules.tabKey(id), Rules.productTab(id))));
            for (String id : Rules.HOME_MINE_IDS) edit.putBoolean(Rules.mineKey(id),
                Rules.effectiveHomeOption(independent, prefs.getBoolean(Rules.HOME_MINE_ENABLED, false),
                    prefs.getBoolean(Rules.mineKey(id), false)));
            edit.putBoolean(Rules.HOME_INDEPENDENT_OPTIONS, true);
            edit.commit();
        } catch (SecurityException e) {
            prefs = getSharedPreferences(Rules.PREFS, Context.MODE_PRIVATE);
            frameworkReady = false;
        }
        uiPrefs = getSharedPreferences("miclean_ui", Context.MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(247, 246, 241));
        getWindow().setNavigationBarColor(Color.rgb(247, 246, 241));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(Color.rgb(247, 246, 241));
        content = vertical();
        content.setPadding(dp(24), dp(24), dp(24), dp(28));
        scroll.addView(content);
        setContentView(scroll);
        String moduleVersion;
        try { moduleVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { moduleVersion = "preview"; }
        label(content, "NOADMI  /  " + moduleVersion, 12, ORANGE, true);
        label(content, "NoAdMi", 30, INK, true);
        label(content, "按需精简，保留常用。", 14, MUTED, false);
        testedVersionsCard();
        LinearLayout status = card();
        label(status, frameworkReady ? "框架已注入本模块" : "尚未启用 Hook 框架", 17, INK, true);
        label(status, frameworkReady ? "展开分类设置；保存后请强行停止并重新打开对应应用。"
            : "请在 LSPosed 启用本模块并勾选米家、运动健康，再重新打开模块。", 13, MUTED, false);
        launcherIconControl(status);
        homeCard();
        healthCard();
        label(content, "“隐藏”关闭时沿用应用原本的显示状态。分类会记住展开状态，不同版本效果以实际使用为准。", 12, MUTED, false);
        for (Runnable refresh : refreshers) refresh.run();
    }

    private ComponentName launcherAlias() {
        return new ComponentName(this, getPackageName() + ".LauncherAlias");
    }

    private boolean launcherIconVisible() {
        int state = getPackageManager().getComponentEnabledSetting(launcherAlias());
        return state == PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
            || state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
    }

    private void syncLauncherIconSwitch() {
        if (launcherIconSwitch == null) return;
        updatingLauncherIcon = true;
        launcherIconSwitch.setChecked(launcherIconVisible());
        updatingLauncherIcon = false;
    }

    private void launcherIconControl(LinearLayout parent) {
        launcherIconSwitch = new Switch(this);
        launcherIconSwitch.setText("显示桌面图标");
        launcherIconSwitch.setTextSize(14);
        launcherIconSwitch.setTextColor(INK);
        launcherIconSwitch.setPadding(0, dp(12), 0, dp(12));
        launcherIconSwitch.setMinHeight(dp(50));
        syncLauncherIconSwitch();
        launcherIconSwitch.setOnCheckedChangeListener((button, visible) -> {
            if (updatingLauncherIcon) return;
            try {
                getPackageManager().setComponentEnabledSetting(launcherAlias(), visible
                    ? PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                Toast.makeText(this, visible ? "桌面图标已显示" : "桌面图标已隐藏，可从 LSPosed 打开模块设置", Toast.LENGTH_SHORT).show();
            } catch (RuntimeException e) {
                syncLauncherIconSwitch();
                Toast.makeText(this, "图标设置失败，请重试", Toast.LENGTH_SHORT).show();
            }
        });
        parent.addView(launcherIconSwitch, new LinearLayout.LayoutParams(-1, -2));
        label(parent, "隐藏后可从 LSPosed 的模块设置重新打开；桌面可能稍后刷新。", 12, MUTED, false);
    }

    private void homeCard() {
        LinearLayout box = card();
        label(box, "米家", 21, INK, true);
        boolean installed = installedAppInfo(box, Rules.HOME, "");
        LinearLayout ads = section(box, "home_ads", "通用与横幅", "地区兼容、开屏、个人中心", new String[]{Rules.HOME_REGION_SWITCH, Rules.HOME_SWITCH, Rules.HOME_BANNER_SWITCH}, new boolean[]{false, true, true}, installed);
        addToggle(ads, "中国大陆兼容（实验）", Rules.HOME_REGION_SWITCH, false, installed);
        label(ads, "Play 版：跳过下载大陆版的提示，仍按你选择的地区使用。登录与设备服务需实际验证。", 12, MUTED, false);
        addToggle(ads, "隐藏启动页广告", Rules.HOME_SWITCH, true, installed);
        addToggle(ads, "隐藏“我的”页商城活动横幅", Rules.HOME_BANNER_SWITCH, true, installed);
        String[] tabKeys = new String[Rules.HOME_TAB_IDS.length];
        boolean[] tabDefaults = new boolean[tabKeys.length];
        for (int i = 0; i < tabKeys.length; i++) {
            tabKeys[i] = Rules.tabKey(Rules.HOME_TAB_IDS[i]);
            tabDefaults[i] = Rules.productTab(Rules.HOME_TAB_IDS[i]);
        }
        LinearLayout tabs = section(box, "home_tabs", "底栏", "5 个页面入口", tabKeys, tabDefaults, installed);
        label(tabs, "关闭单项隐藏后遵循米家原配置；商城仍可由原生开关关闭。至少保留一个页面，否则恢复原底栏。", 12, MUTED, false);
        for (int i = 0; i < tabKeys.length; i++) addToggle(tabs, "隐藏“" + Rules.HOME_TAB_TITLES[i] + "”页", tabKeys[i], tabDefaults[i], installed);
        String[] mineKeys = new String[Rules.HOME_MINE_IDS.length];
        for (int i = 0; i < mineKeys.length; i++) mineKeys[i] = Rules.mineKey(Rules.HOME_MINE_IDS[i]);
        LinearLayout mine = section(box, "home_mine", "我的", "13 个功能与服务入口", mineKeys, new boolean[mineKeys.length], installed);
        label(mine, "这些项目包含常用功能，请按需隐藏。应用原本未显示的入口不会被强制打开。", 12, MUTED, false);
        for (int i = 0; i < mineKeys.length; i++) addToggle(mine, "隐藏“" + Rules.HOME_MINE_TITLES[i] + "”", mineKeys[i], false, installed);
    }

    private void healthCard() {
        LinearLayout box = card();
        label(box, "小米运动健康", 21, INK, true);
        boolean installed = installedAppInfo(box, Rules.HEALTH, "国内版：");
        boolean playInstalled = installedAppInfo(box, Rules.HEALTH_PLAY, "Play 版：");
        boolean rulesInstalled = installed || playInstalled;
        label(box, "去广告与入口选项同时作用于已安装的国内版和 Play 版，仅隐藏应用已有内容。", 12, MUTED, false);
        LinearLayout general = section(box, "health_general", "通用与横幅", "地区兼容、开屏、健康与设备横幅", new String[]{Rules.HEALTH_REGION_SWITCH, Rules.HEALTH_SWITCH, Rules.HEALTH_HOME_BANNER_SWITCH, Rules.HEALTH_DEVICE_BANNER_SWITCH}, new boolean[]{false, true, true, true}, rulesInstalled);
        addToggle(general, "Play 版中国大陆兼容（实验）", Rules.HEALTH_REGION_SWITCH, false, playInstalled);
        label(general, "允许选择中国大陆，并跳过启动时的大陆版迁移引导。需在 LSPosed 勾选 Play 版运动健康。", 12, MUTED, false);
        addToggle(general, "隐藏启动页广告与素材预取", Rules.HEALTH_SWITCH, true, rulesInstalled);
        addToggle(general, "隐藏健康页底部推广横幅", Rules.HEALTH_HOME_BANNER_SWITCH, true, rulesInstalled);
        addToggle(general, "隐藏设备页推广横幅", Rules.HEALTH_DEVICE_BANNER_SWITCH, true, rulesInstalled);
        LinearLayout sport = section(box, "health_sport", "运动", "分别控制运动页三个推荐区块", new String[]{Rules.HEALTH_SPORT_GROUP_SWITCH, Rules.HEALTH_SPORT_ACTIVITY_SWITCH, Rules.HEALTH_SPORT_BANNER_SWITCH}, new boolean[]{true, true, true}, rulesInstalled);
        label(sport, "保留运动入口、运动记录、训练指标和跑步路线。", 12, MUTED, false);
        addToggle(sport, "隐藏运动团", Rules.HEALTH_SPORT_GROUP_SWITCH, true, rulesInstalled);
        addToggle(sport, "隐藏活动推荐", Rules.HEALTH_SPORT_ACTIVITY_SWITCH, true, rulesInstalled);
        addToggle(sport, "隐藏运动页底部横幅", Rules.HEALTH_SPORT_BANNER_SWITCH, true, rulesInstalled);
        String[] mineKeys = new String[2 + Rules.HEALTH_MINE_IDS.length];
        boolean[] mineDefaults = new boolean[mineKeys.length];
        mineKeys[0] = Rules.HEALTH_BANNER_SWITCH;
        mineKeys[1] = Rules.HEALTH_MEMBER_SWITCH;
        for (int i = 0; i < Rules.HEALTH_MINE_IDS.length; i++) mineKeys[i + 2] = Rules.healthMineKey(Rules.HEALTH_MINE_IDS[i]);
        java.util.Arrays.fill(mineDefaults, true);
        LinearLayout mine = section(box, "health_mine", "我的", "广告、营销卡片与功能入口", mineKeys, mineDefaults, rulesInstalled);
        addToggle(mine, "隐藏活动横幅", Rules.HEALTH_BANNER_SWITCH, true, rulesInstalled);
        addToggle(mine, "隐藏会员、支付与问诊推广", Rules.HEALTH_MEMBER_SWITCH, true, rulesInstalled);
        label(mine, "以下为功能入口，可分别隐藏；关闭隐藏后恢复原有显示。", 12, MUTED, false);
        for (int i = 0; i < Rules.HEALTH_MINE_IDS.length; i++) addToggle(mine,
            "隐藏“" + Rules.HEALTH_MINE_TITLES[i] + "”", Rules.healthMineKey(Rules.HEALTH_MINE_IDS[i]), true, rulesInstalled);
    }

    private void testedVersionsCard() {
        LinearLayout box = card();
        TextView header = label(box, "", 17, INK, true);
        header.setPadding(0, dp(12), 0, dp(12));
        header.setFocusable(true);
        LinearLayout body = vertical();
        box.addView(body, new LinearLayout.LayoutParams(-1, -2));
        label(body, "以下为已进行过验证的版本，具体功能效果以实际使用为准。", 12, MUTED, false);
        for (Rules.TestedApp app : Rules.TESTED_APPS)
            label(body, app.label + "\n" + app.version + "（" + app.code + "）", 13, MUTED, false);
        Runnable refresh = () -> {
            boolean expanded = uiPrefs.getBoolean("tested_versions", false);
            header.setText((expanded ? "▾  " : "▸  ") + "测试版本说明");
            header.setContentDescription("测试版本说明" + (expanded ? "，点击收起" : "，点击展开"));
            body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        };
        header.setOnClickListener(v -> {
            uiPrefs.edit().putBoolean("tested_versions", !uiPrefs.getBoolean("tested_versions", false)).apply();
            refresh.run();
        });
        refreshers.add(refresh);
    }

    private boolean installedAppInfo(LinearLayout parent, String pkg, String prefix) {
        boolean installed = false;
        String text;
        try {
            PackageInfo p = getPackageManager().getPackageInfo(pkg, 0);
            installed = true;
            long code = android.os.Build.VERSION.SDK_INT >= 28 ? p.getLongVersionCode() : p.versionCode;
            text = "已安装 " + p.versionName + " · "
                + (Rules.testedVersion(pkg, p.versionName, code) ? "已测试" : "未测试");
        } catch (PackageManager.NameNotFoundException e) { text = "未安装"; }
        label(parent, prefix + text, 12, MUTED, false);
        return installed;
    }

    private LinearLayout section(LinearLayout parent, String id, String title, String detail,
            String[] keys, boolean[] defaults, boolean installed) {
        TextView header = label(parent, "", 16, INK, true);
        header.setPadding(0, dp(15), 0, dp(15));
        header.setFocusable(true);
        LinearLayout body = vertical();
        parent.addView(body, new LinearLayout.LayoutParams(-1, -2));
        Runnable refresh = () -> {
            boolean expanded = uiPrefs.getBoolean(id, false);
            int count = 0;
            for (int i = 0; i < keys.length; i++) if (prefs.getBoolean(keys[i], defaults[i])) count++;
            String summary = "已开启 " + count + "/" + keys.length;
            header.setText((expanded ? "▾  " : "▸  ") + title + "  ·  " + summary);
            header.setContentDescription(title + "，" + summary + (expanded ? "，点击收起" : "，点击展开"));
            body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        };
        header.setOnClickListener(v -> {
            uiPrefs.edit().putBoolean(id, !uiPrefs.getBoolean(id, false)).apply();
            refresh.run();
        });
        refreshers.add(refresh);
        label(body, detail, 12, ORANGE, false);
        return body;
    }

    private Switch addToggle(LinearLayout parent, String text, String key, boolean defaultValue,
            boolean installed) {
        Switch toggle = new Switch(this);
        toggle.setText(text);
        toggle.setTextSize(14);
        toggle.setTextColor(INK);
        toggle.setPadding(0, dp(12), 0, dp(12));
        toggle.setMinHeight(dp(50));
        toggle.setChecked(prefs.getBoolean(key, defaultValue));
        refreshers.add(() -> toggle.setEnabled(frameworkReady && installed));
        toggle.setOnCheckedChangeListener((button, checked) -> {
            boolean saved = prefs.edit().putBoolean(key, checked).commit();
            for (Runnable refresh : refreshers) refresh.run();
            Toast.makeText(this, saved ? "已保存，请强行停止并重新打开对应应用" : "保存失败，请重新打开模块", Toast.LENGTH_SHORT).show();
        });
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        return toggle;
    }

    private LinearLayout vertical() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }
    private LinearLayout card() {
        LinearLayout box = vertical();
        box.setPadding(dp(20), dp(14), dp(20), dp(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(16);
        content.addView(box, lp);
        return box;
    }
    private TextView label(LinearLayout parent, String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(dp(4), 1);
        view.setPadding(0, dp(5), 0, dp(5));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
