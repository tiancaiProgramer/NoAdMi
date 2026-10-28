package io.github.miclean.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import io.github.miclean.module.skip.*;

/** Inline controls with three initially collapsed groups; operations are Activity-owned. */
final class SmartSkipPanel implements AutoCloseable {
    private static final int INK=Color.rgb(36,44,40),MUTED=Color.rgb(102,112,106),ORANGE=Color.rgb(202,88,27);
    private final Activity activity;
    private final SkipRepository repository;
    private final SharedPreferences ui;
    private final SubscriptionActions actions;
    private final Switch enabled,service,autoUpdate,mobileUpdate,notice;
    private final LinearLayout sources,logs;
    private final TextView method,progress,update,details;
    private final AccessibilityAccess access;
    private final LatestToggleWrites sourceWrites;
    private final Map<Long,Switch> sourceRows=new LinkedHashMap<>();
    private final Map<Long,String> sourceDocuments=new LinkedHashMap<>();
    private final LatestToggleWrites.Listener sourceListener=this::sourceSaved;
    private TextView emptySources;
    private AlertDialog noticeDialog,methodDialog,inputRouteDialog;
    private boolean syncingSources;
    private final Handler handler=new Handler(Looper.getMainLooper());
    private boolean updating,closed;
    private int polls;
    SmartSkipPanel(Activity activity,LinearLayout parent,SubscriptionActions actions){
        this.activity=activity;this.actions=actions;repository=SkipRepository.get(activity);sourceWrites=SubscriptionToggles.get(repository);ui=activity.getSharedPreferences("miclean_ui",0);
        enabled=toggle(parent,"启用");enabled.setContentDescription("启用智能跳过");
        LinearLayout subscriptions=fold(parent,"skip_compact_sources","订阅管理",null);
        autoUpdate=toggle(subscriptions,"自动更新订阅");mobileUpdate=toggle(subscriptions,"移动网络更新");
        autoUpdate.setOnCheckedChangeListener((button,value)->{if(!updating){repository.getPreferences().edit().putBoolean("auto_update",value).apply();repository.schedule();}});
        mobileUpdate.setOnCheckedChangeListener((button,value)->{if(!updating){repository.getPreferences().edit().putBoolean("wifi_only",!value).apply();repository.schedule();}});
        sources=vertical();sources.setTag("skip_source_list");subscriptions.addView(sources,new LinearLayout.LayoutParams(-1,-2));
        progress=text(subscriptions,"",12,ORANGE,false);
        details=action(subscriptions,"详情与规则  ›",()->activity.startActivity(new Intent(activity,SmartSkipActivity.class).putExtra(SmartSkipActivity.SOURCE_LIST,true)));
        // Keep these three actions last, even when the dynamic source list is refreshed.
        action(subscriptions,"添加订阅链接  ›",actions::addLink);
        action(subscriptions,"导入本地规则  ›",actions::importLocal);
        update=action(subscriptions,"立即更新订阅",actions::updateAll);
        LinearLayout settings=fold(parent,"skip_compact_settings","设置",null);
        service=toggle(settings,"无障碍服务");
        LinearLayout methodRow=new LinearLayout(activity);methodRow.setTag("skip_access_method");methodRow.setGravity(Gravity.CENTER_VERTICAL);methodRow.setMinimumHeight(dp(52));methodRow.setPadding(0,gap(12),0,gap(12));
        TextView title=new TextView(activity);title.setText("无障碍方式");title.setTextColor(INK);title.setTextSize(14);title.setGravity(Gravity.CENTER_VERTICAL);methodRow.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        method=new TextView(activity);method.setTextSize(14);method.setTextColor(MUTED);method.setGravity(Gravity.END|Gravity.CENTER_VERTICAL);methodRow.addView(method,new LinearLayout.LayoutParams(-2,-2));settings.addView(methodRow,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout noticeRow=new LinearLayout(activity);noticeRow.setTag("skip_notice_row");noticeRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView noticeTitle=new TextView(activity);noticeTitle.setTag("skip_notice_editor");noticeTitle.setText("跳过提示  ›");noticeTitle.setTextSize(14);noticeTitle.setTextColor(INK);noticeTitle.setMinHeight(dp(52));noticeTitle.setGravity(Gravity.CENTER_VERTICAL);noticeRow.addView(noticeTitle,new LinearLayout.LayoutParams(0,-2,1));
        notice=new Switch(activity);notice.setTag("skip_notice_enabled");notice.setContentDescription("跳过提示开关");SettingsRow.style(activity,notice);noticeRow.addView(notice,new LinearLayout.LayoutParams(-2,-2));settings.addView(noticeRow,new LinearLayout.LayoutParams(-1,-2));
        noticeTitle.setOnClickListener(v->editNotice());notice.setOnCheckedChangeListener((button,value)->{if(!updating)repository.getPreferences().edit().putBoolean(SkipNotice.ENABLED,value).apply();});
        access=new AccessibilityAccess(activity,this::refreshControls);
        methodRow.setOnClickListener(v->{if(access.busy())return;methodDialog=DialogUi.builder(activity).setTitle("无障碍方式").setSingleChoiceItems(new String[]{"Root","Shizuku"},access.shizukuMode()?1:0,(dialog,which)->{dialog.dismiss();access.method(which==1);if(which==1)access.authorize();refreshControls();}).setNeutralButton("输入对照",(dialog,which)->editInputRoute()).setNegativeButton("取消",null).show();});
        service.setOnCheckedChangeListener((button,value)->{if(!updating){polls=0;access.setEnabled(value);}});
        enabled.setOnCheckedChangeListener((button,value)->{if(!updating)repository.setEnabled(value);});
        logs=fold(parent,"skip_compact_logs","执行记录",this::renderLogs);
        sourceWrites.observe(sourceListener);refresh();
    }
    void refresh(){if(closed)return;refreshControls();renderSources();if(logs.isShown())renderLogs();}
    private void refreshControls(){
        if(closed)return;updating=true;
        enabled.setChecked(repository.getEnabled());notice.setChecked(repository.getPreferences().getBoolean(SkipNotice.ENABLED,false));autoUpdate.setChecked(repository.getPreferences().getBoolean("auto_update",false));mobileUpdate.setChecked(!repository.getPreferences().getBoolean("wifi_only",true));
        boolean active=repository.serviceEnabled(),busy=access.busy();service.setChecked(active);service.setEnabled(!busy);
        String state=busy?"正在修改系统设置…":!active?"服务未启用":SkipAccessibilityService.connected?"服务已连接":"等待系统连接…";
        if(android.os.Build.VERSION.SDK_INT>=30)service.setStateDescription(state);
        service.setText(busy?"无障碍服务\n正在修改系统设置…":access.error()!=null?"无障碍服务\n"+access.error():active&&!SkipAccessibilityService.connected?"无障碍服务\n"+(polls>=10?"服务未连接，请关闭后重新开启":"等待系统连接…"):access.shizukuMode()&&!SkipRuntimeClient.status.isEmpty()?"无障碍服务\n"+SkipRuntimeClient.status:"无障碍服务");
        method.setText((access.shizukuMode()?"Shizuku":"Root")+"  ›");updating=false;
        handler.removeCallbacks(checkConnection);if(active&&((!SkipAccessibilityService.connected&&polls++<10)||access.shizukuMode()))handler.postDelayed(checkConnection,700);
    }
    private void editInputRoute(){
        if(closed||activity.isFinishing()||inputRouteDialog!=null&&inputRouteDialog.isShowing())return;
        int mode;try{mode=SkipInputMode.normalize(repository.getPreferences().getInt(SkipInputMode.KEY,SkipInputMode.COMPAT));}catch(ClassCastException invalid){mode=SkipInputMode.COMPAT;}
        inputRouteDialog=DialogUi.builder(activity).setTitle("增强输入对照（Shizuku）")
            .setSingleChoiceItems(new String[]{"当前兼容输入（默认）","GKD 命令输入（对照）","GKD 虚拟触屏（对照）","无障碍手势（保留前台识别）"},mode,(dialog,which)->{
                repository.getPreferences().edit().putInt(SkipInputMode.KEY,SkipInputMode.normalize(which)).apply();dialog.dismiss();
            }).setNegativeButton("取消",null).show();
    }
    private void editNotice(){
        if(noticeDialog!=null&&noticeDialog.isShowing())return;
        LinearLayout form=DialogUi.content(activity);DialogUi.description(form,"点击或手势收到成功回调后显示提示，不代表页面一定已关闭。留空恢复默认文案。");
        EditText input=new EditText(activity);input.setTag("skip_notice_text");input.setTextSize(14);input.setSingleLine(true);input.setHint(SkipNotice.DEFAULT_TEXT);input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(SkipNotice.MAX_LENGTH)});DialogUi.input(input);input.setText(SkipNotice.text(repository.getPreferences().getString(SkipNotice.TEXT,null)));form.addView(input,new LinearLayout.LayoutParams(-1,-2));
        noticeDialog=DialogUi.builder(activity).setTitle("跳过提示").setView(form).setNegativeButton("取消",null).setPositiveButton("保存",(dialog,which)->repository.getPreferences().edit().putString(SkipNotice.TEXT,SkipNotice.text(input.getText().toString())).apply()).show();
    }
    private final Runnable checkConnection=()->refreshControls();
    private void renderSources(){
        List<SkipSource> list=repository.getSources().list();
        progress.setText(actions.busy()?"正在处理，请稍候…":"");progress.setVisibility(actions.busy()?View.VISIBLE:View.GONE);
        update.setEnabled(!actions.busy()&&!sourceWrites.anyPending()&&list.stream().anyMatch(s->!s.getLocal()&&s.getEnabled()));update.setAlpha(update.isEnabled()?1f:.45f);
        details.setEnabled(!list.isEmpty());details.setAlpha(details.isEnabled()?1f:.45f);
        Set<Long> present=new HashSet<>();for(SkipSource source:list)present.add(source.getId());
        java.util.Iterator<Map.Entry<Long,Switch>> iterator=sourceRows.entrySet().iterator();while(iterator.hasNext()){Map.Entry<Long,Switch> row=iterator.next();if(!present.contains(row.getKey())){sources.removeView(row.getValue());sourceDocuments.remove(row.getKey());iterator.remove();}}
        if(list.isEmpty()){if(emptySources==null)emptySources=text(sources,"暂无订阅，添加后可分别启用。",12,MUTED,false);else if(emptySources.getParent()==null)sources.addView(emptySources);return;}
        if(emptySources!=null&&emptySources.getParent()!=null)sources.removeView(emptySources);
        syncingSources=true;
        try{for(int index=0;index<list.size();index++){
            SkipSource source=list.get(index);long id=source.getId();Switch toggle=sourceRows.get(id);
            if(toggle==null){toggle=toggle(sources,source.getName());toggle.setTag("skip_source_toggle:"+id);sourceRows.put(id,toggle);sourceDocuments.put(id,source.getDocument());
                toggle.setOnCheckedChangeListener((button,value)->{if(!syncingSources&&!closed){sourceWrites.request(id,value);renderSources();}});
            }
            // Reorder only when subscriptions actually change, never on a value-only save.
            if(sources.getChildAt(index)!=toggle){sources.removeView(toggle);sources.addView(toggle,index);}
            if(!source.getDocument().equals(sourceDocuments.get(id))){toggle.setText(source.getName());sourceDocuments.put(id,source.getDocument());}
            boolean value=sourceWrites.value(id);if(toggle.isChecked()!=value)toggle.setChecked(value);
            if(android.os.Build.VERSION.SDK_INT>=30)toggle.setStateDescription(sourceWrites.pending(id)?"正在保存，以最后一次选择为准":value?"已启用":"已停用");
        }}finally{syncingSources=false;}
    }
    private void sourceSaved(long id,Exception error){
        if(closed)return;renderSources();
        if(error!=null&&sourceRows.containsKey(id))DialogUi.builder(activity).setTitle("保存失败").setMessage("已恢复实际保存的状态，请重试。\n"+(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage())).setPositiveButton("知道了",null).show();
    }
    private void renderLogs(){
        if(closed)return;logs.removeAllViews();List<SkipLog> records=repository.logs();
        if(records.isEmpty()){text(logs,"暂无执行记录",13,MUTED,false);return;}
        text(logs,"记录表示点击或手势调用已受理，实际效果以页面变化为准。",12,MUTED,false);
        action(logs,"清空执行记录",()->DialogUi.builder(activity).setTitle("清空执行记录？").setMessage("仅清除本机记录，订阅与开关保持原样。").setNegativeButton("取消",null).setPositiveButton("清空",(d,w)->{repository.clearLogs();renderLogs();}).show());
        for(SkipLog record:records){text(logs,record.getGroup()+" · "+record.getResult(),14,INK,true);text(logs,record.getApp()+"\n"+record.getSubscription()+" · "+record.getRule()+"\n"+DateFormat.getDateTimeInstance().format(new Date(record.getTime())),12,MUTED,false);}
    }
    private LinearLayout fold(LinearLayout parent,String key,String title,Runnable opened){
        LinearLayout group=vertical();group.setTag("group:"+key);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=gap(12);parent.addView(group,params);
        LinearLayout header=new LinearLayout(activity);header.setTag("fold:"+key);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(12),gap(12),dp(12),gap(12));header.setMinimumHeight(dp(52));header.setFocusable(true);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(237,242,232));bg.setCornerRadius(dp(10));bg.setStroke(dp(1),Color.rgb(219,228,212));header.setBackground(bg);
        TextView name=new TextView(activity);name.setText(title);name.setTextSize(15);name.setTextColor(INK);name.setTypeface(null,Typeface.BOLD);name.setGravity(Gravity.CENTER_VERTICAL);header.addView(name,new LinearLayout.LayoutParams(0,-2,1));
        TextView arrow=new TextView(activity);arrow.setText("›");arrow.setTextSize(22);arrow.setTextColor(MUTED);arrow.setGravity(Gravity.CENTER);header.addView(arrow,new LinearLayout.LayoutParams(dp(22),-2));group.addView(header,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout area=new LinearLayout(activity);area.setPadding(dp(10),gap(8),0,gap(4));View guide=new View(activity);guide.setBackgroundColor(Color.rgb(211,223,202));area.addView(guide,new LinearLayout.LayoutParams(dp(2),-1));
        LinearLayout body=vertical();body.setTag("body:"+key);LinearLayout.LayoutParams bodyParams=new LinearLayout.LayoutParams(0,-2,1);bodyParams.leftMargin=dp(10);area.addView(body,bodyParams);group.addView(area,new LinearLayout.LayoutParams(-1,-2));
        Runnable sync=()->{boolean open=ui.getBoolean(key,false);area.setVisibility(open?View.VISIBLE:View.GONE);body.setVisibility(open?View.VISIBLE:View.GONE);arrow.setRotation(open?90:0);header.setContentDescription(title+"，"+(open?"点击收起":"点击展开"));};sync.run();
        header.setOnClickListener(v->{boolean open=!ui.getBoolean(key,false);ui.edit().putBoolean(key,open).apply();sync.run();if(open&&opened!=null)opened.run();});return body;
    }
    private Switch toggle(LinearLayout parent,String title){Switch view=new Switch(activity);view.setText(title);SettingsRow.style(activity,view);parent.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView view=new TextView(activity);view.setText(value);view.setTextSize(size);view.setTextColor(color);view.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);if(bold)view.setTypeface(null,Typeface.BOLD);view.setPadding(0,gap(6),0,gap(6));parent.addView(view,new LinearLayout.LayoutParams(-1,-2));return view;}
    private TextView action(LinearLayout parent,String title,Runnable action){TextView view=text(parent,title,14,ORANGE,true);view.setMinHeight(dp(48));view.setOnClickListener(v->action.run());return view;}
    private LinearLayout vertical(){LinearLayout layout=new LinearLayout(activity);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private int gap(int value){return UiSpacing.vertical(activity,value);}
    @Override public void close(){closed=true;if(noticeDialog!=null)noticeDialog.dismiss();if(methodDialog!=null)methodDialog.dismiss();if(inputRouteDialog!=null)inputRouteDialog.dismiss();sourceWrites.remove(sourceListener);handler.removeCallbacksAndMessages(null);access.close();}
}
