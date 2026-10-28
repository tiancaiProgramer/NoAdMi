package io.github.miclean.skipfixture;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

/** Isolated test surface, with observable input identity and sequential-rule timing. */
public final class FixtureActivity extends Activity {
    private TextView input;
    public long chainStartedMs,chainElapsedMs;public int lastInputDeviceId=Integer.MIN_VALUE;
    @Override public boolean dispatchTouchEvent(android.view.MotionEvent event){
        if(input!=null&&event.getActionMasked()==android.view.MotionEvent.ACTION_UP){lastInputDeviceId=event.getDeviceId();input.setText("输入设备："+lastInputDeviceId);}
        return super.dispatchTouchEvent(event);
    }
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout page=new LinearLayout(this);page.setOrientation(1);int gap=Math.round(24*getResources().getDisplayMetrics().density);page.setPadding(gap,gap,gap,gap);page.setFitsSystemWindows(true);
        TextView title=new TextView(this);title.setText("NoAdMi 本机执行验证");title.setTextSize(24);page.addView(title);
        TextView ticker=new TextView(this);page.addView(ticker);android.os.Handler handler=new android.os.Handler(getMainLooper());handler.post(new Runnable(){int count;public void run(){if(isDestroyed()||isFinishing()||count>=24)return;ticker.setText("局部内容更新 "+(++count));handler.postDelayed(this,100);}});
        TextView status=new TextView(this);status.setText("测试广告等待关闭");status.setTextSize(18);page.addView(status);
        input=new TextView(this);input.setText("输入设备：未收到触屏事件");page.addView(input);
        String mode=getIntent().getStringExtra("mode");
        Button skip=new Button(this);skip.setText("chain".equals(mode)?"链路第一步":"repeat".equals(mode)?"重复场景目标":"测试跳过");
        skip.setOnClickListener(v->{
            if("chain".equals(mode)){
                chainStartedMs=android.os.SystemClock.elapsedRealtime();page.removeView(skip);Button second=new Button(this);second.setText("链路第二步");
                second.setOnClickListener(w->{chainElapsedMs=android.os.SystemClock.elapsedRealtime()-chainStartedMs;status.setText("测试广告已关闭");page.removeView(second);});page.addView(second);
            }else{status.setText("测试广告已关闭");page.removeView(skip);}
        });page.addView(skip);setContentView(page);
    }
}
