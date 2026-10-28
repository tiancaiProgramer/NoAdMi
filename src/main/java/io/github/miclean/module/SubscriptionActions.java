package io.github.miclean.module;

import android.app.*;
import android.content.Intent;
import android.net.Uri;
import android.widget.*;
import android.view.Gravity;
import java.io.*;
import io.github.miclean.module.skip.*;
import li.gkd.app.subscription.RawSubscription;

/** Activity-owned subscription operations, shared by the inline panel and detail pages. */
final class SubscriptionActions implements AutoCloseable {
    private static final int IMPORT=71;
    private static final java.util.concurrent.ExecutorService edits=java.util.concurrent.Executors.newSingleThreadExecutor(r->new Thread(r,"NoAdMi-subscription-edits"));
    private static final java.util.concurrent.ExecutorService downloads=java.util.concurrent.Executors.newFixedThreadPool(2,r->new Thread(r,"NoAdMi-subscription-downloads"));
    private final Activity activity;
    private final SkipRepository repository;
    private final Runnable changed,progress;
    private boolean busy,closed;
    private Runnable popupProgress;
    private AlertDialog editorDialog,confirmationDialog,documentDialog;
    private interface Work<T>{T run()throws Exception;}
    private interface Done<T>{void accept(T value);}
    SubscriptionActions(Activity activity,Runnable changed,Runnable progress){this.activity=activity;repository=SkipRepository.get(activity);this.changed=changed;this.progress=progress;}
    boolean busy(){return busy;}
    void addLink(){
        LinearLayout form=DialogUi.content(activity);DialogUi.description(form,"填写 JSON / JSON5 文件的 HTTPS 直链。");
        EditText url=new EditText(activity);url.setTextSize(14);url.setSingleLine(true);url.setHint("https://…/subscription.json5");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);DialogUi.input(url);form.addView(url,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=DialogUi.builder(activity).setTitle("添加订阅链接").setView(form).setNegativeButton("取消",null).setPositiveButton("读取订阅",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button->{String value=url.getText().toString().trim();try{SkipSources.requireHttps(value);}catch(Exception e){url.setError(e.getMessage());return;}dialog.dismiss();network(()->SkipSources.download(value),document->preview(document,value));}));DialogUi.show(dialog);
    }
    private void copyLink(String value){
        android.content.ClipboardManager clipboard=(android.content.ClipboardManager)activity.getSystemService(android.content.Context.CLIPBOARD_SERVICE);
        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("订阅链接",value));
        if(android.os.Build.VERSION.SDK_INT<33)notice("订阅链接已复制");
    }
    void editSubscription(long id){
        if(editorDialog!=null&&editorDialog.isShowing())return;
        SkipSource source=repository.getSources().find(id);boolean remote=!source.getLocal();
        LinearLayout header=new LinearLayout(activity);header.setGravity(Gravity.CENTER_VERTICAL);header.setPadding(dp(22),UiSpacing.vertical(activity,12),dp(14),UiSpacing.vertical(activity,4));header.setTag("subscription_edit_header");
        TextView title=new TextView(activity);title.setTag("dialog_title");title.setText("编辑订阅");title.setTextSize(20);title.setTextColor(DialogUi.INK);title.setTypeface(null,android.graphics.Typeface.BOLD);header.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        LinearLayout form=DialogUi.content(activity);TextView metadata=DialogUi.description(form,source.getName()+" · v"+source.getVersion());
        EditText input=new EditText(activity);input.setTag("subscription_url");input.setTextSize(14);input.setSingleLine(true);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);input.setText(source.getUrl());DialogUi.input(input);
        input.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){input.setError(null);}public void afterTextChanged(android.text.Editable e){}});
        if(remote){DialogUi.description(form,"保存仅修改链接；更新会下载规则并保存。已有开关保留。");form.addView(input,new LinearLayout.LayoutParams(-1,-2));}
        else DialogUi.description(form,"本地订阅没有远程更新链接，可在下方查看或编辑 JSON5。");
        TextView status=DialogUi.description(form,"");status.setTag("subscription_edit_status");
        AlertDialog.Builder builder=DialogUi.builder(activity).setCustomTitle(header).setView(form).setNeutralButton("删除",null).setNegativeButton(remote?"取消":"关闭",null);
        if(remote)builder.setPositiveButton("保存",null);AlertDialog dialog=builder.create();editorDialog=dialog;
        form.removeView(status);
        LinearLayout documentSection=new LinearLayout(activity);documentSection.setOrientation(LinearLayout.VERTICAL);documentSection.setTag("subscription_edit_document_section");
        LinearLayout.LayoutParams sectionParams=new LinearLayout.LayoutParams(-1,-2);sectionParams.topMargin=UiSpacing.vertical(activity,remote?12:0);form.addView(documentSection,sectionParams);
        TextView viewDocument=DialogUi.choice(documentSection,"查看订阅 JSON5  ›",()->view(id));viewDocument.setTag("subscription_edit_view_document");
        ((LinearLayout.LayoutParams)viewDocument.getLayoutParams()).bottomMargin=0;
        if(!remote){TextView editDocument=DialogUi.choice(documentSection,"编辑订阅 JSON5  ›",()->{dialog.dismiss();edit(id);});LinearLayout.LayoutParams params=(LinearLayout.LayoutParams)editDocument.getLayoutParams();params.topMargin=UiSpacing.vertical(activity,12);params.bottomMargin=0;}
        form.addView(status);
        TextView copy=remote?headerAction(header,"复制","subscription_edit_copy",()->copyLink(input.getText().toString().trim())):null;
        TextView update=remote?headerAction(header,"更新","subscription_edit_update",()->{
            String value=input.getText().toString().trim();try{SkipSources.requireHttps(value);}catch(Exception error){input.setError(error.getMessage());return;}
            network(()->repository.getSources().replaceFromUrl(id,SkipSources.download(value),value),updated->{metadata.setText(repository.getSources().find(id).getName()+" · v"+repository.getSources().find(id).getVersion());changed.run();notice(updated?"订阅已更新，手动开关已保留":"已是最新版本");});
        }):null;
        Runnable sync=()->{if(!dialog.isShowing())return;status.setText(busy?"正在处理，请稍候…":"");input.setEnabled(!busy);dialog.getButton(-3).setEnabled(!busy);if(remote){copy.setEnabled(!busy);update.setEnabled(!busy);dialog.getButton(-1).setEnabled(!busy);}};
        dialog.setOnDismissListener(d->{if(popupProgress==sync)popupProgress=null;});
        dialog.setOnShowListener(v->{popupProgress=sync;sync.run();dialog.getButton(-3).setTag("subscription_edit_delete");dialog.getButton(-3).setOnClickListener(button->delete(id,dialog::dismiss));
            if(remote)dialog.getButton(-1).setOnClickListener(button->{String value=input.getText().toString().trim();try{SkipSources.requireHttps(value);}catch(Exception error){input.setError(error.getMessage());return;}
                work(()->{repository.getSources().changeUrl(id,value);return "已保存订阅链接";},result->{dialog.dismiss();changed.run();notice(result);});});
        });DialogUi.show(dialog);
    }
    private TextView headerAction(LinearLayout parent,String label,String tag,Runnable run){
        TextView action=new TextView(activity);action.setTag(tag);action.setText(label);action.setTextSize(14);action.setTextColor(DialogUi.ORANGE);action.setGravity(Gravity.CENTER);action.setMinHeight(dp(48));action.setPadding(dp(8),0,dp(8),0);action.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(android.graphics.Color.argb(35,102,112,106)),null,null));action.setOnClickListener(v->run.run());parent.addView(action,new LinearLayout.LayoutParams(-2,-2));return action;
    }
    void importLocal(){
        LinearLayout box=DialogUi.content(activity);AlertDialog dialog=DialogUi.builder(activity).setTitle("导入本地规则").setView(box).setNegativeButton("取消",null).create();
        DialogUi.choice(box,"从文件导入",()->{dialog.dismiss();activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT);});
        DialogUi.choice(box,"粘贴或新建",()->{dialog.dismiss();edit(null);});DialogUi.show(dialog);
    }
    void updateAll(){network(repository::updateAll,result->{changed.run();notice(result);});}
    void update(long id){network(()->repository.update(id),value->{changed.run();notice(value?"订阅已更新，手动开关已保留":"已是最新版本");});}
    private void delete(long id,Runnable deleted){
        SkipSource source=repository.getSources().find(id);
        if(confirmationDialog!=null&&confirmationDialog.isShowing())return;
        confirmationDialog=DialogUi.builder(activity).setTitle("删除 "+source.getName()+"？").setMessage("该订阅的规则及手动开关将一并移除。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->work(()->{repository.getSources().remove(id);return "已删除订阅";},result->{deleted.run();changed.run();notice(result);})).show();
    }
    private void view(long id){
        SkipSource source=repository.getSources().find(id);
        if(documentDialog==null||!documentDialog.isShowing())documentDialog=SubscriptionDocumentDialog.show(activity,source.getDocument());
    }
    void edit(Long id){
        SkipSource source=id==null?null:repository.getSources().find(id);
        if(source!=null&&!source.getLocal()){view(id);return;}
        EditText input=new EditText(activity);input.setTextSize(13);input.setTypeface(android.graphics.Typeface.MONOSPACE);input.setGravity(Gravity.TOP|Gravity.START);input.setMinLines(9);input.setMaxLines(18);input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(source==null?"{\n  id: -2,\n  name: '本地规则',\n  version: 1,\n  apps: [],\n  globalGroups: [],\n}":source.getDocument());
        boolean editable=source==null||source.getLocal();if(!editable)input.setKeyListener(null);DialogUi.input(input);
        LinearLayout form=DialogUi.content(activity);form.addView(input,new LinearLayout.LayoutParams(-1,-2));ScrollView scroll=new ScrollView(activity);scroll.addView(form);AlertDialog.Builder builder=DialogUi.builder(activity).setTitle(editable?"编辑本地订阅":"订阅原文（只读）").setView(scroll).setNegativeButton("关闭",null);if(editable)builder.setPositiveButton("校验并保存",null);
        AlertDialog dialog=builder.create();dialog.setOnShowListener(v->{if(editable)dialog.getButton(-1).setOnClickListener(button->{String document=input.getText().toString();work(()->{if(id==null)repository.getSources().add(document,"",false);else repository.getSources().replace(id,document,false);return "已保存本地订阅";},result->{dialog.dismiss();changed.run();notice(result);});});});DialogUi.show(dialog);
    }
    boolean onActivityResult(int request,int result,Intent data){
        if(request!=IMPORT)return false;if(result!=Activity.RESULT_OK||data==null||data.getData()==null)return true;Uri uri=data.getData();
        work(()->{try(InputStream in=activity.getContentResolver().openInputStream(uri);ByteArrayOutputStream out=new ByteArrayOutputStream()){if(in==null)throw new IllegalArgumentException("无法读取文件");byte[] buffer=new byte[8192];int n;while((n=in.read(buffer))>=0){if(out.size()+n>SkipSources.MAX_BYTES)throw new IllegalArgumentException("订阅文件超过 10 MB");out.write(buffer,0,n);}return out.toString("UTF-8");}},document->preview(document,""));return true;
    }
    private void preview(String document,String url){
        work(()->RawSubscription.Companion.parse(document,true),raw->{LinearLayout box=DialogUi.content(activity);DialogUi.description(box,raw.getName()+" · v"+raw.getVersion()+"\n"+raw.getApps().size()+" 个应用 · "+raw.getGlobalGroups().size()+" 个全局规则组");Switch enable=new Switch(activity);enable.setText("添加后启用这个订阅");SettingsRow.style(activity,enable);box.addView(enable);DialogUi.builder(activity).setTitle("确认添加").setView(box).setNegativeButton("取消",null).setPositiveButton("添加",(d,w)->work(()->repository.getSources().add(document,url,enable.isChecked()),source->{changed.run();notice("已添加 "+source.getName());})).show();});
    }
    private <T> void work(Work<T> task,Done<T> done){work(task,done,edits);}
    private <T> void network(Work<T> task,Done<T> done){work(task,done,downloads);}
    private <T> void work(Work<T> task,Done<T> done,java.util.concurrent.Executor executor){
        if(busy){notice("正在处理，请稍候");return;}busy=true;signalProgress();
        executor.execute(()->{try{T value=task.run();activity.runOnUiThread(()->{busy=false;if(!alive())return;signalProgress();done.accept(value);});}catch(Exception error){activity.runOnUiThread(()->{busy=false;if(!alive())return;signalProgress();DialogUi.builder(activity).setTitle("未完成操作").setMessage(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()).setPositiveButton("知道了",null).show();});}});
    }
    private void signalProgress(){progress.run();if(popupProgress!=null)popupProgress.run();}
    private boolean alive(){return !closed&&!activity.isFinishing()&&!activity.isDestroyed();}
    private void notice(String value){if(alive()&&!value.isEmpty())Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    @Override public void close(){closed=true;for(AlertDialog dialog:new AlertDialog[]{editorDialog,confirmationDialog,documentDialog})if(dialog!=null)dialog.dismiss();popupProgress=null;}
}
