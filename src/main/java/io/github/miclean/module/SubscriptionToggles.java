package io.github.miclean.module;

import android.os.Handler;
import android.os.Looper;
import java.util.concurrent.Executors;
import io.github.miclean.module.skip.SkipRepository;

/** Application lifetime: pending intents survive a panel or Activity being recreated. */
final class SubscriptionToggles {
    private static LatestToggleWrites instance;
    static synchronized LatestToggleWrites get(SkipRepository repository){
        if(instance==null){
            Handler main=new Handler(Looper.getMainLooper());
            instance=new LatestToggleWrites(new LatestToggleWrites.Store(){
                public boolean read(long id){try{return repository.getSources().find(id).getEnabled();}catch(IllegalArgumentException deleted){return false;}}
                public void write(long id,boolean value){repository.getSources().enable(id,value);}
            },Executors.newSingleThreadExecutor(task->new Thread(task,"NoAdMi-subscription-switches")),task->main.post(task));
        }
        return instance;
    }
    private SubscriptionToggles(){}
}
