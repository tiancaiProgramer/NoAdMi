package io.github.miclean.skipfixture;
import android.app.Activity;
import android.os.Bundle;
import android.widget.*;

/** An isolated ad-shaped test surface; no network, accounts, installs or business data. */
public final class FixtureActivity extends Activity {
    @Override public void onCreate(Bundle state){
        super.onCreate(state);LinearLayout page=new LinearLayout(this);page.setOrientation(1);int gap=Math.round(24*getResources().getDisplayMetrics().density);page.setPadding(gap,gap,gap,gap);page.setFitsSystemWindows(true);
        TextView title=new TextView(this);title.setText("NoAdMi 本机执行验证");title.setTextSize(24);page.addView(title);
        TextView status=new TextView(this);status.setText("测试广告等待关闭");status.setTextSize(18);page.addView(status);
        Button skip=new Button(this);skip.setText("测试跳过");skip.setOnClickListener(v->{status.setText("测试广告已关闭");page.removeView(skip);});page.addView(skip);setContentView(page);
    }
}
