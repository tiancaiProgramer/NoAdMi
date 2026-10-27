package io.github.miclean.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import io.github.miclean.module.skip.SkipRepository;

/** All licensing is presented in the application's overall About card. */
final class OpenSourcePanel {
    static void add(Activity activity,LinearLayout parent){
        TextView heading=text(activity,parent,"",17,true);heading.setTag("license_header");heading.setMinHeight(dp(activity,56));heading.setPadding(dp(activity,12),UiSpacing.vertical(activity,12),dp(activity,12),UiSpacing.vertical(activity,12));
        LinearLayout.LayoutParams spacing=(LinearLayout.LayoutParams)heading.getLayoutParams();spacing.topMargin=UiSpacing.vertical(activity,12);heading.setLayoutParams(spacing);
        GradientDrawable bg=new GradientDrawable();bg.setColor(Color.rgb(237,242,232));bg.setCornerRadius(dp(activity,10));bg.setStroke(dp(activity,1),Color.rgb(219,228,212));heading.setBackground(bg);
        LinearLayout body=new LinearLayout(activity);body.setOrientation(1);body.setPadding(dp(activity,24),UiSpacing.vertical(activity,8),0,0);parent.addView(body);
        text(activity,body,"NoAdMi 集成版使用 GPL-3.0-only。智能跳过复用 GKD 的选择器、JSON5 订阅模型与规则参数核心，保留来源和许可；第三方订阅遵循各自许可。",12,false);
        text(activity,body,"GKD · GPL-3.0-only\ngithub.com/gkd-kit/gkd\nKotlin / kotlinx.serialization / kotlin-json5 / exp4j / libxposed · Apache-2.0\nShizuku API 13.1.5 · MIT · RikkaApps/Shizuku-API",12,false);
        String[] names={"GPL-3.0","Apache-2.0","Shizuku MIT"},files={"GPL-3.0.txt","Apache-2.0.txt","Shizuku-MIT.txt"};
        for(int i=0;i<names.length;i++){String name=names[i],file=files[i];TextView row=text(activity,body,"查看完整 "+name+" 许可  ›",14,true);row.setTextColor(Color.rgb(202,88,27));row.setMinHeight(dp(activity,48));row.setOnClickListener(v->read(activity,name,file));}
        android.content.SharedPreferences ui=activity.getSharedPreferences("miclean_ui",0);
        Runnable refresh=()->{boolean expanded=ui.getBoolean("open_source",false);heading.setText((expanded?"▾  ":"▸  ")+"许可与开源说明");body.setVisibility(expanded?View.VISIBLE:View.GONE);};
        heading.setOnClickListener(v->{ui.edit().putBoolean("open_source",!ui.getBoolean("open_source",false)).apply();refresh.run();});refresh.run();
    }
    private static void read(Activity activity,String title,String file){
        SkipRepository.executor.execute(()->{try(InputStream in=activity.getAssets().open("licenses/"+file);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>=0)out.write(buffer,0,n);String source=out.toString("UTF-8");
            activity.runOnUiThread(()->{if(activity.isFinishing()||activity.isDestroyed())return;ScrollView scroll=new ScrollView(activity);TextView value=text(activity,null,source,13,false);value.setTextIsSelectable(true);value.setPadding(dp(activity,20),dp(activity,12),dp(activity,20),dp(activity,12));scroll.addView(value);DialogUi.builder(activity).setTitle(title).setView(scroll).setPositiveButton("关闭",null).show();});
        }catch(Exception error){activity.runOnUiThread(()->{if(!activity.isFinishing())Toast.makeText(activity,"读取许可失败："+error.getMessage(),Toast.LENGTH_LONG).show();});}});
    }
    private static TextView text(Activity activity,LinearLayout parent,String value,int size,boolean bold){TextView row=new TextView(activity);row.setText(value);row.setTextSize(size);row.setTextColor(Color.rgb(36,44,40));row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setPadding(0,UiSpacing.vertical(activity,6),0,UiSpacing.vertical(activity,6));if(bold)row.setTypeface(null,Typeface.BOLD);if(parent!=null)parent.addView(row,new LinearLayout.LayoutParams(-1,-2));return row;}
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
