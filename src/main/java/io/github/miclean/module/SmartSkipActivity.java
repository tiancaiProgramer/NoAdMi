package io.github.miclean.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.widget.*;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Date;
import java.text.DateFormat;
import io.github.miclean.module.skip.*;
import li.gkd.app.subscription.RawSubscription;

/** Subscription detail/rule pages; main controls now live inline in MainActivity. */
public final class SmartSkipActivity extends Activity {
    static final String SOURCE_ID="source_id",SOURCE_LIST="source_list";
    static final java.util.concurrent.ExecutorService ruleWriter=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"NoAdMi-rule-settings"));
    private SkipRepository repository;
    private SubscriptionActions actions;
    private LinearLayout body,root;
    private AppRulesPage appsPage;
    private AppManagementPage managedPage;
    private final AppManagementPage.State managedState=new AppManagementPage.State();
    private int pageGeneration;
    private final java.util.Map<Long,AppRulesPage.State> appStates=new java.util.HashMap<>();
    private TextView progress;
    private Runnable page;
    private final ArrayDeque<Runnable> back=new ArrayDeque<>();
    private final java.util.Set<Long> expandedSources=new java.util.HashSet<>();
    private static final int INK=Color.rgb(36,44,40),MUTED=Color.rgb(102,112,106),ORANGE=Color.rgb(202,88,27);
    @Override public void onCreate(Bundle state){
        super.onCreate(state);repository=SkipRepository.get(this);
        if(!getIntent().hasExtra(SOURCE_ID)&&!getIntent().getBooleanExtra(SOURCE_LIST,false)){finish();return;}
        if(android.os.Build.VERSION.SDK_INT>=33)getOnBackInvokedDispatcher().registerOnBackInvokedCallback(0,this::goBack);
        actions=new SubscriptionActions(this,()->{if(page!=null)page.run();},this::refreshProgress);
        if(getIntent().hasExtra(SOURCE_ID)){long id=getIntent().getLongExtra(SOURCE_ID,0);page=()->details(id);}else page=this::sources;page.run();
    }
    @Override public void onBackPressed(){goBack();}
    @Override public void onDestroy(){if(actions!=null)actions.close();if(appsPage!=null)appsPage.close();if(managedPage!=null)managedPage.close();++pageGeneration;super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(actions!=null)actions.onActivityResult(request,result,data);}
    private void refreshProgress(){if(progress!=null)progress.setText(actions.busy()?"正在处理，请稍候…":"");}
    private void goBack(){if(back.isEmpty())finish();else{page=back.pop();page.run();}}
    private void navigate(Runnable next){back.push(page);page=next;page.run();}
    private void start(String title,String description){start(title,description,false);}
    private void start(String title,String description,boolean listPage){
        ++pageGeneration;if(appsPage!=null){appsPage.close();appsPage=null;}
        if(managedPage!=null){managedPage.close();managedPage=null;}
        getWindow().setStatusBarColor(Color.rgb(247,246,241));getWindow().setNavigationBarColor(Color.rgb(247,246,241));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFitsSystemWindows(true);scroll.setBackgroundColor(Color.rgb(247,246,241));
        root=vertical();root.setPadding(dp(22),dp(16),dp(22),dp(28));
        if(listPage){FrameLayout host=new FrameLayout(this);host.setFitsSystemWindows(true);host.setBackgroundColor(Color.rgb(247,246,241));host.addView(root,new FrameLayout.LayoutParams(-1,-1));setContentView(host);}else{scroll.addView(root);setContentView(scroll);}
        action(root,"应用管理".equals(title)?"‹  详情与规则":"应用规则管理".equals(title)?"‹  应用管理":"‹  订阅管理",this::goBack);text(root,title,24,INK,true);text(root,description,13,MUTED,false);
        progress=text(root,"",12,ORANGE,false);refreshProgress();
        body=vertical();body.setPadding(dp(16),dp(9),dp(16),dp(9));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(16));body.setBackground(bg);root.addView(body);
    }
    private void sources(){
        start("详情与规则","点击订阅卡片展开详情与规则，再次点击收起。");body.setTag("skip_source_details");
        TextView appManager=DialogUi.choice(body,"应用管理  ›\n已安装应用 · 跨订阅规则 · 全局白名单",()->navigate(this::managedApps));appManager.setTag("app_management_card");
        android.text.SpannableString caption=new android.text.SpannableString(appManager.getText());int subtitle=caption.toString().indexOf('\n');caption.setSpan(new android.text.style.StyleSpan(Typeface.BOLD),0,subtitle,0);caption.setSpan(new android.text.style.AbsoluteSizeSpan(12,true),subtitle+1,caption.length(),0);appManager.setText(caption);
        List<SkipSource> list=repository.getSources().list();if(list.isEmpty())text(body,"暂无订阅，请返回订阅管理添加。",13,MUTED,false);
        for(SkipSource source:list)sourceCard(body,source);
    }
    private void managedApps(){
        start("应用管理","关闭应用开关即加入全局白名单，停止所有订阅在该应用内匹配；各订阅的独立设置保持。",true);
        managedPage=new AppManagementPage(this,repository,body,root,managedState,app->navigate(()->managedRules(app)));
    }
    private void managedRules(ManagedAppCatalog.App app){
        start("应用规则管理",app.name+" · "+app.id);
        Switch whitelist=toggle(body,"加入全局白名单",repository.isWhitelisted(app.id),value->mutate(()->repository.setWhitelisted(app.id,value)));whitelist.setTag("global_whitelist:"+app.id);
        text(body,"加入后，全部订阅停止在此应用内匹配；移出后仍遵循各订阅、分类与规则组的独立开关。",12,MUTED,false);
        TextView loading=text(body,"正在汇总各订阅规则…",13,MUTED,false);int token=pageGeneration;
        AppRulesPage.loader.execute(()->{try{
            List<SkipManagedGroup> groups=SkipAppRules.groups(repository.getSources().list(),app.id,app.code,app.version,app.system,app.launcher);
            runOnUiThread(()->{if(isFinishing()||isDestroyed()||pageGeneration!=token)return;body.removeView(loading);
                if(groups.isEmpty())text(body,"当前订阅没有此应用的专属规则或适用全局规则，仍可加入全局白名单。",13,MUTED,false);
                java.util.Map<Long,LinearLayout> sections=new java.util.LinkedHashMap<>();
                for(SkipManagedGroup entry:groups){
                    LinearLayout area=sections.get(entry.getSource().getId());
                    if(area==null){SkipSource source=entry.getSource();area=fold(body,source.getName()+(source.getEnabled()?"":" · 订阅已关闭"));sections.put(source.getId(),area);}
                    managedGroup(area,entry);
                }
            });
        }catch(Exception error){runOnUiThread(()->{if(!isDestroyed()&&pageGeneration==token)loading.setText("读取失败："+error.getMessage());});}});
    }
    private LinearLayout fold(LinearLayout parent,String title){
        LinearLayout box=vertical();LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=UiSpacing.vertical(this,12);parent.addView(box,params);
        LinearLayout area=vertical();area.setPadding(dp(10),UiSpacing.vertical(this,4),0,UiSpacing.vertical(this,4));
        TextView heading=DialogUi.choice(box,title+"  ▾",()->{boolean show=area.getVisibility()!=android.view.View.VISIBLE;area.setVisibility(show?android.view.View.VISIBLE:android.view.View.GONE);});box.addView(area);return area;
    }
    private void managedGroup(LinearLayout parent,SkipManagedGroup entry){
        RawSubscription.RawGroupProps group=entry.getGroup();SkipSource source=entry.getSource();String app=entry.getApp();
        LinearLayout box=vertical();LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=UiSpacing.vertical(this,8);params.bottomMargin=UiSpacing.vertical(this,4);parent.addView(box,params);
        Switch control=toggle(box,(entry.getGlobal()?"全局 · ":"专属 · ")+group.getName(),entry.getPreference(),value->mutate(()->{if(entry.getGlobal())repository.getSources().enableGlobalForApp(source.getId(),app,group.getKey(),value);else repository.getSources().enableGroup(source.getId(),app,group.getKey(),value);}));
        control.setTag("managed_rule:"+source.getId()+":"+(entry.getGlobal()?"global":"app")+":"+group.getKey());
        if(!group.getValid()||!entry.getCompatible()){control.setEnabled(false);text(box,!group.getValid()?"规则校验失败，停止执行":"当前应用版本不符合规则条件",12,ORANGE,false);}
        if(!source.getEnabled())text(box,"订阅总开关已关闭，保存规则选择后需开启该订阅。",12,MUTED,false);
        else if(Boolean.FALSE.equals(source.getApps().get(app)))text(box,"此应用已加入该订阅的白名单，需从订阅内移出后才能执行。",12,MUTED,false);
        if(entry.getGlobal())text(box,"此开关只控制当前应用；仍受该订阅的全局规则组总开关控制。",12,MUTED,false);
        if(group.getDesc()!=null)text(box,group.getDesc(),12,MUTED,false);
        DialogUi.choice(box,group.getRules().size()+" 条规则 · 查看内容  ›",()->showText(group.getName(),group.getCacheStr()));
    }
    private void sourceCard(LinearLayout parent,SkipSource source){
        LinearLayout card=new LinearLayout(this);card.setTag("skip_source_card:"+source.getId());card.setOrientation(LinearLayout.HORIZONTAL);card.setGravity(Gravity.CENTER_VERTICAL);card.setPadding(dp(12),UiSpacing.vertical(this,12),dp(12),UiSpacing.vertical(this,12));card.setMinimumHeight(dp(52));card.setFocusable(true);card.setContentDescription(source.getName()+"，详情与规则");
        GradientDrawable background=new GradientDrawable();background.setColor(Color.rgb(237,242,232));background.setCornerRadius(dp(10));background.setStroke(dp(1),Color.rgb(219,228,212));card.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(Color.argb(35,102,112,106)),background,null));
        LinearLayout titles=vertical();card.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView name=text(titles,source.getName(),14,INK,true);name.setPadding(0,0,0,0);
        TextView metadata=text(titles,"v"+source.getVersion()+" · "+(source.getLocal()?"本地":"远程")+" · "+source.getSubscription().getApps().size()+" 个应用 · "+source.getSubscription().getGlobalGroups().size()+" 个全局组",12,MUTED,false);metadata.setPadding(0,UiSpacing.vertical(this,4),0,0);
        TextView arrow=new TextView(this);arrow.setText("›");arrow.setTextSize(22);arrow.setTextColor(MUTED);arrow.setGravity(Gravity.CENTER);card.addView(arrow,new LinearLayout.LayoutParams(dp(22),-2));
        LinearLayout group=vertical();group.setTag("skip_source_group:"+source.getId());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=UiSpacing.vertical(this,12);params.bottomMargin=UiSpacing.vertical(this,4);parent.addView(group,params);group.addView(card,new LinearLayout.LayoutParams(-1,-2));
        LinearLayout area=new LinearLayout(this);area.setPadding(dp(10),UiSpacing.vertical(this,8),0,UiSpacing.vertical(this,4));android.view.View guide=new android.view.View(this);guide.setBackgroundColor(Color.rgb(211,223,202));area.addView(guide,new LinearLayout.LayoutParams(dp(2),-1));
        LinearLayout content=vertical();content.setTag("skip_source_content:"+source.getId());LinearLayout.LayoutParams contentParams=new LinearLayout.LayoutParams(0,-2,1);contentParams.leftMargin=dp(10);area.addView(content,contentParams);group.addView(area,new LinearLayout.LayoutParams(-1,-2));
        Runnable sync=()->{boolean open=expandedSources.contains(source.getId());if(open&&content.getChildCount()==0)sourceControls(content,source);area.setVisibility(open?android.view.View.VISIBLE:android.view.View.GONE);arrow.setRotation(open?90:0);card.setContentDescription(source.getName()+"，详情与规则，"+(open?"点击收起":"点击展开"));};sync.run();
        card.setOnClickListener(v->{if(!expandedSources.add(source.getId()))expandedSources.remove(source.getId());sync.run();});
    }

    private interface Change{void changed(boolean value);}
    private void details(long id){
        SkipSource source;try{source=repository.getSources().find(id);}catch(Exception e){goBack();return;}
        start(source.getName(),"v"+source.getVersion()+" · ID "+source.getId());
        sourceControls(body,source);
    }
    private void sourceControls(LinearLayout target,SkipSource source){
        long id=source.getId();text(target,"ID "+id,12,MUTED,false);
        TextView origin=text(target,source.getLocal()?"本地订阅，可直接编辑":"更新来源："+source.getUrl(),12,MUTED,false);origin.setTextIsSelectable(true);
        if(source.getCheckedAt()>0)text(target,"上次检查："+DateFormat.getDateTimeInstance().format(new Date(source.getCheckedAt())),12,MUTED,false);
        action(target,"编辑订阅  ›",()->actions.editSubscription(id));
        action(target,"规则分类  ›",()->navigate(()->categories(id)));
        action(target,"应用规则  ›",()->navigate(()->apps(id)));
        action(target,"全局规则  ›",()->navigate(()->groups(id,"*")));
    }
    private void apps(long id){
        SkipSource source=repository.getSources().find(id);start("应用规则",source.getName()+" · 关闭应用规则即加入本订阅白名单",true);
        appsPage=new AppRulesPage(this,repository,id,body,root,appStates.computeIfAbsent(id,key->new AppRulesPage.State()),app->navigate(()->groups(id,app)));
    }
    private void categories(long id){
        SkipSource source=repository.getSources().find(id);start("规则分类",source.getName()+" · 分类开关控制应用规则，单独设置过的规则组优先。全局规则不受分类影响。");
        final int generation=pageGeneration;TextView loading=text(body,"正在读取规则分类…",13,MUTED,false);
        AppRulesPage.loader.execute(()->{try{
            java.util.Map<Integer,Integer> counts=new java.util.LinkedHashMap<>();for(RawSubscription.RawCategory category:source.getSubscription().getCategories()){
                int count=0;for(RawSubscription.RawApp app:source.getSubscription().getCategoryApps(category.getKey()))count+=app.getGroups().size();counts.put(category.getKey(),count);
            }
            runOnUiThread(()->{if(isFinishing()||isDestroyed()||generation!=pageGeneration)return;body.removeView(loading);
                if(counts.isEmpty())text(body,"此订阅未声明规则分类，应用规则和全局规则仍可单独管理。",13,MUTED,false);
                for(RawSubscription.RawCategory category:source.getSubscription().getCategories()){
                    LinearLayout card=vertical();card.setPadding(dp(12),UiSpacing.vertical(this,6),dp(12),UiSpacing.vertical(this,6));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(237,242,232));bg.setCornerRadius(dp(10));card.setBackground(bg);
                    LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.topMargin=UiSpacing.vertical(this,8);params.bottomMargin=UiSpacing.vertical(this,4);body.addView(card,params);
                    Boolean override=source.getCategories().get(category.getKey());Switch toggle=toggle(card,category.getName(),override!=null?override:!Boolean.FALSE.equals(category.getEnable()),value->mutate(()->repository.getSources().enableCategory(id,category.getKey(),value)));toggle.setTag("rule_category:"+category.getKey());
                    text(card,counts.get(category.getKey())+" 个应用规则组",12,MUTED,false);if(category.getDesc()!=null)text(card,category.getDesc(),12,MUTED,false);
                }
            });
        }catch(Exception error){runOnUiThread(()->{if(!isDestroyed()&&generation==pageGeneration)loading.setText("读取失败："+error.getMessage());});}});
    }
    private void groups(long id,String app){
        SkipSource source=repository.getSources().find(id);start("*".equals(app)?"全局规则":"规则组",source.getName()+" · "+app);
        List<? extends RawSubscription.RawGroupProps> list="*".equals(app)?source.getSubscription().getGlobalGroups():source.getSubscription().getAppGroups(app);
        if(list.isEmpty())text(body,"此处没有规则组",13,MUTED,false);
        for(RawSubscription.RawGroupProps group:list){
            boolean value=source.groupPreference(app,group);
            Switch view=toggle(body,group.getName(),value,v->mutate(()->repository.getSources().enableGroup(id,app,group.getKey(),v)));
            if(!group.getValid()){view.setEnabled(false);text(body,"规则校验失败，已停止执行："+group.getValidationError(),12,ORANGE,false);}
            if(group.getDesc()!=null)text(body,group.getDesc(),12,MUTED,false);
            action(body,group.getRules().size()+" 条规则 · 查看规则内容  ›",()->showText(group.getName(),group.getCacheStr()));
        }
    }
    private void mutate(Runnable change){ruleWriter.execute(()->{try{change.run();}catch(Exception error){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){message("保存失败："+error.getMessage());page.run();}});}});}
    private Switch toggle(LinearLayout parent,String title,boolean value,Change change){Switch s=new Switch(this);s.setText(title);SettingsRow.style(this,s);s.setChecked(value);parent.addView(s,new LinearLayout.LayoutParams(-1,-2));s.setOnCheckedChangeListener((button,checked)->change.changed(checked));return s;}
    private LinearLayout vertical(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(6),0,dp(6));parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private void action(LinearLayout parent,String title,Runnable run){TextView t=text(parent,title,14,ORANGE,true);t.setMinHeight(dp(48));t.setGravity(Gravity.CENTER_VERTICAL);t.setOnClickListener(v->run.run());}
    private void showText(String title,String value){ScrollView scroll=new ScrollView(this);TextView text=new TextView(this);text.setText(value);text.setTextSize(13);text.setTextIsSelectable(true);text.setPadding(dp(22),dp(12),dp(22),dp(12));scroll.addView(text);DialogUi.builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭",null).show();}
    private void message(String text){if(!text.isEmpty())Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextWatcher watcher(Runnable run){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){run.run();}public void afterTextChanged(Editable e){}};}
}
