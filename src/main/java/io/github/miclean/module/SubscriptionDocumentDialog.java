package io.github.miclean.module;

import android.app.*;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.concurrent.*;

/** Shows only bounded visible text rows. No full-document EditText, span buffer or layout. */
final class SubscriptionDocumentDialog {
    private static final ExecutorService reader=Executors.newSingleThreadExecutor(r->new Thread(r,"NoAdMi-document-view"));
    static AlertDialog show(Activity activity,String document){
        Handler main=new Handler(Looper.getMainLooper());LinearLayout box=DialogUi.content(activity);
        TextView status=DialogUi.description(box,"正在加载订阅原文…");status.setTag("subscription_document_status");
        ListView list=new ListView(activity);list.setTag("subscription_document_list");list.setDivider(null);list.setItemsCanFocus(true);
        android.graphics.Rect available=new android.graphics.Rect();activity.getWindow().getDecorView().getWindowVisibleDisplayFrame(available);
        int height=available.height()>0?available.height():activity.getResources().getDisplayMetrics().heightPixels;
        box.addView(list,new LinearLayout.LayoutParams(-1,Math.max(dp(activity,120),Math.round(height*.52f))));
        AlertDialog dialog=DialogUi.builder(activity).setTitle("订阅 JSON5（只读）").setView(box).setNegativeButton("关闭",null).create();
        Future<?> task=reader.submit(()->{try{
            DocumentPages pages=new DocumentPages(document);
            main.post(()->{if(activity.isDestroyed()||activity.isFinishing()||!dialog.isShowing())return;
                status.setText(pages.length()+" 字符 · 原文分段显示，长按可复制所选文字");
                list.setAdapter(new BaseAdapter(){
                    public int getCount(){return pages.size();}public Object getItem(int position){return pages.page(position);}public long getItemId(int position){return position;}
                    public View getView(int position,View recycled,ViewGroup parent){TextView row;if(recycled==null){row=new TextView(activity);row.setTextSize(13);row.setTextColor(DialogUi.INK);row.setTypeface(Typeface.MONOSPACE);row.setTextIsSelectable(true);row.setPadding(0,0,0,0);row.setIncludeFontPadding(false);}else row=(TextView)recycled;row.setText(pages.page(position));return row;}
                });
            });
        }catch(CancellationException ignored){}catch(Exception error){main.post(()->{if(dialog.isShowing())status.setText("加载失败："+error.getMessage());});}});
        dialog.setOnDismissListener(d->{task.cancel(true);main.removeCallbacksAndMessages(null);list.setAdapter(null);});
        DialogUi.show(dialog);return dialog;
    }
    private static int dp(Activity activity,int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    private SubscriptionDocumentDialog(){}
}
