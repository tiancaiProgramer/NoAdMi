package io.github.miclean.module;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.*;

/** Native dialogs using the same surface, type, spacing and accent as the settings cards. */
final class DialogUi {
    static final int INK=Color.rgb(36,44,40),MUTED=Color.rgb(102,112,106),ORANGE=Color.rgb(202,88,27);
    private DialogUi(){}
    static AlertDialog.Builder builder(Activity activity){return new Builder(activity);}
    private static final class Builder extends AlertDialog.Builder {
        private final Activity activity;
        Builder(Activity activity){super(activity,android.R.style.Theme_Material_Light_Dialog_Alert);this.activity=activity;}
        @Override public AlertDialog.Builder setTitle(CharSequence value){
            TextView title=new TextView(getContext());title.setTag("dialog_title");title.setText(value);title.setTextSize(20);title.setTextColor(INK);title.setTypeface(null,Typeface.BOLD);title.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
            title.setPadding(dp(activity,22),gap(activity,24),dp(activity,22),gap(activity,12));return setCustomTitle(title);
        }
        @Override public AlertDialog show(){AlertDialog dialog=create();DialogUi.show(dialog);return dialog;}
    }
    static void show(AlertDialog dialog){
        dialog.show();Window window=dialog.getWindow();if(window==null)return;
        Context context=dialog.getContext();GradientDrawable surface=shape(context,Color.rgb(247,246,241),18);surface.setStroke(dp(context,1),Color.rgb(219,228,212));window.setBackgroundDrawable(surface);
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        // Use the actual host window, including split-screen, instead of fixed pixel dimensions.
        android.graphics.Rect available=new android.graphics.Rect();window.getDecorView().getWindowVisibleDisplayFrame(available);
        int width=available.width()>0?available.width():context.getResources().getDisplayMetrics().widthPixels;
        window.setLayout(Math.min(dp(context,480),Math.max(dp(context,200),width-dp(context,32))),WindowManager.LayoutParams.WRAP_CONTENT);
        style(window.getDecorView());
    }
    private static void style(View view){
        if(view instanceof Button&&!(view instanceof CompoundButton)){Button button=(Button)view;button.setTextColor(ORANGE);button.setTextSize(14);button.setAllCaps(false);button.setMinHeight(dp(view.getContext(),48));}
        else if(view instanceof TextView&&!(view instanceof EditText)&&!(view instanceof Switch)&&!"dialog_title".equals(view.getTag())){
            TextView text=(TextView)view;text.setTextColor(view.getId()==android.R.id.message?MUTED:INK);text.setTextSize(14);
        }
        if(view instanceof ViewGroup)for(int i=0;i<((ViewGroup)view).getChildCount();i++)style(((ViewGroup)view).getChildAt(i));
    }
    static LinearLayout content(Activity activity){LinearLayout box=new LinearLayout(activity);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(activity,22),gap(activity,8),dp(activity,22),gap(activity,8));return box;}
    static TextView description(LinearLayout parent,String value){TextView text=new TextView(parent.getContext());text.setText(value);text.setTextColor(MUTED);text.setTextSize(14);text.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);text.setPadding(0,0,0,gap(parent.getContext(),16));parent.addView(text,new LinearLayout.LayoutParams(-1,-2));return text;}
    static void input(EditText input){
        Context context=input.getContext();input.setTextColor(INK);input.setHintTextColor(MUTED);input.setHighlightColor(Color.argb(70,202,88,27));input.setBackgroundTintList(null);input.setPadding(dp(context,12),gap(context,16),dp(context,12),gap(context,16));input.setMinHeight(dp(context,52));
        StateListDrawable background=new StateListDrawable();GradientDrawable focused=shape(context,Color.WHITE,10);focused.setStroke(dp(context,1),ORANGE);GradientDrawable normal=shape(context,Color.WHITE,10);normal.setStroke(dp(context,1),Color.rgb(219,228,212));background.addState(new int[]{android.R.attr.state_focused},focused);background.addState(new int[]{},normal);input.setBackground(background);
        if(android.os.Build.VERSION.SDK_INT>=29){GradientDrawable cursor=shape(context,ORANGE,0);cursor.setSize(dp(context,2),dp(context,24));input.setTextCursorDrawable(cursor);}
    }
    static TextView choice(LinearLayout parent,String label,Runnable action){
        TextView row=new TextView(parent.getContext());row.setText(label);row.setTextColor(INK);row.setTextSize(14);row.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);row.setMinHeight(dp(parent.getContext(),52));row.setPadding(dp(parent.getContext(),12),gap(parent.getContext(),12),dp(parent.getContext(),12),gap(parent.getContext(),12));
        GradientDrawable background=shape(parent.getContext(),Color.rgb(237,242,232),10);background.setStroke(dp(parent.getContext(),1),Color.rgb(219,228,212));row.setBackground(new android.graphics.drawable.RippleDrawable(ColorStateList.valueOf(Color.argb(35,102,112,106)),background,null));row.setOnClickListener(v->action.run());
        LinearLayout.LayoutParams params=new LinearLayout.LayoutParams(-1,-2);params.bottomMargin=gap(parent.getContext(),8);parent.addView(row,params);return row;
    }
    private static GradientDrawable shape(Context context,int color,int radius){GradientDrawable shape=new GradientDrawable();shape.setColor(color);shape.setCornerRadius(dp(context,radius));return shape;}
    private static int dp(Context context,int value){return Math.round(value*context.getResources().getDisplayMetrics().density);}
    private static int gap(Context context,int value){return UiSpacing.vertical(context,value);}
}
