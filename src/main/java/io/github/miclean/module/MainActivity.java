package io.github.miclean.module;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.net.Uri;
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
import java.util.Map;

public final class MainActivity extends Activity {
    private SharedPreferences prefs, uiPrefs;
    private boolean updatingRuleToggles;
    private final List<RuleToggle> ruleToggles=new ArrayList<>();
    private static final class RuleToggle {
        final Switch view;
        final String[] keys;
        final boolean defaultValue;

        RuleToggle(Switch view, String[] keys, boolean defaultValue) {
            this.view = view;
            this.keys = keys;
            this.defaultValue = defaultValue;
        }

        int selected(SharedPreferences prefs) {
            int count = 0;
            for (String key : keys) if (prefs.getBoolean(key, defaultValue)) count++;
            return count;
        }

        void write(SharedPreferences.Editor edit, boolean checked) {
            for (String key : keys) edit.putBoolean(key, checked);
        }
    }
    private Map<String, PackageInfo> installed;
    private final BroadcastReceiver packageChanges = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (reloadInstalledTargets()) render();
        }
    };
    private boolean frameworkReady;
    private Switch launcherIconSwitch;
    private boolean updatingLauncherIcon;
    private final SaveNoticeGate saveNotice = new SaveNoticeGate();
    private LinearLayout content;
    private NativeAdsPanel nativeAds;
    private SmartSkipPanel smartSkip;
    private SubscriptionActions subscriptionActions;
    private final List<Runnable> refreshers = new ArrayList<>();
    private final java.util.Map<View,Integer> foldLevels = new java.util.IdentityHashMap<>();
    private static final int INK = Color.rgb(36, 44, 40);
    private static final int MUTED = Color.rgb(102, 112, 106);
    private static final int ORANGE = Color.rgb(202, 88, 27);

    private final Runnable settingsListener = () -> runOnUiThread(() -> { if (!isFinishing()) render(); });
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        subscriptionActions=new SubscriptionActions(this,()->{if(smartSkip!=null)smartSkip.refresh();},()->{if(smartSkip!=null)smartSkip.refresh();});
        Settings.listen(settingsListener);
        installed = InstalledTargets.read(getPackageManager());
        render();
    }
    @Override public void onDestroy() { Settings.unlisten(settingsListener); if(smartSkip!=null)smartSkip.close(); if(subscriptionActions!=null)subscriptionActions.close(); super.onDestroy(); }
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(subscriptionActions!=null)subscriptionActions.onActivityResult(request,result,data);}
    @Override protected void onStart() {
        super.onStart();
        IntentFilter filter = new IntentFilter(Intent.ACTION_PACKAGE_ADDED);
        filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        filter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        filter.addDataScheme("package");
        registerReceiver(packageChanges, filter);
    }

    @Override protected void onStop() {
        unregisterReceiver(packageChanges);
        super.onStop();
    }

    @Override public void onResume() {
        super.onResume();
        if (reloadInstalledTargets() || frameworkReady != Settings.ready()) render();
        syncLauncherIconSwitch();
        if (nativeAds != null) nativeAds.refreshIfVisible();
        if (smartSkip != null) smartSkip.refresh();
    }

    private boolean reloadInstalledTargets() {
        Map<String, PackageInfo> current = InstalledTargets.read(getPackageManager());
        boolean changed = !current.keySet().equals(installed.keySet());
        if (!changed) {
            for (Map.Entry<String, PackageInfo> entry : current.entrySet())
                if (entry.getValue().lastUpdateTime != installed.get(entry.getKey()).lastUpdateTime) {
                    changed = true;
                    break;
                }
        }
        installed = current;
        return changed;
    }

    private void render() {
        if(smartSkip!=null){smartSkip.close();smartSkip=null;}
        frameworkReady = Settings.ready();
        refreshers.clear();
        foldLevels.clear();
        ruleToggles.clear();
        prefs = Settings.current(this);
        if (frameworkReady) {
            SharedPreferences.Editor edit = prefs.edit();
            if(!prefs.contains(PromotionRules.MARKET_RECOMMEND))edit.putBoolean(PromotionRules.MARKET_RECOMMEND,PromotionSettings.recommendations(prefs));
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
            Settings.backup(this);
        }
        uiPrefs = getSharedPreferences("miclean_ui", Context.MODE_PRIVATE);
        getWindow().setStatusBarColor(Color.rgb(247, 246, 241));
        getWindow().setNavigationBarColor(Color.rgb(247, 246, 241));
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);
        scroll.setBackgroundColor(Color.rgb(247, 246, 241));
        content = vertical();
        content.setPadding(dp(24), dp(24), dp(24), gap(28));
        scroll.addView(content);
        setContentView(scroll);
        String moduleVersion;
        try { moduleVersion = getPackageManager().getPackageInfo(getPackageName(), 0).versionName; }
        catch (Exception ignored) { moduleVersion = "preview"; }
        label(content, "NOADMI  /  " + moduleVersion, 12, ORANGE, true);
        label(content, "NoAdMi", 30, INK, true);
        label(content, "按需精简，保留常用。", 14, MUTED, false);
        LinearLayout status = card();
        label(status, frameworkReady ? "框架服务已连接" : "等待框架服务连接", 17, INK, true);
        label(status, frameworkReady ? "修改后，强行停止并重开对应应用生效。"
            : "请在支持 libxposed API 102 的框架中启用模块并勾选目标应用，再重新打开模块。", 13, MUTED, false);
        if (Settings.status() != null) label(status, Settings.status(), 12, ORANGE, false);
        advertisingCard();
        programCard();
        label(content, "“隐藏”关闭时沿用应用原本的显示状态。分类会记住展开状态，不同版本效果以实际使用为准。", 12, MUTED, false);
        aboutCard();
        for (Runnable refresh : refreshers) refresh.run();
        if (nativeAds != null) nativeAds.refreshIfVisible();
    }

    private void updateControl(LinearLayout parent) {
        TextView update = label(parent, "检查更新  ↗", 15, ORANGE, true);
        update.setPadding(0, gap(14), 0, gap(14));
        update.setMinHeight(dp(48));
        update.setContentDescription("检查更新，打开 NoAdMi 官方 GitHub 发布页");
        update.setOnClickListener(v -> {
            try { startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Rules.RELEASES_URL))); }
            catch (android.content.ActivityNotFoundException e) {
                Toast.makeText(this, "未找到浏览器，请访问 github.com/tiancaiProgramer/NoAdMi/releases", Toast.LENGTH_LONG).show();
            }
        });
        label(parent, "tiancaiProgramer/NoAdMi · 查看最新版本与更新说明", 12, MUTED, false);
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
        launcherIconSwitch.setPadding(0, gap(12), 0, gap(12));
        launcherIconSwitch.setMinHeight(dp(48));
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

    private void advertisingCard() {
        LinearLayout box = fold(card(), "advertising", "广告管理", "官方广告设置 · App 广告 · 智能跳过", 0, 21, true);
        List<NativeAdRules.Entry> officialEntries = NativeAdRules.forInstalled(installed.keySet());
        nativeAds = null;
        if (!officialEntries.isEmpty()) {
            LinearLayout official = fold(box, "system_official", "官方广告设置",
                    "独立操作原应用的 " + officialEntries.size() + " 项真实开关", 0, 16, false);
            nativeAds = new NativeAdsPanel(this, official, this::savedNotice, officialEntries);
        }
        LinearLayout ads = fold(box, "app_ads", "App 广告", "按应用管理广告与推广隐藏", 2, 16, false);
        bulkAdControl(ads);
        label(ads, "按应用分组的下方单项就是总开关联动清单。打开补全可用广告隐藏，关闭停用；官方广告与程序管理独立操作。", 12, MUTED, false);
        if (installed.containsKey(Rules.HOME)) {
            LinearLayout homeAds = adCategory(ads, "ad_home", "米家", 2);
            installedAppInfo(homeAds, Rules.HOME, "");
            addToggle(homeAds, "隐藏启动页广告", Rules.HOME_SWITCH, true);
            addToggle(homeAds, "隐藏“我的”页商城活动横幅", Rules.HOME_BANNER_SWITCH, true);
        }
        if (healthInstalled()) {
            LinearLayout healthAds = adCategory(ads, "ad_health", "小米运动健康", 7);
            installedAppInfo(healthAds, Rules.HEALTH, "国内版：");
            installedAppInfo(healthAds, Rules.HEALTH_PLAY, "Play 版：");
            label(healthAds, "同时作用于已安装的国内版与 Play 版。", 12, MUTED, false);
            addToggle(healthAds, "隐藏启动页广告与素材预取", Rules.HEALTH_SWITCH, true);
            addToggle(healthAds, "隐藏健康页底部推广横幅", Rules.HEALTH_HOME_BANNER_SWITCH, true);
            addToggle(healthAds, "隐藏设备页推广横幅", Rules.HEALTH_DEVICE_BANNER_SWITCH, true);
            addToggle(healthAds, "隐藏运动页活动推荐", Rules.HEALTH_SPORT_ACTIVITY_SWITCH, true);
            addToggle(healthAds, "隐藏运动页底部横幅", Rules.HEALTH_SPORT_BANNER_SWITCH, true);
            addToggle(healthAds, "隐藏“我的”页活动横幅", Rules.HEALTH_BANNER_SWITCH, true);
            addToggle(healthAds, "隐藏会员、支付与问诊推广", Rules.HEALTH_MEMBER_SWITCH, true);
        }
        if (installed.containsKey(Rules.BROWSER)) {
            LinearLayout browserAds = adCategory(ads, "ad_browser", "小米浏览器", 2);
            installedAppInfo(browserAds, Rules.BROWSER, "");
            addToggle(browserAds, "隐藏主动与第三方唤起开屏广告", PromotionRules.BROWSER_SPLASH, false);
            addToggle(browserAds, "过滤信息流广告卡片", PromotionRules.BROWSER_FEED, false);
            label(browserAds, "需勾选浏览器作用域；仅处理浏览器自身推广，保留普通信息流内容，网页内广告不在此范围。", 12, MUTED, false);
        }
        if (installed.containsKey(Rules.MARKET)) {
            LinearLayout marketAds = adCategory(ads, "ad_market", "应用商店", 2);
            installedAppInfo(marketAds, Rules.MARKET, "");
            addToggle(marketAds, "隐藏开屏广告", PromotionRules.MARKET_SPLASH, false);
            addToggle(marketAds, "隐藏应用内推荐", PromotionRules.MARKET_RECOMMEND, false);
            label(marketAds, "需勾选应用商店作用域。应用内推荐包含已适配的首页横幅、首次／回流、下载／更新、详情小卡及同类推荐。", 12, MUTED, false);
        }
        if (installed.containsKey(Rules.SECURITY_CENTER)) {
            LinearLayout networkAds = adCategory(ads, "ad_network", "网络助手（手机管家）", 2);
            installedAppInfo(networkAds, Rules.SECURITY_CENTER, "");
            addToggle(networkAds, "隐藏充值入口", "network_recharge", false,
                    Rules.NETWORK_KEYS[0], Rules.NETWORK_KEYS[1]);
            addToggle(networkAds, "隐藏流量套餐推广", "network_plan_promotion", false,
                    Rules.NETWORK_KEYS[2], Rules.NETWORK_KEYS[3], Rules.NETWORK_PRODUCT_PROMOTION);
            label(networkAds, "需勾选手机管家 / 安全服务作用域。保留流量统计、联网管理和流量排行；充值入口也纳入广告总开关，请按需设置。", 12, MUTED, false);
        }
        if (installed.containsKey(Rules.SECURITY_ADD)) {
            LinearLayout gameAds = adCategory(ads, "ad_game", "游戏助手（系统服务组件）", 2);
            installedAppInfo(gameAds, Rules.SECURITY_ADD, "");
            addToggle(gameAds, "隐藏游戏推荐与推广卡片", Rules.GAME_RECOMMEND_SWITCH, false);
            addToggle(gameAds, "隐藏迅游新人加速推广弹窗", Rules.GAME_POPUP_SWITCH, false);
            label(gameAds, "需勾选系统服务组件作用域。推荐隐藏会收起热玩排行、游戏盒子等推广入口，保留已添加游戏与设置。", 12, MUTED, false);
        }
        LinearLayout skip = fold(box, "smart_skip", "智能跳过", "GKD 格式订阅 · 无障碍自动执行", 0, 16, false);
        smartSkip = new SmartSkipPanel(this, skip, subscriptionActions);
    }

    private LinearLayout adCategory(LinearLayout parent,String id,String title,int count){
        if(count==1){adHeading(parent,title,count);return parent;}
        return fold(parent,id,title,"",1,14,false);
    }

    private void adHeading(LinearLayout parent, String title, int count) {
        divider(parent);
        TextView heading = label(parent, title + " · " + count + " 项", 13, MUTED, true);
        heading.setTag("ad_heading:" + title);
        heading.setPadding(0, gap(16), 0, gap(4));
    }

    private void programCard() {
        if (!installed.containsKey(Rules.HOME) && !healthInstalled()) return;
        LinearLayout box = fold(card(), "program", "程序管理", "功能入口、底栏与地区兼容", 1, 21, true);
        if (installed.containsKey(Rules.HOME)) {
            LinearLayout home = fold(box, "app_home", "米家", "", 1, 16, true);
            installedAppInfo(home, Rules.HOME, "");
            directToggle(home, "中国大陆兼容（实验）", Rules.HOME_REGION_SWITCH, false);
            indentedHelp(home, "Play 版：跳过下载大陆版的提示，仍按所选地区使用；登录与设备服务需实际验证。");
            LinearLayout tabs = fold(home, "home_tabs", "底栏", "", 1, 14, false);
            indentedHelp(tabs, "至少保留一个页面，否则恢复原底栏；关闭隐藏后遵循米家原配置。");
            for (int i = 0; i < Rules.HOME_TAB_IDS.length; i++) directToggle(tabs,
                "隐藏“" + Rules.HOME_TAB_TITLES[i] + "”页", Rules.tabKey(Rules.HOME_TAB_IDS[i]), Rules.productTab(Rules.HOME_TAB_IDS[i]));
            LinearLayout mine = fold(home, "home_mine", "我的", "", 1, 14, false);
            indentedHelp(mine, "包含常用功能，请按需隐藏；原本未显示的入口不会被强制打开。");
            for (int i = 0; i < Rules.HOME_MINE_IDS.length; i++) directToggle(mine,
                "隐藏“" + Rules.HOME_MINE_TITLES[i] + "”", Rules.mineKey(Rules.HOME_MINE_IDS[i]), false);
        }
        if (healthInstalled()) {
            LinearLayout health = fold(box, "app_health", "小米运动健康", "", 1, 16, true);
            installedAppInfo(health, Rules.HEALTH, "国内版：");
            installedAppInfo(health, Rules.HEALTH_PLAY, "Play 版：");
            if (installed.containsKey(Rules.HEALTH_PLAY)) {
                directToggle(health, "Play 版中国大陆兼容（实验）", Rules.HEALTH_REGION_SWITCH, false);
                indentedHelp(health, "允许选择中国大陆并跳过大陆版迁移引导。请勾选 Play 版作用域。");
            }
            directToggle(health, "隐藏运动团", Rules.HEALTH_SPORT_GROUP_SWITCH, true);
            LinearLayout healthMine = fold(health, "health_mine", "我的", "", 1, 14, false);
            for (int i = 0; i < Rules.HEALTH_MINE_IDS.length; i++) directToggle(healthMine,
                "隐藏“" + Rules.HEALTH_MINE_TITLES[i] + "”", Rules.healthMineKey(Rules.HEALTH_MINE_IDS[i]), true);
        }
    }

    private void directToggle(LinearLayout parent, String text, String key, boolean initial) {
        divider(parent);
        Switch toggle = addToggle(parent, text, key, initial);
        toggle.setPadding(dp(22), gap(12), 0, gap(12));
    }

    private void indentedHelp(LinearLayout parent, String text) {
        TextView view = label(parent, text, 12, MUTED, false);
        view.setPadding(dp(22), gap(4), 0, gap(8));
    }

    private void divider(LinearLayout parent) {
        View line = new View(this);
        line.setBackgroundColor(Color.rgb(231,235,229));
        parent.addView(line, new LinearLayout.LayoutParams(-1, dp(1)));
    }

    /** Fixed chevron space keeps a plain switch and a folding row on the same text baseline. */
    private LinearLayout fold(LinearLayout parent, String id, String title, String caption,
            int countMode, int size, boolean initial) {
        int level = foldLevels.containsKey(parent) ? foldLevels.get(parent) : 0;
        LinearLayout group = vertical();
        group.setTag("group:" + id);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(-1, -2);
        if (level > 0) { groupParams.topMargin = gap(level == 1 ? 12 : 6); groupParams.bottomMargin = gap(4); }
        parent.addView(group, groupParams);
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(level == 1 ? dp(12) : 0, gap(12), level == 1 ? dp(12) : 0, gap(12));
        header.setMinimumHeight(dp(level == 0 ? 60 : level == 1 ? 56 : 52));
        if (level > 0) {
            GradientDrawable headingBackground = new GradientDrawable();
            headingBackground.setColor(level == 1 ? Color.rgb(237,242,232) : Color.rgb(246,248,243));
            headingBackground.setCornerRadius(dp(level == 1 ? 10 : 8));
            if (level == 1) headingBackground.setStroke(dp(1), Color.rgb(219,228,212));
            header.setBackground(headingBackground);
        }
        header.setFocusable(true);
        header.setTag("fold:" + id);
        TextView arrow = new TextView(this);
        arrow.setTextSize(22); arrow.setTextColor(MUTED);
        arrow.setGravity(android.view.Gravity.CENTER);
        arrow.setText("›");
        header.addView(arrow, new LinearLayout.LayoutParams(dp(22), -2));
        LinearLayout titles = vertical();
        header.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));
        TextView name = label(titles, title, size, INK, true);
        name.setPadding(0, 0, 0, 0);
        TextView detail = label(titles, caption, 12, MUTED, false);
        detail.setPadding(0, gap(3), 0, 0);
        TextView quantity = new TextView(this);
        quantity.setTextSize(12); quantity.setTextColor(MUTED);
        quantity.setGravity(android.view.Gravity.END);
        quantity.setPadding(dp(8), 0, 0, 0);
        header.addView(quantity, new LinearLayout.LayoutParams(-2, -2));
        group.addView(header, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout body = vertical(); body.setTag("body:" + id);
        foldLevels.put(body, level + 1);
        LinearLayout expandedArea;
        if (level > 0) {
            expandedArea = new LinearLayout(this);
            expandedArea.setOrientation(LinearLayout.HORIZONTAL);
            expandedArea.setPadding(dp(level == 1 ? 12 : 10), gap(8), 0, gap(4));
            View guide = new View(this);
            guide.setTag("guide:" + id);
            guide.setBackgroundColor(Color.rgb(211,223,202));
            expandedArea.addView(guide, new LinearLayout.LayoutParams(dp(2), -1));
            LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, -2, 1);
            bodyParams.leftMargin = dp(10);
            expandedArea.addView(body, bodyParams);
        } else {
            expandedArea = body;
            divider(body);
        }
        group.addView(expandedArea, new LinearLayout.LayoutParams(-1, -2));
        Runnable refresh = () -> {
            boolean expanded = uiPrefs.getBoolean(id, initial);
            String summary = caption;
            if (countMode != 0) {
                SettingCounts counts = new SettingCounts();
                for (RuleToggle row : ruleToggles) if (within(row.view, body))
                    counts.include(row.selected(prefs), row.keys.length);
                summary = countMode == 2 ? counts.advertisingSummary() : counts.settingsSummary();
                quantity.setText(counts.enabled + "/" + counts.available + " 项"
                    + (counts.unavailable == 0 ? "" : "\n不可用 " + counts.unavailable + " 项"));
                if (!caption.isEmpty()) summary = caption + "\n" + summary;
            }
            quantity.setVisibility(countMode == 1 && caption.isEmpty() ? View.VISIBLE : View.GONE);
            arrow.setRotation(expanded ? 90f : 0f);
            detail.setText(summary);
            detail.setVisibility(summary.isEmpty() || (countMode == 1 && caption.isEmpty()) ? View.GONE : View.VISIBLE);
            header.setContentDescription(title + (summary.isEmpty() ? "" : "，" + summary) + (expanded ? "，点击收起" : "，点击展开"));
            body.setVisibility(expanded ? View.VISIBLE : View.GONE);
            expandedArea.setVisibility(expanded ? View.VISIBLE : View.GONE);
        };
        header.setOnClickListener(v -> {
            uiPrefs.edit().putBoolean(id, !uiPrefs.getBoolean(id, initial)).apply();
            refresh.run();
            if (("advertising".equals(id) || "system_official".equals(id)) && nativeAds != null) nativeAds.refreshIfVisible();
        });
        refreshers.add(refresh);
        return body;
    }

    private void aboutCard() {
        LinearLayout box = appGroup("about", "关于");
        updateControl(box);
        launcherIconControl(box);
        label(box, "数量按可用开关统计，未安装项不计入；不是已拦截广告次数。", 12, MUTED, false);
        testedVersionsSection(box);
        OpenSourcePanel.add(this,box);
    }

    private void testedVersionsSection(LinearLayout box) {
        TextView header = label(box, "", 17, INK, true);
        header.setPadding(0, gap(12), 0, gap(12));
        header.setMinHeight(dp(56));
        header.setPadding(dp(12), gap(12), dp(12), gap(12));
        GradientDrawable headingBackground = new GradientDrawable();
        headingBackground.setColor(Color.rgb(237,242,232));
        headingBackground.setCornerRadius(dp(10));
        headingBackground.setStroke(dp(1), Color.rgb(219,228,212));
        header.setBackground(headingBackground);
        header.setFocusable(true);
        LinearLayout body = vertical();
        body.setPadding(dp(24), gap(8), 0, 0);
        box.addView(body, new LinearLayout.LayoutParams(-1, -2));
        label(body, "近期测试记录在前，同批按版本从新到旧排列；测试记录不限制规则加载。", 12, MUTED, false);
        for (Rules.TestedApp app : Rules.testedAppsNewestFirst())
            label(body, app.label + "\n" + app.version + "（" + app.code + "） · " + app.testedRelease + " 验证", 13, MUTED, false);
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

    private LinearLayout appGroup(String id, String title) {
        LinearLayout box = card();
        TextView header = label(box, "", 21, INK, true);
        header.setPadding(0, gap(12), 0, gap(12));
        header.setMinHeight(dp(48));
        header.setFocusable(true);
        LinearLayout body = vertical();
        box.addView(body, new LinearLayout.LayoutParams(-1, -2));
        Runnable refresh = () -> {
            boolean expanded = uiPrefs.getBoolean(id, false);
            header.setText((expanded ? "▾  " : "▸  ") + title);
            header.setContentDescription(title + (expanded ? "，点击收起" : "，点击展开"));
            body.setVisibility(expanded ? View.VISIBLE : View.GONE);
        };
        header.setOnClickListener(v -> {
            uiPrefs.edit().putBoolean(id, !uiPrefs.getBoolean(id, false)).apply();
            refresh.run();
            if ("app_system".equals(id) && nativeAds != null) nativeAds.refreshIfVisible();
        });
        refreshers.add(refresh);
        return body;
    }

    private boolean healthInstalled() {
        return installed.containsKey(Rules.HEALTH) || installed.containsKey(Rules.HEALTH_PLAY);
    }

    private void installedAppInfo(LinearLayout parent, String pkg, String prefix) {
        PackageInfo info = installed.get(pkg);
        if (info == null) return;
        long code = android.os.Build.VERSION.SDK_INT >= 28 ? info.getLongVersionCode() : info.versionCode;
        label(parent, prefix + "已安装 " + info.versionName + " · "
                + (Rules.testedVersion(pkg, info.versionName, code) ? "已测试" : "未测试"), 12, MUTED, false);
    }

    private static boolean within(View child,View parent){
        for(android.view.ViewParent p=child.getParent();p instanceof View;p=p.getParent())if(p==parent)return true;
        return false;
    }

    private List<RuleToggle> bulkMembers(View parent) {
        List<RuleToggle> result = new ArrayList<>();
        for (RuleToggle row : ruleToggles)
            if (within(row.view, parent) && BulkEnablePolicy.eligible(row.keys[0])) result.add(row);
        return result;
    }

    private void bulkAdControl(LinearLayout parent) {
        Switch toggle = new Switch(this);
        toggle.setText("广告隐藏总开关"); toggle.setContentDescription("广告隐藏总开关");
        SettingsRow.style(this, toggle);
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        TextView summary = label(parent, "", 12, ORANGE, false);
        summary.setTag("App 广告隐藏状态");
        final boolean[] updating = {false};
        refreshers.add(() -> {
            SettingCounts counts = new SettingCounts();
            for (RuleToggle row : bulkMembers(parent)) counts.include(row.selected(prefs), row.keys.length);
            updating[0] = true;
            try { toggle.setChecked(counts.allEnabled()); } finally { updating[0] = false; }
            toggle.setEnabled(frameworkReady && counts.available > 0);
            summary.setText(counts.advertisingSummary());
            if (android.os.Build.VERSION.SDK_INT >= 30) toggle.setStateDescription(counts.advertisingSummary());
        });
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (updating[0]) return;
            SharedPreferences.Editor edit = prefs.edit();
            for (RuleToggle row : bulkMembers(parent)) row.write(edit, checked);
            boolean saved = edit.commit(); if (saved) Settings.backup(this);
            for (Runnable refresh : refreshers) refresh.run();
            savedNotice(saved, saved ? "已保存，请强行停止并重新打开对应应用" : "保存失败，请重新打开模块");
        });
    }

    private Switch addToggle(LinearLayout parent, String text, String key, boolean defaultValue) {
        return addToggle(parent, text, key, defaultValue, key);
    }

    private Switch addToggle(LinearLayout parent, String text, String tag, boolean defaultValue, String... keys) {
        Switch toggle = new Switch(this);
        toggle.setText(text);
        SettingsRow.style(this, toggle);
        RuleToggle row = new RuleToggle(toggle, keys, defaultValue);
        ruleToggles.add(row);
        toggle.setTag(tag);
        refreshers.add(() -> {
            int selected = row.selected(prefs);
            boolean mixed = selected > 0 && selected < keys.length;
            toggle.setEnabled(frameworkReady);
            toggle.setText(mixed ? text + " · 部分开启" : text);
            if (android.os.Build.VERSION.SDK_INT >= 30)
                toggle.setStateDescription(mixed ? "部分开启" : null);
            updatingRuleToggles = true;
            try { toggle.setChecked(selected == keys.length); }
            finally { updatingRuleToggles = false; }
        });
        toggle.setOnCheckedChangeListener((button, checked) -> {
            if (updatingRuleToggles) return;
            SharedPreferences.Editor edit = prefs.edit();
            row.write(edit, checked);
            boolean saved = edit.commit();
            if (saved) Settings.backup(this);
            for (Runnable refresh : refreshers) refresh.run();
            savedNotice(saved, saved ? "已保存，请强行停止并重新打开对应应用" : "保存失败，请重新打开模块");
        });
        parent.addView(toggle, new LinearLayout.LayoutParams(-1, -2));
        return toggle;
    }

    private void savedNotice(boolean success, String message) {
        if (!success || saveNotice.allow(android.os.SystemClock.elapsedRealtime()))
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout vertical() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        return box;
    }
    private LinearLayout card() {
        LinearLayout box = vertical();
        box.setPadding(dp(20), gap(14), dp(20), gap(14));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.WHITE);
        bg.setCornerRadius(dp(18));
        box.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = gap(16);
        content.addView(box, lp);
        return box;
    }
    private TextView label(LinearLayout parent, String text, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(text);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setGravity(android.view.Gravity.START | android.view.Gravity.CENTER_VERTICAL);
        view.setLineSpacing(gap(4), 1);
        view.setPadding(0, gap(5), 0, gap(5));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
        return view;
    }
    private int gap(int value) { return UiSpacing.vertical(this, value); }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
