package io.github.miclean.module;

import android.app.Activity;
import android.graphics.Color;
import android.view.View;
import android.widget.ProgressBar;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.json.JSONArray;
import org.json.JSONObject;

/** Shows actual persisted official states; never substitutes module preferences. */
final class NativeAdsPanel {
    interface Notice { void show(boolean success, String message); }
    private static final ExecutorService EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "NoAdMi-official-controls"); thread.setDaemon(true); return thread;
    });
    private final Activity activity;
    private final Notice notice;
    private final LinearLayout root;
    private final TextView progress;
    private final ProgressBar spinner;
    private final TextView refresh, closeState;
    private final Switch close;
    private final Map<String, Row> rows = new LinkedHashMap<>();
    private volatile boolean busy;
    private boolean updating;
    private static final class Row {
        Switch toggle;
        TextView state;
        Boolean enabled;
        String error;
    }
    NativeAdsPanel(Activity activity, LinearLayout root, Notice notice) {
        this.activity=activity; this.root=root; this.notice=notice;
        LinearLayout status=new LinearLayout(activity);
        status.setOrientation(LinearLayout.HORIZONTAL);status.setGravity(Gravity.CENTER_VERTICAL);
        status.setPadding(0,gap(6),0,gap(6));root.addView(status);
        spinner=new ProgressBar(activity,null,android.R.attr.progressBarStyleSmall);
        spinner.setIndeterminate(true);spinner.setContentDescription("正在读取或保存广告设置");
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams indicator=new LinearLayout.LayoutParams(dp(20),dp(20));indicator.rightMargin=dp(8);
        status.addView(spinner,indicator);
        progress=new TextView(activity);progress.setTextSize(14);progress.setTextColor(Color.rgb(202,88,27));
        progress.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        progress.setText("展开后读取实际设置；未识别的版本保留原状态。");
        status.addView(progress,new LinearLayout.LayoutParams(0,-2,1));
        close = new Switch(activity); close.setText("一键关闭官方广告");
        close.setContentDescription("一键关闭官方广告"); close.setTag("official_close");
        SettingsRow.style(activity, close); root.addView(close);
        closeState = text("", 12); closeState.setTag("官方广告关闭状态");
        closeState.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        text("仅执行关闭，全部回读确认关闭后显示完成；不会批量开启官方广告。下方单项打开表示官方广告或推荐已开启。", 13);
        text("需要 Root；本地配置保存时会停止对应应用。系统工具广告另需勾选“小米账号”作用域，并重启账号应用；后台保存由账号服务确认。", 12);
        refresh = text("刷新真实开关状态  ↻", 14);
        refresh.setMinHeight(dp(52)); refresh.setGravity(Gravity.CENTER_VERTICAL);
        refresh.setTextColor(Color.rgb(202,88,27)); refresh.setFocusable(true);
        for (NativeAdRules.Entry e : NativeAdRules.ENTRIES) {
            Row row = new Row(); row.toggle=new Switch(activity); row.toggle.setText(e.title);
            SettingsRow.style(activity, row.toggle);
            row.toggle.setVisibility(View.GONE); root.addView(row.toggle);
            row.state=text(e.title + " · 尚未读取", 12); rows.put(e.id,row);
            row.toggle.setOnCheckedChangeListener((button, enabled) -> {
                if (updating || busy) return;
                if (NativeAdRules.supportsBackgroundWrite(e)) save(e, enabled);
            });
        }
        refresh.setOnClickListener(v -> refresh());
        close.setOnCheckedChangeListener((button, checked) -> {
            if (updating || busy) return;
            // This is a one-way action: an unchecked state never re-enables official ads.
            if (checked) closeAll(); else controls();
        });
        text("文件管理扩展服务包含云盘等普通功能，不纳入批量关闭；手机管家目前没有已适配的独立官方广告开关。",12);
        controls();
    }
    void refreshIfVisible() { if (root.isShown()) refresh(); }
    void refresh() {
        if (busy) return;
        busy=true; controls(); progress.setText("正在读取广告开关，请稍候…（0/"+rows.size()+"）");
        EXECUTOR.execute(() -> {
            Map<String, JSONObject> states = readAll();
            ui(() -> {
                apply(states); busy=false; controls();
                int valid=0; for (Row row:rows.values()) if(row.enabled!=null)valid++;
                progress.setText("已读取 " + valid + "/" + rows.size() + " 项 · "
                    + android.text.format.DateFormat.format("HH:mm:ss", System.currentTimeMillis()));
            });
        });
    }
    private Map<String,JSONObject> readAll() {
        Map<String,JSONObject> states=new LinkedHashMap<>();
        int completed=0;
        for(String group:NativeAdRules.groups()) {
            final int current=completed;
            final String title=NativeAdRules.group(group).get(0).title;
            ui(() -> progress.setText("正在读取广告开关，请稍候…（"+current+"/"+rows.size()+"）\n"+title));
            try { merge(states,NativeAdController.read(activity.getApplicationContext(),group)); }
            catch(Exception error) { for(NativeAdRules.Entry e:NativeAdRules.group(group)) states.put(e.id,failure(e.id,error)); }
            completed+=NativeAdRules.group(group).size();
        }
        return states;
    }
    private void save(NativeAdRules.Entry e, boolean enabled) {
        busy=true; controls(); progress.setText("正在保存：" + e.title);
        EXECUTOR.execute(() -> {
            Map<String,JSONObject> states=new LinkedHashMap<>();
            try { merge(states,NativeAdController.write(activity.getApplicationContext(),e.id,enabled)); }
            catch(Exception error) { states.put(e.id,failure(e.id,error)); }
            ui(() -> {
                apply(states); busy=false; controls(); Row row=rows.get(e.id);
                boolean ok=row.enabled!=null && row.enabled==enabled;
                progress.setText(ok ? e.title+" · 已"+(enabled?"开启":"关闭")+"，回读确认成功" : e.title+" · "+row.error);
                notice.show(ok,ok?"已保存并核对官方开关":row.error);
            });
        });
    }
    private void closeAll() {
        if(busy)return;
        busy=true;controls();progress.setText("正在读取并关闭已支持广告开关…");
        EXECUTOR.execute(() -> {
            Map<String,JSONObject> states=readAll(); int changed=0, already=0;
            List<String> failed=new ArrayList<>();
            for(NativeAdRules.Entry e:NativeAdRules.ENTRIES) {
                JSONObject before=states.get(e.id);
                if(before==null || !before.optBoolean("ok") || !(before.opt("enabled") instanceof Boolean)) { failed.add(e.title);continue; }
                if(!before.optBoolean("enabled")) { already++;continue; }
                if(e.kind==NativeAdRules.ACCOUNT && !before.optBoolean("writable")) { failed.add(e.title+"（请打开官方设置）");continue; }
                ui(() -> progress.setText("正在关闭："+e.title));
                try { merge(states,NativeAdController.write(activity.getApplicationContext(),e.id,false)); }
                catch(Exception error){states.put(e.id,failure(e.id,error));}
                JSONObject after=states.get(e.id);
                if(after!=null && after.optBoolean("ok") && after.opt("enabled") instanceof Boolean && !after.optBoolean("enabled"))changed++;
                else failed.add(e.title);
            }
            final String summary="已关闭 "+changed+" 项，原已关闭 "+already+" 项，未处理 "+failed.size()+" 项";
            ui(() -> {
                apply(states);busy=false;controls();
                progress.setText(summary+(failed.isEmpty()?"":"\n未处理："+android.text.TextUtils.join("、",failed)));
                notice.show(failed.isEmpty(),summary);
            });
        });
    }
    private static void merge(Map<String,JSONObject> states,JSONObject result)throws Exception{
        JSONArray rows=result.getJSONArray("results");
        for(int i=0;i<rows.length();i++){JSONObject row=rows.getJSONObject(i);NativeAdRules.find(row.getString("id"));states.put(row.getString("id"),row);}
    }
    private void apply(Map<String,JSONObject> states){
        updating=true;
        try{for(Map.Entry<String,JSONObject> entry:states.entrySet()){
            Row row=rows.get(entry.getKey()); if(row==null)continue; JSONObject value=entry.getValue();
            if(value.optBoolean("ok") && value.opt("enabled") instanceof Boolean){
                row.enabled=value.optBoolean("enabled");row.error=null;row.toggle.setChecked(row.enabled);
                NativeAdRules.Entry definition=NativeAdRules.find(entry.getKey());
                boolean writable=definition.kind!=NativeAdRules.ACCOUNT || value.optBoolean("writable");
                row.state.setOnClickListener(writable?null:v->AccountAdClient.openOfficial(activity));
                row.state.setClickable(!writable);
                row.state.setTextColor(writable?Color.rgb(102,112,106):Color.rgb(202,88,27));
                row.toggle.setVisibility(writable ? View.VISIBLE : View.GONE);
                row.state.setText(writable ? "官方状态："+(row.enabled?"开启":"关闭")
                    : definition.title+" · "+(row.enabled?"开启":"关闭")+" · 打开官方设置 ↗");
            }else{
                row.enabled=null;row.error=value.optString("error","未能读取");row.toggle.setVisibility(View.GONE);
                NativeAdRules.Entry definition=NativeAdRules.find(entry.getKey());
                row.state.setText(definition.title+" · "+row.error+(definition.kind==NativeAdRules.ACCOUNT?" · 打开官方设置 ↗":""));
                if(definition.kind==NativeAdRules.ACCOUNT){row.state.setTextColor(Color.rgb(202,88,27));row.state.setClickable(true);row.state.setOnClickListener(v->AccountAdClient.openOfficial(activity));}
            }
        }}finally{updating=false;}
    }
    private void controls(){
        spinner.setVisibility(busy?View.VISIBLE:View.GONE);
        OfficialAdCounts counts = new OfficialAdCounts();
        for (Row row : rows.values()) counts.include(row.enabled);
        updating = true;
        try { close.setChecked(!busy && counts.allClosed()); } finally { updating = false; }
        close.setEnabled(!busy && !counts.allClosed());
        closeState.setText(busy ? "正在读取或保存，完成后回读确认…" : counts.summary());
        if (android.os.Build.VERSION.SDK_INT >= 30) close.setStateDescription(closeState.getText());
        refresh.setEnabled(!busy);
        for(Row row:rows.values()){row.toggle.setEnabled(!busy && row.enabled!=null);row.state.setEnabled(!busy);}
    }
    private void ui(Runnable action){activity.runOnUiThread(() -> {if(!activity.isFinishing()&&!activity.isDestroyed())action.run();});}
    private static JSONObject failure(String id,Exception error){
        try{return new JSONObject().put("id",id).put("ok",false).put("error",error.getMessage()==null?"操作未成功":error.getMessage());}
        catch(Exception impossible){throw new IllegalStateException(impossible);}
    }
    private TextView text(String value,int size){TextView v=new TextView(activity);v.setText(value);v.setTextSize(size);
        v.setTextColor(Color.rgb(102,112,106));v.setPadding(0,gap(4),0,gap(8));root.addView(v);return v;}
    private int gap(int value){return UiSpacing.vertical(activity,value);}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
