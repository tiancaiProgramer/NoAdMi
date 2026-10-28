package io.github.miclean.module;

import java.util.ArrayList;
import java.util.List;

/** Bounded, lossless UTF-16 slices; even minified documents cannot create one giant row. */
public final class DocumentPages {
    public static final int PAGE_CHARS=2048;
    private final String document;
    private final int[] ends;
    public DocumentPages(String document){
        this.document=document;List<Integer> positions=new ArrayList<>();int start=0;
        while(start<document.length()){
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            int end=Math.min(document.length(),start+PAGE_CHARS);
            if(end<document.length()){
                for(int i=end-1;i>=start+PAGE_CHARS/2;i--)if(document.charAt(i)=='\n'){end=i+1;break;}
                if(Character.isHighSurrogate(document.charAt(end-1))&&Character.isLowSurrogate(document.charAt(end)))end--;
            }
            positions.add(end);start=end;
        }
        ends=new int[positions.size()];for(int i=0;i<ends.length;i++)ends[i]=positions.get(i);
    }
    public int size(){return ends.length;}
    public String page(int index){return document.substring(index==0?0:ends[index-1],ends[index]);}
    public int length(){return document.length();}
}
