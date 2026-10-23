package io.github.miclean.tests;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.*;

/** NoAdMi-only test: expose and enable the six new explicit advertising controls. */
public final class PromotionUiSmoke extends Instrumentation {
    volatile Activity activity;int checks;boolean desired;Bundle options;
    void check(boolean b,String s){checks++;if(!b)throw new AssertionError(s);}
    TextView find(View v,String text){if(v instanceof TextView&&((TextView)v).getText().toString().contains(text))return(TextView)v;if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++){TextView r=find(g.getChildAt(i),text);if(r!=null)return r;}}return null;}
    @Override public void onCreate(Bundle args){super.onCreate(args);options=args;desired=!"false".equals(args.getString("enabled","true"));args.putBoolean("preserve","true".equals(args.getString("preserve","false")));start();}
    @Override public void callActivityOnCreate(Activity a,Bundle state){super.callActivityOnCreate(a,state);if(a.getClass().getName().equals("io.github.miclean.module.MainActivity"))activity=a;}
    @Override public void onStart(){Bundle result=new Bundle();try{
        getTargetContext().startActivity(new Intent().setClassName("io.github.miclean.module","io.github.miclean.module.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        for(int i=0;i<60&&activity==null;i++)SystemClock.sleep(100);check(activity!=null,"Activity absent");
        for(int i=0;i<60;i++){final boolean[] ready={false};runOnMainSync(()->ready[0]=find(activity.getWindow().getDecorView(),"框架服务已连接")!=null);if(ready[0])break;SystemClock.sleep(100);}
        final Throwable[] error={null};runOnMainSync(()->{try{
            View root=activity.getWindow().getDecorView();
            TextView browser=find(root,"▸  小米浏览器");if(browser==null)browser=find(root,"▾  小米浏览器");
            TextView market=find(root,"▸  应用商店");if(market==null)market=find(root,"▾  应用商店");
            check(browser!=null&&market!=null,"App groups missing");check(find(root,"应用精简")==null,"Uninstall UI returned");
            boolean bc=browser.getText().toString().startsWith("▸"),mc=market.getText().toString().startsWith("▸");if(bc)browser.performClick();if(mc)market.performClick();
            List<TextView> folds=new ArrayList<>();TextView h=find(root,"开屏与信息流");check(h!=null,"Browser section absent");if(h.getText().toString().startsWith("▸")){h.performClick();folds.add(h);}
            for(String old:new String[]{"隐藏首次与回流推荐页","隐藏下载与更新相关广告推荐","隐藏详情小卡广告推荐","隐藏首页顶部大幅轮播横幅","隐藏详情页“其他用户还喜欢”","隐藏详情页热门／同类及更多推荐"})check(find(root,old)==null,"Old recommendation toggle remains "+old);
            for(String label:new String[]{"隐藏主动与第三方唤起开屏广告","过滤信息流接口中的广告卡片","隐藏开屏广告","应用内推荐"}){
                TextView v=find(root,label);check(v instanceof Switch,"Toggle absent "+label);Switch sw=(Switch)v;check(sw.isEnabled(),"Toggle unavailable "+label);
                boolean choice=label.equals("应用内推荐")&&options.containsKey("recommend")?Boolean.parseBoolean(options.getString("recommend")):sw.isChecked();
                if(sw.isChecked()!=choice)sw.performClick();check(sw.isChecked()==choice,"Toggle did not save "+label);
            }
            for(TextView f:folds)f.performClick();if(bc)browser.performClick();if(mc)market.performClick();
            TextView about=find(root,"关于");ViewGroup card=(ViewGroup)about.getParent(),parent=(ViewGroup)card.getParent();check(parent.indexOfChild(card)==parent.getChildCount()-1,"About not last");
        }catch(Throwable e){error[0]=e;}});if(error[0]!=null)throw error[0];
        result.putString("stream","PASS: "+checks+" new advertising UI checks; unified application recommendations and separate splash control verified via real module UI, uninstall UI absent, fold states restored, About last.\n");finish(Activity.RESULT_OK,result);
    }catch(Throwable e){result.putString("stream","FAIL: "+e+"\n");finish(Activity.RESULT_CANCELED,result);}}
}
