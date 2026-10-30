package io.github.miclean.module;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.Future;
import java.util.function.Consumer;
import io.github.miclean.module.skip.*;

/** Installed-app management is independent of each subscription's declared-app list. */
final class AppManagementPage implements AutoCloseable {
    static final class State {String query="";int position,offset;}
    private final Activity activity;private final SkipRepository repository;private final State state;
    private final Consumer<ManagedAppCatalog.App> open;private final SharedPreferences prefs;
    private final Handler main=new Handler(Looper.getMainLooper());private final LatestToggleWrites writes;
    private final Map<Long,String> keys=new HashMap<>();private final Map<String,Long> ids=new HashMap<>();
    private final Map<String,ManagedAppCatalog.App> apps=new HashMap<>();private final Map<String,Integer> globals=new HashMap<>();
    private final ListView list;private final EditText search;private final TextView status,empty,sortButton,groupButton,filterButton;
    private List<AppRuleListModel.Entry> catalog=Collections.emptyList();private final Rows rows=new Rows();
    private Future<?> loadTask,filterTask;private int mask,order,generation;private boolean onlyWhitelist,closed,ready,restoreScroll=true;
    private final Runnable searchTask=()->filter(true);
    private final SharedPreferences.OnSharedPreferenceChangeListener listener=(p,k)->{if(SkipRepository.GLOBAL_WHITELIST.equals(k)&&alive()){syncRows();if(onlyWhitelist)filter(false);}};
    AppManagementPage(Activity activity,SkipRepository repository,LinearLayout controls,LinearLayout root,State state,Consumer<ManagedAppCatalog.App> open){
        this.activity=activity;this.repository=repository;this.state=state;this.open=open;
        prefs=activity.getSharedPreferences("smart_skip_apps",0);mask=prefs.getInt("groups",3)&3;order=prefs.getInt("order",0);if(order<0||order>2)order=0;onlyWhitelist=prefs.getBoolean("whitelist",false);
        writes=new LatestToggleWrites(new LatestToggleWrites.Store(){public boolean read(long key){return !repository.isWhitelisted(keys.get(key));}public void write(long key,boolean enabled){repository.setWhitelisted(keys.get(key),!enabled);}},SmartSkipActivity.ruleWriter,r->main.post(r));
        search=new EditText(activity);search.setTag("managed_apps_search");search.setSingleLine(true);search.setTextSize(14);search.setHint("搜索名称或包名");DialogUi.input(search);search.setText(state.query);controls.addView(search,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout tools=new LinearLayout(activity);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=gap(12);controls.addView(tools,params);
        sortButton=tool(tools,"排序",this::sortDialog);groupButton=tool(tools,"展示分组",this::groupDialog);filterButton=tool(tools,"筛选",this::filterDialog);
        status=new TextView(activity);status.setTag("managed_apps_status");status.setTextSize(12);status.setTextColor(DialogUi.MUTED);status.setPadding(0,gap(6),0,gap(8));status.setText("正在读取已安装应用…");controls.addView(status);
        list=new ListView(activity);list.setTag("managed_apps_list");list.setDivider(null);list.setClipToPadding(false);list.setPadding(0,gap(8),0,gap(8));list.setAdapter(rows);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        empty=new TextView(activity);empty.setText("正在读取已安装应用…");empty.setTextColor(DialogUi.MUTED);empty.setGravity(Gravity.CENTER);root.addView(empty,new LinearLayout.LayoutParams(-1,0,1));list.setEmptyView(empty);
        writes.observe((key,error)->{if(!alive())return;if(error!=null)Toast.makeText(activity,"保存失败："+error.getMessage(),Toast.LENGTH_LONG).show();syncRows();if(onlyWhitelist)filter(false);});
        repository.getPreferences().registerOnSharedPreferenceChangeListener(listener);
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int start,int before,int count){restoreScroll=false;main.removeCallbacks(searchTask);main.postDelayed(searchTask,160);}});
        updateTools();load();
    }
    private boolean alive(){return !closed&&!activity.isFinishing()&&!activity.isDestroyed();}
    private void load(){loadTask=AppRulesPage.loader.submit(()->{try{
        List<ManagedAppCatalog.App> installed=ManagedAppCatalog.load(activity);List<SkipSource> sources=repository.getSources().list();
        Map<String,Long> visits=repository.recentVisits(),triggers=repository.recentTriggers();List<AppRuleListModel.Entry> data=new ArrayList<>();Map<String,Integer> counts=new HashMap<>();
        for(ManagedAppCatalog.App app:installed){if(Thread.currentThread().isInterrupted())return;int specific=0,global=0;for(SkipManagedGroup group:SkipAppRules.groups(sources,app.id,app.code,app.version,app.system,app.launcher)){if(group.getGlobal())global++;else specific++;}counts.put(app.id,global);data.add(new AppRuleListModel.Entry(app.id,app.name,app.type,specific,visits.getOrDefault(app.id,0L),triggers.getOrDefault(app.id,0L)));}
        main.post(()->{if(!alive())return;long key=0;for(ManagedAppCatalog.App app:installed){apps.put(app.id,app);keys.put(key,app.id);ids.put(app.id,key++);}globals.putAll(counts);catalog=data;ready=true;filter(false);});
    }catch(Exception e){main.post(()->{if(alive()){status.setText("读取失败，返回后可重试："+e.getMessage());empty.setText("无法读取应用信息");}});}});}
    private void filter(boolean reset){
        if(!ready||!alive())return;final int token=++generation,selectedMask=mask,selectedOrder=order;final boolean white=onlyWhitelist;final String query=search.getText().toString();if(reset)restoreScroll=false;
        Set<String> blocked=new HashSet<>();for(AppRuleListModel.Entry e:catalog)if(!writes.value(ids.get(e.id)))blocked.add(e.id);
        if(filterTask!=null)filterTask.cancel(true);
        filterTask=AppRulesPage.loader.submit(()->{List<AppRuleListModel.Entry> result=AppRuleListModel.filter(catalog,query,selectedMask,selectedOrder,white,blocked);main.post(()->{if(!alive()||generation!=token)return;rows.data=result;rows.notifyDataSetChanged();empty.setText("没有符合条件的应用\n可检查搜索、展示分组或只看白名单");status.setText(result.size()+" / "+catalog.size()+" 个已安装应用"+(white?" · 只看全局白名单":""));if(reset)list.setSelection(0);if(restoreScroll){list.setSelectionFromTop(state.position,state.offset);restoreScroll=false;}});});
    }
    private void save(){prefs.edit().putInt("groups",mask).putInt("order",order).putBoolean("whitelist",onlyWhitelist).apply();updateTools();filter(true);}
    private void updateTools(){sortButton.setText(new String[]{"按名称 ▾","最近使用 ▾","最近触发 ▾"}[order]);groupButton.setText("展示分组 "+Integer.bitCount(mask)+"/2 ▾");filterButton.setText(onlyWhitelist?"只看白名单 ▾":"筛选 ▾");}
    private void sortDialog(){DialogUi.builder(activity).setTitle("排序").setSingleChoiceItems(new String[]{"按名称","最近使用","最近触发"},order,(d,w)->{order=w;save();d.dismiss();}).setNegativeButton("取消",null).show();}
    private void groupDialog(){final int[] selected={mask};DialogUi.builder(activity).setTitle("展示分组").setMultiChoiceItems(new String[]{"系统应用","用户应用"},new boolean[]{(mask&1)!=0,(mask&2)!=0},(d,w,value)->{if(value)selected[0]|=1<<w;else selected[0]&=~(1<<w);}).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{mask=selected[0];save();}).show();}
    private void filterDialog(){final boolean[] selected={onlyWhitelist};LinearLayout form=DialogUi.content(activity);DialogUi.description(form,"开启后仅显示全局白名单中的应用。全局白名单会停止所有订阅在这些应用内匹配；各订阅自己的白名单保持独立。");Switch toggle=new Switch(activity);toggle.setText("只看白名单");SettingsRow.style(activity,toggle);toggle.setChecked(onlyWhitelist);toggle.setOnCheckedChangeListener((v,value)->selected[0]=value);form.addView(toggle);DialogUi.builder(activity).setTitle("筛选").setView(form).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{onlyWhitelist=selected[0];save();}).show();}
    private TextView tool(LinearLayout parent,String title,Runnable run){LinearLayout box=new LinearLayout(activity);box.setOrientation(1);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.leftMargin=parent.getChildCount()==0?0:gap(12);parent.addView(box,params);TextView t=DialogUi.choice(box,title,run);t.setTextSize(12);t.setPadding(dp(5),gap(10),dp(5),gap(10));t.setGravity(Gravity.CENTER);return t;}
    private String meta(AppRuleListModel.Entry e){return e.id+"\n"+(!writes.value(ids.get(e.id))?"已加入全局白名单 · 全部订阅停止匹配":e.groups+" 个专属规则组 · "+globals.get(e.id)+" 个适用全局组");}
    private void syncRows(){for(int i=0;i<list.getChildCount();i++){View v=list.getChildAt(i);Holder h=(Holder)v.getTag();boolean enabled=writes.value(ids.get(h.entry.id));if(h.toggle.isChecked()!=enabled){h.toggle.setOnCheckedChangeListener(null);h.toggle.setChecked(enabled);h.toggle.setOnCheckedChangeListener((b,value)->writes.request(ids.get(h.entry.id),value));}h.meta.setText(meta(h.entry));}}
    private static final class Holder {AppRuleListModel.Entry entry;Switch toggle;TextView meta,link;}
    private final class Rows extends BaseAdapter {
        List<AppRuleListModel.Entry> data=Collections.emptyList();public int getCount(){return data.size();}public Object getItem(int p){return data.get(p);}public long getItemId(int p){return ids.get(data.get(p).id);}public boolean hasStableIds(){return true;}
        public View getView(int position,View recycled,ViewGroup parent){LinearLayout box;Holder h;if(recycled==null){box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(dp(14),gap(4),dp(14),gap(4));box.setBackgroundColor(Color.WHITE);h=new Holder();h.toggle=new Switch(activity);SettingsRow.style(activity,h.toggle);box.addView(h.toggle,new LinearLayout.LayoutParams(-1,-2));h.meta=new TextView(activity);h.meta.setTextSize(12);h.meta.setTextColor(DialogUi.MUTED);box.addView(h.meta);h.link=DialogUi.choice(box,"管理此应用规则  ›",()->{});box.setTag(h);}else{box=(LinearLayout)recycled;h=(Holder)box.getTag();}
            AppRuleListModel.Entry e=data.get(position);h.entry=e;h.toggle.setTag("managed_app_switch:"+e.id);h.toggle.setOnCheckedChangeListener(null);h.toggle.setText(e.name);h.toggle.setChecked(writes.value(ids.get(e.id)));h.toggle.setOnCheckedChangeListener((b,value)->writes.request(ids.get(e.id),value));h.meta.setText(meta(e));h.link.setOnClickListener(v->open.accept(apps.get(e.id)));return box;}
    }
    private int dp(int v){return Math.round(v*activity.getResources().getDisplayMetrics().density);}private int gap(int v){return UiSpacing.vertical(activity,v);}
    @Override public void close(){state.query=search.getText().toString();state.position=list.getFirstVisiblePosition();state.offset=list.getChildCount()==0?0:list.getChildAt(0).getTop();closed=true;++generation;repository.getPreferences().unregisterOnSharedPreferenceChangeListener(listener);main.removeCallbacksAndMessages(null);if(loadTask!=null)loadTask.cancel(true);if(filterTask!=null)filterTask.cancel(true);}
}
