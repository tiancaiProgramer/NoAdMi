package io.github.miclean.module;

import java.util.*;
import java.util.concurrent.*;

public final class LatestToggleWritesTest {
    static int checks;
    static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
    static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks=new ArrayDeque<>();
        public void execute(Runnable task){tasks.add(task);}
        void one(){tasks.remove().run();}
        void drain(){while(!tasks.isEmpty())one();}
    }
    static final class Store implements LatestToggleWrites.Store {
        final Map<Long,Boolean> disk=new HashMap<>();int writes;boolean fail;Runnable duringWrite;
        public boolean read(long id){return Boolean.TRUE.equals(disk.get(id));}
        public void write(long id,boolean value)throws Exception{writes++;if(duringWrite!=null){Runnable action=duringWrite;duringWrite=null;action.run();}if(fail)throw new Exception("disk failure");disk.put(id,value);}
    }
    public static void main(String[] args)throws Exception{
        Store store=new Store();Queue writer=new Queue(),ui=new Queue();LatestToggleWrites state=new LatestToggleWrites(store,writer,ui);List<String> events=new ArrayList<>();
        LatestToggleWrites.Listener listener=(id,error)->events.add(id+":"+(error==null?"ok":"error"));state.observe(listener);
        state.request(1,true);check(state.value(1)&&state.pending(1),"Intent not visible before disk save");check(!state.value(2),"Other subscription changed");
        state.request(1,false);state.request(1,true);check(state.value(1),"Last rapid intent lost");writer.drain();ui.drain();check(store.read(1)&&store.writes==1&&!state.anyPending(),"Superseded writes not coalesced");
        check(events.stream().noneMatch(e->e.startsWith("2:")),"Unrelated subscription notified");
        store.duringWrite=()->state.request(1,true);state.request(1,false);writer.one();check(state.value(1)&&state.pending(1),"Old completion overwrote new intent");writer.drain();ui.drain();check(store.read(1)&&!state.anyPending(),"Newest write did not finish");
        state.request(1,false);state.request(2,true);writer.one();check(!state.value(1)&&state.value(2)&&state.pending(2),"Independent pending state lost");writer.drain();ui.drain();check(!store.read(1)&&store.read(2),"Independent disk values incorrect");
        store.fail=true;state.request(1,true);writer.drain();check(!state.value(1)&&!state.pending(1),"Failure did not roll back to persisted value");ui.drain();check(events.get(events.size()-1).equals("1:error"),"Latest failure not reported");
        events.clear();state.request(1,true);writer.drain();store.fail=false;state.request(1,true);ui.drain();check(events.stream().noneMatch(e->e.endsWith("error")),"Stale error reverted later request");writer.drain();ui.drain();check(store.read(1),"Retry failed");
        events.clear();state.request(1,false);state.remove(listener);List<Boolean> replacement=new ArrayList<>();state.observe((id,error)->replacement.add(state.value(id)));check(!state.value(1),"New UI lost pending state");writer.drain();ui.drain();check(events.isEmpty()&&!replacement.isEmpty()&&!replacement.get(replacement.size()-1),"Detached UI was notified or new UI failed to update");
        ExecutorService serial=Executors.newSingleThreadExecutor();Map<Long,Boolean> saved=new ConcurrentHashMap<>();LatestToggleWrites stress=new LatestToggleWrites(new LatestToggleWrites.Store(){public boolean read(long id){return Boolean.TRUE.equals(saved.get(id));}public void write(long id,boolean value){saved.put(id,value);}},serial,Runnable::run);
        for(int i=0;i<1000;i++){stress.request(i%2,(i/2)%2==0);}stress.request(0,true);stress.request(1,false);serial.shutdown();check(serial.awaitTermination(10,TimeUnit.SECONDS),"Concurrent saves stalled");check(stress.value(0)&&!stress.value(1)&&Boolean.TRUE.equals(saved.get(0L))&&Boolean.FALSE.equals(saved.get(1L))&&!stress.anyPending(),"Last intent and persisted state diverged under load");
        System.out.println("PASS: "+checks+" subscription toggle coalescing, delayed completion, independent state, failure rollback, observer lifecycle and concurrent last-intent checks");
    }
}
