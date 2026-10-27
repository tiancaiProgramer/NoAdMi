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
    private SkipRepository repository;
    private SubscriptionActions actions;
    private LinearLayout body;
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
    @Override public void onDestroy(){if(actions!=null)actions.close();super.onDestroy();}
    @Override protected void onActivityResult(int request,int result,Intent data){super.onActivityResult(request,result,data);if(actions!=null)actions.onActivityResult(request,result,data);}
    private void refreshProgress(){if(progress!=null)progress.setText(actions.busy()?"正在处理，请稍候…":"");}
    private void goBack(){if(back.isEmpty())finish();else{page=back.pop();page.run();}}
    private void navigate(Runnable next){back.push(page);page=next;page.run();}
    private void start(String title,String description){
        getWindow().setStatusBarColor(Color.rgb(247,246,241));getWindow().setNavigationBarColor(Color.rgb(247,246,241));
        getWindow().getDecorView().setSystemUiVisibility(android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|android.view.View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        ScrollView scroll=new ScrollView(this);scroll.setFitsSystemWindows(true);scroll.setBackgroundColor(Color.rgb(247,246,241));
        LinearLayout root=vertical();root.setPadding(dp(22),dp(16),dp(22),dp(28));scroll.addView(root);setContentView(scroll);
        action(root,"‹  订阅管理",this::goBack);text(root,title,24,INK,true);text(root,description,13,MUTED,false);
        progress=text(root,"",12,ORANGE,false);refreshProgress();
        body=vertical();body.setPadding(dp(16),dp(9),dp(16),dp(9));GradientDrawable bg=new GradientDrawable();bg.setColor(Color.WHITE);bg.setCornerRadius(dp(16));body.setBackground(bg);root.addView(body);
    }
    private void sources(){
        start("详情与规则","点击订阅卡片展开详情与规则，再次点击收起。");body.setTag("skip_source_details");
        List<SkipSource> list=repository.getSources().list();if(list.isEmpty())text(body,"暂无订阅，请返回订阅管理添加。",13,MUTED,false);
        for(SkipSource source:list)sourceCard(body,source);
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
        text(target,source.getLocal()?"本地订阅，可直接编辑":"更新来源："+source.getUrl(),12,MUTED,false);
        if(source.getCheckedAt()>0)text(target,"上次检查："+DateFormat.getDateTimeInstance().format(new Date(source.getCheckedAt())),12,MUTED,false);
        if(!source.getLocal())action(target,"更新此订阅",()->actions.update(id));
        action(target,"应用规则  ›",()->navigate(()->apps(id)));
        action(target,"全局规则  ›",()->navigate(()->groups(id,"*")));
        action(target,source.getLocal()?"编辑订阅 JSON5":"查看订阅 JSON5",()->actions.edit(id));
        action(target,"删除订阅",()->actions.delete(id));
    }
    private void apps(long id){
        SkipSource source=repository.getSources().find(id);start("应用规则",source.getName()+" · 可以独立关闭某个应用的规则");
        EditText search=new EditText(this);search.setHint("搜索名称或包名");search.setSingleLine(true);body.addView(search);
        LinearLayout list=vertical();body.addView(list);
        Runnable render=()->{list.removeAllViews();String query=search.getText().toString().toLowerCase(java.util.Locale.ROOT);
            for(RawSubscription.RawApp app:source.getSubscription().getApps()){
                String name=app.getName()==null?app.getId():app.getName();if(!(name+" "+app.getId()).toLowerCase(java.util.Locale.ROOT).contains(query))continue;
                toggle(list,name,!Boolean.FALSE.equals(repository.getSources().find(id).getApps().get(app.getId())),value->mutate(()->repository.getSources().enableApp(id,app.getId(),value)));
                text(list,app.getId()+" · "+app.getGroups().size()+" 个规则组",12,MUTED,false);
                action(list,"规则组  ›",()->navigate(()->groups(id,app.getId())));
            }
        };search.addTextChangedListener(watcher(render));render.run();
    }
    private void groups(long id,String app){
        SkipSource source=repository.getSources().find(id);start("*".equals(app)?"全局规则":"规则组",source.getName()+" · "+app);
        List<? extends RawSubscription.RawGroupProps> list="*".equals(app)?source.getSubscription().getGlobalGroups():source.getSubscription().getAppGroups(app);
        if(list.isEmpty())text(body,"此处没有规则组",13,MUTED,false);
        for(RawSubscription.RawGroupProps group:list){
            String key=source.groupKey(app,group.getKey());Boolean override=source.getGroups().get(key);
            RawSubscription.RawCategory category=source.getSubscription().getCategory(group.getName());
            boolean value=override!=null?override:group.getEnable()!=null?group.getEnable():category==null||!Boolean.FALSE.equals(category.getEnable());
            Switch view=toggle(body,group.getName(),value,v->mutate(()->repository.getSources().enableGroup(id,app,group.getKey(),v)));
            if(!group.getValid()){view.setEnabled(false);text(body,"规则校验失败，已停止执行："+group.getValidationError(),12,ORANGE,false);}
            if(group.getDesc()!=null)text(body,group.getDesc(),12,MUTED,false);
            action(body,group.getRules().size()+" 条规则 · 查看规则内容  ›",()->showText(group.getName(),group.getCacheStr()));
        }
    }
    private void mutate(Runnable change){SkipRepository.executor.execute(()->{try{change.run();}catch(Exception error){runOnUiThread(()->{if(!isFinishing()&&!isDestroyed()){message("保存失败："+error.getMessage());page.run();}});}});}
    private Switch toggle(LinearLayout parent,String title,boolean value,Change change){Switch s=new Switch(this);s.setText(title);SettingsRow.style(this,s);s.setChecked(value);parent.addView(s,new LinearLayout.LayoutParams(-1,-2));s.setOnCheckedChangeListener((button,checked)->change.changed(checked));return s;}
    private LinearLayout vertical(){LinearLayout layout=new LinearLayout(this);layout.setOrientation(LinearLayout.VERTICAL);return layout;}
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);if(bold)t.setTypeface(null,Typeface.BOLD);t.setPadding(0,dp(6),0,dp(6));parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;}
    private void action(LinearLayout parent,String title,Runnable run){TextView t=text(parent,title,14,ORANGE,true);t.setMinHeight(dp(48));t.setGravity(Gravity.CENTER_VERTICAL);t.setOnClickListener(v->run.run());}
    private void showText(String title,String value){ScrollView scroll=new ScrollView(this);TextView text=new TextView(this);text.setText(value);text.setTextSize(13);text.setTextIsSelectable(true);text.setPadding(dp(22),dp(12),dp(22),dp(12));scroll.addView(text);DialogUi.builder(this).setTitle(title).setView(scroll).setPositiveButton("关闭",null).show();}
    private void message(String text){if(!text.isEmpty())Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private TextWatcher watcher(Runnable run){return new TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){run.run();}public void afterTextChanged(Editable e){}};}
}
