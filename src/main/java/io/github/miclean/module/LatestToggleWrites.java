package io.github.miclean.module;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;

/** Keeps the last user intent visible while ordered, coalesced disk writes finish. */
final class LatestToggleWrites {
    interface Store { boolean read(long id); void write(long id,boolean value)throws Exception; }
    interface Listener { void changed(long id,Exception error); }
    private static final class Change {
        final long id;final boolean value;
        Change(long id,boolean value){this.id=id;this.value=value;}
    }
    private final Store store;
    private final Executor writer,callbacks;
    private final Map<Long,Change> pending=new HashMap<>(),latest=new HashMap<>();
    private final CopyOnWriteArrayList<Listener> listeners=new CopyOnWriteArrayList<>();
    LatestToggleWrites(Store store,Executor writer,Executor callbacks){this.store=store;this.writer=writer;this.callbacks=callbacks;}
    synchronized boolean value(long id){Change change=pending.get(id);return change==null?store.read(id):change.value;}
    synchronized boolean pending(long id){return pending.containsKey(id);}
    synchronized boolean anyPending(){return !pending.isEmpty();}
    void observe(Listener listener){listeners.addIfAbsent(listener);}
    void remove(Listener listener){listeners.remove(listener);}
    void request(long id,boolean value){
        Change change=new Change(id,value);synchronized(this){pending.put(id,change);latest.put(id,change);}dispatch(change,null);
        try{writer.execute(()->write(change));}catch(RuntimeException rejected){finish(change,rejected);}
    }
    private void write(Change change){
        synchronized(this){if(pending.get(change.id)!=change)return;}
        Exception error=null;try{store.write(change.id,change.value);}catch(Exception failure){error=failure;}finish(change,error);
    }
    private void finish(Change change,Exception error){
        synchronized(this){if(pending.get(change.id)!=change)return;pending.remove(change.id);}dispatch(change,error);
    }
    private void dispatch(Change change,Exception error){
        callbacks.execute(()->{synchronized(this){if(latest.get(change.id)!=change)return;}for(Listener listener:listeners)listener.changed(change.id,error);});
    }
}
