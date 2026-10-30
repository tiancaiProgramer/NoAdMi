package io.github.miclean.tests;
import android.app.*;import android.content.*;import android.os.*;import java.io.*;import java.util.*;import org.json.*;import io.github.miclean.module.skip.*;
/** Fixed fixture IDs only; backs up settings, never enables or edits user subscriptions. */
public final class AppManagementEngineSetup extends Instrumentation {
    private static final long A=-202610300210L,B=-202610300211L;private static final String PKG="io.github.miclean.skipfixture";private String phase;
    @Override public void onCreate(Bundle args){super.onCreate(args);phase=args.getString("phase","setup");start();}
    @Override public void onStart(){Bundle out=new Bundle();try{SkipRepository r=SkipRepository.get(getTargetContext());SharedPreferences p=r.getPreferences(),saved=getTargetContext().getSharedPreferences("app_management_engine_backup",0);String[] keys={"enabled","access_method",SkipInputMode.KEY};
        if("setup".equals(phase)){
            if(saved.contains("prepared"))throw new AssertionError("Existing test backup");if(r.getSources().list().stream().anyMatch(s->s.getId()==A||s.getId()==B))throw new AssertionError("Fixture source exists");
            SharedPreferences.Editor e=saved.edit().putBoolean("prepared",true).putBoolean("had_white",p.contains(SkipRepository.GLOBAL_WHITELIST)).putBoolean("was_white",r.isWhitelisted(PKG));
            for(String k:keys){e.putBoolean("has_"+k,p.contains(k));if(k.equals("enabled"))e.putBoolean(k,p.getBoolean(k,false));else if(k.equals(SkipInputMode.KEY))e.putInt(k,p.getInt(k,0));else e.putString(k,p.getString(k,"root"));}e.commit();
            r.getSources().add("{id:"+A+",name:'r2引擎隔离A',version:1,apps:[{id:'"+PKG+"',groups:[{key:1,name:'r2专属验证',actionMaximum:1,rules:[{matches:'[text=\"重复场景目标\"]',action:'clickNode'}]}]}]}","",true);
            r.getSources().add("{id:"+B+",name:'r2引擎隔离B',version:1,globalGroups:[{key:2,name:'r2全局验证',apps:[{id:'"+PKG+"'}],matchAnyApp:false,actionMaximum:1,rules:[{matches:'[text=\"重复场景目标\"]',action:'clickNode'}]}]}","",true);
            p.edit().putBoolean("enabled",true).putString("access_method","root").putInt(SkipInputMode.KEY,0).commit();r.setWhitelisted(PKG,true);
        }else{
            if(!saved.getBoolean("prepared",false))throw new AssertionError("No prepared test backup");
            if("global_block".equals(phase))r.setWhitelisted(PKG,true);
            else if("source_b".equals(phase)){r.setWhitelisted(PKG,false);r.getSources().enableApp(A,PKG,false);}
            else if("both_blocked".equals(phase)){r.getSources().enableGlobalForApp(B,PKG,2,false);}
            else if("source_a".equals(phase)){r.getSources().enableApp(A,PKG,true);}
            else if("cleanup".equals(phase)){
                for(long id:new long[]{A,B})if(r.getSources().list().stream().anyMatch(s->s.getId()==id))r.getSources().remove(id);
                r.setWhitelisted(PKG,saved.getBoolean("was_white",false));SharedPreferences.Editor e=p.edit();if(!saved.getBoolean("had_white",false))e.remove(SkipRepository.GLOBAL_WHITELIST);
                for(String k:keys){if(!saved.getBoolean("has_"+k,false))e.remove(k);else if(k.equals("enabled"))e.putBoolean(k,saved.getBoolean(k,false));else if(k.equals(SkipInputMode.KEY))e.putInt(k,saved.getInt(k,0));else e.putString(k,saved.getString(k,"root"));}e.commit();saved.edit().clear().commit();
                File f=new File(getTargetContext().getFilesDir(),"smart_skip/actions.json");if(f.exists()){JSONArray all=new JSONArray(new String(java.nio.file.Files.readAllBytes(f.toPath()),java.nio.charset.StandardCharsets.UTF_8)),kept=new JSONArray();for(int i=0;i<all.length();i++){long id=all.getJSONObject(i).optLong("sourceId",0);if(id!=A&&id!=B)kept.put(all.getJSONObject(i));}android.util.AtomicFile atomic=new android.util.AtomicFile(f);FileOutputStream stream=atomic.startWrite();try{stream.write(kept.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));atomic.finishWrite(stream);}catch(Exception error){atomic.failWrite(stream);throw error;}}
            }else throw new AssertionError("Unknown phase");
        }
        out.putString("stream","PASS: r2 fixed fixture phase "+phase+"\n");
    }catch(Throwable e){out.putString("stream","FAIL: "+e+"\n");}finish(out.getString("stream","").startsWith("PASS:")?Activity.RESULT_OK:Activity.RESULT_CANCELED,out);}
}
