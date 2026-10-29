package android.view.accessibility;

import android.graphics.Rect;
import java.util.*;

/** JVM-only platform double: real production AndroidSkipTree is tested against
 * changing backing nodes, copied native handles, counted RPCs and ownership. */
public final class AccessibilityNodeInfo {
    public static int childrenRead, parentsRead, textQueries, idQueries, liveHandles;
    public static boolean failQuery;
    public static final class Data {
        public final int key;
        public String text, id, pkg="test.app";
        public int window=1;
        public Data parent;
        public final List<Data> children=new ArrayList<>();
        public Data(int key,String text,String id){this.key=key;this.text=text;this.id=id;}
        public Data add(Data child){children.add(child);child.parent=this;return this;}
    }
    private final Data data;
    private boolean recycled;
    public AccessibilityNodeInfo(Data data){this.data=data;liveHandles++;}
    private void usable(){if(recycled)throw new AssertionError("Read after recycle");}
    public static AccessibilityNodeInfo obtain(AccessibilityNodeInfo node){node.usable();return new AccessibilityNodeInfo(node.data);}
    public void recycle(){usable();recycled=true;liveHandles--;}
    public AccessibilityNodeInfo getChild(int i){usable();childrenRead++;Data d=data.children.get(i);return d==null?null:new AccessibilityNodeInfo(d);}
    public AccessibilityNodeInfo getParent(){usable();parentsRead++;return data.parent==null?null:new AccessibilityNodeInfo(data.parent);}
    public int getChildCount(){usable();return data.children.size();}
    public int getWindowId(){usable();return data.window;}
    public CharSequence getPackageName(){usable();return data.pkg;}
    public String getViewIdResourceName(){usable();return data.id;}
    public CharSequence getClassName(){usable();return "android.widget.TextView";}
    public CharSequence getText(){usable();return data.text;}
    public CharSequence getContentDescription(){usable();return null;}
    public boolean isClickable(){usable();return true;}
    public boolean isFocusable(){usable();return false;}
    public boolean isCheckable(){usable();return false;}
    public boolean isChecked(){usable();return false;}
    public boolean isEditable(){usable();return false;}
    public boolean isLongClickable(){usable();return false;}
    public boolean isVisibleToUser(){usable();return true;}
    public void getBoundsInScreen(Rect r){usable();r.left=0;r.top=0;r.right=100;r.bottom=100;}
    private void query(Data d,String value,boolean id,List<AccessibilityNodeInfo> out){
        if(d==null)return;
        String s=id?d.id:d.text;
        if(s!=null&&(id?s.equals(value):s.contains(value)))out.add(new AccessibilityNodeInfo(d));
        for(Data child:d.children)query(child,value,id,out);
    }
    public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByText(String text){usable();textQueries++;return find(text,false);}
    public List<AccessibilityNodeInfo> findAccessibilityNodeInfosByViewId(String id){usable();idQueries++;return find(id,true);}
    private List<AccessibilityNodeInfo> find(String value,boolean id){
        if(failQuery){failQuery=false;throw new IllegalStateException("Temporary remote failure");}
        List<AccessibilityNodeInfo> out=new ArrayList<>();query(data,value,id,out);return out;
    }
    @Override public int hashCode(){return data.key;}
    @Override public boolean equals(Object other){return other instanceof AccessibilityNodeInfo&&data==((AccessibilityNodeInfo)other).data;}
    public static void resetCounts(){childrenRead=parentsRead=textQueries=idQueries=0;}
}
