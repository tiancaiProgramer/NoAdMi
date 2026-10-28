package io.github.miclean.tests;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.*;import org.json.*;import io.github.miclean.module.skip.*;
public final class SkipRepeatSetup extends Instrumentation {
    public static final long ID=-202610280129L;private String phase,mode;private int inputMode;
    @Override public void onCreate(Bundle b){super.onCreate(b);phase=b==null?"setup":b.getString("phase","setup");mode=b==null?"node":b.getString("mode","node");inputMode=b==null?0:Integer.parseInt(b.getString("input_mode","0"));start();}
    @Override public void onStart(){Bundle out=new Bundle();try{SkipRepository r=SkipRepository.get(getTargetContext());SharedPreferences p=r.getPreferences(),backup=getTargetContext().getSharedPreferences("repeat_test_backup",0);String[] keys={"enabled",SkipNotice.ENABLED,SkipNotice.TEXT,"access_method",SkipInputMode.KEY};
        if("cleanup".equals(phase)){
            if(!backup.contains("has_enabled"))throw new AssertionError("No prepared backup; refusing to alter user options");
            if(r.getSources().list().stream().anyMatch(s->s.getId()==ID))r.getSources().remove(ID);
            SharedPreferences.Editor e=p.edit();for(String k:keys){if(!backup.getBoolean("has_"+k,false))e.remove(k);else if(k.equals(SkipInputMode.KEY))e.putInt(k,backup.getInt(k,0));else if((k.equals(SkipNotice.TEXT)||k.equals("access_method")))e.putString(k,backup.getString(k,""));else e.putBoolean(k,backup.getBoolean(k,false));}e.commit();backup.edit().clear().commit();
            File f=new File(getTargetContext().getFilesDir(),"smart_skip/actions.json");JSONArray kept=new JSONArray();if(f.exists()){JSONArray rows=new JSONArray(new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8));for(int i=0;i<rows.length();i++)if(rows.getJSONObject(i).optLong("sourceId",0)!=ID)kept.put(rows.getJSONObject(i));android.util.AtomicFile af=new android.util.AtomicFile(f);FileOutputStream stream=af.startWrite();try{stream.write(kept.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));af.finishWrite(stream);}catch(Exception ex){af.failWrite(stream);throw ex;}}
            out.putString("stream","PASS: temporary repeat source, preferences and only its execution records cleaned.\n");
        }else{
            if(r.getSources().list().stream().anyMatch(s->s.getId()==ID))throw new AssertionError("Test ID already used");SharedPreferences.Editor e=backup.edit().clear();for(String k:keys){e.putBoolean("has_"+k,p.contains(k));if(k.equals(SkipInputMode.KEY))e.putInt(k,p.getInt(k,0));else if((k.equals(SkipNotice.TEXT)||k.equals("access_method")))e.putString(k,p.getString(k,""));else e.putBoolean(k,p.getBoolean(k,false));}e.commit();
            String rules=mode.equals("chain")?
                "{key:0,matches:'[text=\"链路第一步\"]',action:'clickNode'},{key:1,preKeys:0,matches:'[text=\"链路第二步\"]',action:'clickNode'}":
                "{matches:'[text=\"重复场景目标\"]',action:'"+(mode.equals("center")?"clickCenter":"clickNode")+"',actionDelay:"+((mode.equals("delay")||mode.equals("center"))?800:0)+"}";
            r.getSources().add("{id:"+ID+",name:'重复场景隔离验证',version:1,apps:[{id:'io.github.miclean.skipfixture',groups:[{key:1,name:'仅测试重复场景',fastQuery:false,matchTime:1500,forcedTime:1500,actionMaximum:1,rules:["+rules+"]}]}]}","",true);
            p.edit().putInt(SkipInputMode.KEY,SkipInputMode.normalize(inputMode)).putString("access_method",mode.equals("center")?"shizuku":"root").putBoolean("enabled",true).putBoolean(SkipNotice.ENABLED,true).putString(SkipNotice.TEXT,"跳过操作验证🙂").commit();out.putString("stream","PASS: isolated enabled fixture source and custom notice prepared; real subscriptions unchanged.\n");
        }
    }catch(Throwable e){out.putString("stream","FAIL: "+e+"\n");}finish(out.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
