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
    private final Activity activity;
    private final SkipRepository repository;
    private final Runnable changed,progress;
    private boolean busy,closed;
    private interface Work<T>{T run()throws Exception;}
    private interface Done<T>{void accept(T value);}
    SubscriptionActions(Activity activity,Runnable changed,Runnable progress){this.activity=activity;repository=SkipRepository.get(activity);this.changed=changed;this.progress=progress;}
    boolean busy(){return busy;}
    void addLink(){
        LinearLayout form=DialogUi.content(activity);DialogUi.description(form,"填写 JSON / JSON5 文件的 HTTPS 直链。");
        EditText url=new EditText(activity);url.setTextSize(14);url.setSingleLine(true);url.setHint("https://…/subscription.json5");url.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI);DialogUi.input(url);form.addView(url,new LinearLayout.LayoutParams(-1,-2));
        AlertDialog dialog=DialogUi.builder(activity).setTitle("添加订阅链接").setView(form).setNegativeButton("取消",null).setPositiveButton("读取订阅",null).create();
        dialog.setOnShowListener(v->dialog.getButton(-1).setOnClickListener(button->{String value=url.getText().toString().trim();try{SkipSources.requireHttps(value);}catch(Exception e){url.setError(e.getMessage());return;}dialog.dismiss();work(()->SkipSources.download(value),document->preview(document,value));}));DialogUi.show(dialog);
    }
    void importLocal(){
        LinearLayout box=DialogUi.content(activity);AlertDialog dialog=DialogUi.builder(activity).setTitle("导入本地规则").setView(box).setNegativeButton("取消",null).create();
        DialogUi.choice(box,"从文件导入",()->{dialog.dismiss();activity.startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE),IMPORT);});
        DialogUi.choice(box,"粘贴或新建",()->{dialog.dismiss();edit(null);});DialogUi.show(dialog);
    }
    void updateAll(){work(repository::updateAll,result->{changed.run();notice(result);});}
    void update(long id){work(()->repository.update(id),value->{changed.run();notice(value?"订阅已更新，手动开关已保留":"已是最新版本");});}
    void delete(long id){
        SkipSource source=repository.getSources().find(id);
        DialogUi.builder(activity).setTitle("删除 "+source.getName()+"？").setMessage("该订阅的规则及手动开关将一并移除。").setNegativeButton("取消",null).setPositiveButton("删除",(d,w)->work(()->{repository.getSources().remove(id);return "已删除订阅";},result->{changed.run();notice(result);})).show();
    }
    void edit(Long id){
        SkipSource source=id==null?null:repository.getSources().find(id);
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
    private <T> void work(Work<T> task,Done<T> done){
        if(busy){notice("正在处理，请稍候");return;}busy=true;progress.run();
        SkipRepository.executor.execute(()->{try{T value=task.run();activity.runOnUiThread(()->{busy=false;if(!alive())return;progress.run();done.accept(value);});}catch(Exception error){activity.runOnUiThread(()->{busy=false;if(!alive())return;progress.run();DialogUi.builder(activity).setTitle("未完成操作").setMessage(error.getMessage()==null?error.getClass().getSimpleName():error.getMessage()).setPositiveButton("知道了",null).show();});}});
    }
    private boolean alive(){return !closed&&!activity.isFinishing()&&!activity.isDestroyed();}
    private void notice(String value){if(alive()&&!value.isEmpty())Toast.makeText(activity,value,Toast.LENGTH_LONG).show();}
    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
    @Override public void close(){closed=true;}
}
