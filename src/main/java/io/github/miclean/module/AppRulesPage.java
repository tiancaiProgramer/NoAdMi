package io.github.miclean.module;

import android.app.*;
import android.content.SharedPreferences;
import android.content.pm.*;
import android.graphics.Color;
import android.os.*;
import android.text.*;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.concurrent.*;
import io.github.miclean.module.skip.*;
import li.gkd.app.subscription.RawSubscription;

/** Package queries, searching and sorting run off the UI thread; ListView recycles visible rows. */
final class AppRulesPage implements AutoCloseable {
    static final ExecutorService loader=Executors.newFixedThreadPool(2,r->new Thread(r,"NoAdMi-app-list"));
    static final class State { String query="",document="";int position,offset;long loadedAt;List<AppRuleListModel.Entry> cached; }
    private final Activity activity;private final SkipRepository repository;private final long id;private final State state;
    private final java.util.function.Consumer<String> openGroups;
    private final Handler main=new Handler(Looper.getMainLooper());private final SharedPreferences prefs;
    private final ListView list;private final TextView status,sortButton,groupButton,filterButton,empty;private final EditText search;
    private final Rows adapter=new Rows();private final Map<Long,String> keys=new HashMap<>();private final Map<String,Long> ids=new HashMap<>();
    private final LatestToggleWrites writes;
    private List<AppRuleListModel.Entry> catalog=Collections.emptyList();private Future<?> loadTask,filterTask;
    private int mask,order,generation;private boolean onlyWhitelist,closed,ready,restoreScroll=true;
    private final Runnable searchTask=()->filter(true);
    AppRulesPage(Activity activity,SkipRepository repository,long id,LinearLayout controls,LinearLayout root,State state,java.util.function.Consumer<String> openGroups){
        this.activity=activity;this.repository=repository;this.id=id;this.state=state;this.openGroups=openGroups;
        prefs=activity.getSharedPreferences("smart_skip_list",0);mask=prefs.getInt("groups",7)&7;order=prefs.getInt("order",0);if(order<0||order>2)order=0;onlyWhitelist=prefs.getBoolean("whitelist",false);
        writes=new LatestToggleWrites(new LatestToggleWrites.Store(){public boolean read(long key){return !Boolean.FALSE.equals(repository.getSources().find(id).getApps().get(keys.get(key)));}public void write(long key,boolean value){repository.getSources().enableApp(id,keys.get(key),value);}},SmartSkipActivity.ruleWriter,r->main.post(r));
        search=new EditText(activity);search.setTag("app_rules_search");search.setSingleLine(true);search.setTextSize(14);search.setHint("搜索名称或包名");DialogUi.input(search);search.setText(state.query);controls.addView(search,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout tools=new LinearLayout(activity);LinearLayout.LayoutParams toolsParams=new LinearLayout.LayoutParams(-1,-2);toolsParams.topMargin=gap(12);controls.addView(tools,toolsParams);
        sortButton=tool(tools,"排序",this::sortDialog);groupButton=tool(tools,"展示分组",this::groupDialog);filterButton=tool(tools,"筛选",this::filterDialog);
        status=new TextView(activity);status.setTag("app_rules_status");status.setTextColor(DialogUi.MUTED);status.setTextSize(12);status.setPadding(0,gap(6),0,gap(8));status.setText("正在读取应用信息…");controls.addView(status);
        list=new ListView(activity);list.setTag("app_rules_list");list.setDivider(null);list.setClipToPadding(false);list.setPadding(0,gap(8),0,gap(8));list.setAdapter(adapter);root.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        empty=new TextView(activity);empty.setText("正在读取应用信息…");empty.setTextColor(DialogUi.MUTED);empty.setGravity(Gravity.CENTER);root.addView(empty,new LinearLayout.LayoutParams(-1,0,1));list.setEmptyView(empty);
        writes.observe((key,error)->{if(!alive())return;if(error!=null)Toast.makeText(activity,"保存失败："+error.getMessage(),Toast.LENGTH_LONG).show();syncRow(keys.get(key));if(onlyWhitelist)filter(false);});
        search.addTextChangedListener(new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void afterTextChanged(Editable e){}public void onTextChanged(CharSequence s,int st,int before,int count){restoreScroll=false;main.removeCallbacks(searchTask);main.postDelayed(searchTask,160);}});
        updateTools();load();
    }
    private boolean alive(){return !closed&&!activity.isFinishing()&&!activity.isDestroyed();}
    private void load(){
        loadTask=loader.submit(()->{try{
            SkipSource source=repository.getSources().find(id);Map<String,Long> visits=repository.recentVisits(),triggers=repository.recentTriggers(id);
            List<AppRuleListModel.Entry> data=new ArrayList<>();
            final boolean reused=state.cached!=null&&state.document.equals(source.getDocument())&&SystemClock.elapsedRealtime()-state.loadedAt<30000;
            if(reused){
                for(AppRuleListModel.Entry entry:state.cached)data.add(new AppRuleListModel.Entry(entry.id,entry.name,entry.type,entry.groups,visits.getOrDefault(entry.id,0L),triggers.getOrDefault(entry.id,0L)));
            }else{
            PackageManager pm=activity.getPackageManager();Map<String,ApplicationInfo> installed=new HashMap<>();try{for(ApplicationInfo info:pm.getInstalledApplications(0))installed.put(info.packageName,info);}catch(SecurityException restricted){/* Direct queries below may still be permitted. */}
            for(RawSubscription.RawApp app:source.getSubscription().getApps()){
                if(Thread.currentThread().isInterrupted())return;ApplicationInfo info=installed.get(app.getId());
                // Some HyperOS builds return only the caller in bulk queries even when
                // direct lookups are permitted. Absence from that list is not uninstall evidence.
                if(info==null)try{info=pm.getApplicationInfo(app.getId(),0);}catch(PackageManager.NameNotFoundException ignored){}
                String name=app.getName()==null?app.getId():app.getName();int type=AppRuleListModel.UNINSTALLED;
                if(info!=null){type=(info.flags&(ApplicationInfo.FLAG_SYSTEM|ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))!=0?AppRuleListModel.SYSTEM:AppRuleListModel.USER;try{name=info.loadLabel(pm).toString();}catch(RuntimeException ignored){}}
                data.add(new AppRuleListModel.Entry(app.getId(),name,type,app.getGroups().size(),visits.getOrDefault(app.getId(),0L),triggers.getOrDefault(app.getId(),0L)));
            }
            }
            main.post(()->{if(!alive())return;state.cached=data;state.document=source.getDocument();if(!reused)state.loadedAt=SystemClock.elapsedRealtime();catalog=data;long key=0;for(AppRuleListModel.Entry entry:data){keys.put(key,entry.id);ids.put(entry.id,key++);}ready=true;filter(false);});
        }catch(Exception error){main.post(()->{if(alive())status.setText("读取失败，返回后可重试："+error.getMessage());});}});
    }
    private void filter(boolean reset){
        if(!ready||!alive())return;if(reset)restoreScroll=false;final int token=++generation,selectedMask=mask,selectedOrder=order;final boolean white=onlyWhitelist;final String query=search.getText().toString();
        Set<String> whitelist=new HashSet<>();for(AppRuleListModel.Entry entry:catalog)if(!writes.value(ids.get(entry.id)))whitelist.add(entry.id);
        if(filterTask!=null)filterTask.cancel(true);status.setText("正在整理应用列表…");
        filterTask=loader.submit(()->{List<AppRuleListModel.Entry> result=AppRuleListModel.filter(catalog,query,selectedMask,selectedOrder,white,whitelist);main.post(()->{if(!alive()||token!=generation)return;empty.setText("没有符合条件的应用");adapter.data=result;adapter.notifyDataSetChanged();if(reset)list.setSelection(0);status.setText(result.size()+" / "+catalog.size()+" 个应用"+(selectedOrder==1?" · 使用时间来自启用智能跳过后的本机前台记录":selectedOrder==2?" · 触发时间来自本订阅的执行记录":"")+(white?" · 白名单：已关闭该订阅规则的应用":""));if(restoreScroll){list.setSelectionFromTop(state.position,state.offset);restoreScroll=false;}});});
    }
    private void save(){prefs.edit().putInt("groups",mask).putInt("order",order).putBoolean("whitelist",onlyWhitelist).apply();updateTools();filter(true);}
    private void updateTools(){sortButton.setText(new String[]{"按名称 ▾","最近使用 ▾","最近触发 ▾"}[order]);groupButton.setText("展示分组 "+Integer.bitCount(mask)+"/3 ▾");filterButton.setText(onlyWhitelist?"只看白名单 ▾":"筛选 ▾");}
    private void sortDialog(){DialogUi.builder(activity).setTitle("排序").setSingleChoiceItems(new String[]{"按名称","最近使用","最近触发"},order,(d,w)->{order=w;save();d.dismiss();}).setNegativeButton("取消",null).show();}
    private void groupDialog(){final int[] selection={mask};DialogUi.builder(activity).setTitle("展示分组").setMultiChoiceItems(new String[]{"系统应用","用户应用","未安装应用"},new boolean[]{(mask&1)!=0,(mask&2)!=0,(mask&4)!=0},(d,w,checked)->{if(checked)selection[0]|=1<<w;else selection[0]&=~(1<<w);}).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{mask=selection[0];save();}).show();}
    private void filterDialog(){final boolean[] selected={onlyWhitelist};LinearLayout form=DialogUi.content(activity);DialogUi.description(form,"白名单指已关闭当前订阅规则的应用。筛选只控制展示，不修改任何规则开关。");Switch toggle=new Switch(activity);toggle.setText("只看白名单");SettingsRow.style(activity,toggle);toggle.setChecked(onlyWhitelist);toggle.setOnCheckedChangeListener((v,value)->selected[0]=value);form.addView(toggle);DialogUi.builder(activity).setTitle("筛选").setView(form).setNegativeButton("取消",null).setPositiveButton("确定",(d,w)->{onlyWhitelist=selected[0];save();}).show();}
    private TextView tool(LinearLayout parent,String title,Runnable run){LinearLayout box=new LinearLayout(activity);box.setOrientation(1);LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(0,-2,1);params.leftMargin=parent.getChildCount()==0?0:gap(12);parent.addView(box,params);TextView t=DialogUi.choice(box,title,run);t.setTextSize(12);t.setPadding(dp(5),gap(10),dp(5),gap(10));t.setGravity(Gravity.CENTER);return t;}
    private void syncRow(String pkg){for(int i=0;i<list.getChildCount();i++){View row=list.getChildAt(i);Holder h=(Holder)row.getTag();if(pkg.equals(h.pkg)){h.toggle.setOnCheckedChangeListener(null);h.toggle.setChecked(writes.value(ids.get(pkg)));h.toggle.setOnCheckedChangeListener((b,value)->writes.request(ids.get(pkg),value));break;}}}
    private final class Holder {String pkg;Switch toggle;TextView meta,link;}
    private final class Rows extends BaseAdapter {
        List<AppRuleListModel.Entry> data=Collections.emptyList();public int getCount(){return data.size();}public Object getItem(int pos){return data.get(pos);}public long getItemId(int pos){return ids.get(data.get(pos).id);}public boolean hasStableIds(){return true;}
        public View getView(int pos,View recycled,ViewGroup parent){LinearLayout box;Holder h;if(recycled==null){box=new LinearLayout(activity);box.setOrientation(1);box.setPadding(dp(14),gap(4),dp(14),gap(4));box.setBackgroundColor(Color.WHITE);h=new Holder();h.toggle=new Switch(activity);SettingsRow.style(activity,h.toggle);box.addView(h.toggle,new LinearLayout.LayoutParams(-1,-2));h.meta=new TextView(activity);h.meta.setTextSize(12);h.meta.setTextColor(DialogUi.MUTED);box.addView(h.meta);h.link=DialogUi.choice(box,"规则组  ›",()->{});h.link.setMinHeight(dp(40));box.setTag(h);}else{box=(LinearLayout)recycled;h=(Holder)box.getTag();}
            AppRuleListModel.Entry e=data.get(pos);h.pkg=e.id;h.toggle.setTag("app_rule_switch:"+e.id);h.toggle.setOnCheckedChangeListener(null);h.toggle.setText(e.name);h.toggle.setChecked(writes.value(ids.get(e.id)));h.toggle.setOnCheckedChangeListener((button,value)->writes.request(ids.get(e.id),value));h.meta.setText(e.id+"\n"+(e.type==1?"系统应用":e.type==2?"用户应用":"未安装应用")+" · "+e.groups+" 个规则组");h.link.setOnClickListener(v->openGroups.accept(e.id));return box;
        }
    }
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}private int gap(int value){return UiSpacing.vertical(activity,value);}
    @Override public void close(){state.query=search.getText().toString();state.position=list.getFirstVisiblePosition();state.offset=list.getChildCount()==0?0:list.getChildAt(0).getTop();closed=true;++generation;main.removeCallbacksAndMessages(null);if(loadTask!=null)loadTask.cancel(true);if(filterTask!=null)filterTask.cancel(true);}
}
