/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
/**
 * A representation for a slide set
 */

package com.sra.brieflet;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.preference.PreferenceManager;
import android.view.View;

import com.sra.brieflet.actions.SlideAction;
import com.sra.brieflet.actions.SlideIntentAction;
import com.sra.brieflet.util.DialogUtil;
import com.sra.brieflet.util.MLog;

public class SlideSet {
  
  static class IndexEntry {
    String slideName; //w/o extension
    Rect rect;
    String text;
    
    public IndexEntry(String slideName,String text,Rect rect) {
      this.slideName=slideName;
      this.text=text;
      this.rect=rect;
    }
  }

  private String name;
  /** title of slide set */
  private String zipname;
  /** full path to zip file (may have .blt extension) */
  private int numSlides;
  /** default transition for slideset (if any, null otherwise) */
  private String defaultTransition=null;
  /** specific slide transitions (if any) */
  private Map<String, String> slideTransitions=new HashMap<String, String>();
  /** number of slides */
  private Properties props;
  /** properties of the slide set */
  private Map<String, List<SlideAction>> actionMap=new HashMap<String, List<SlideAction>>();
  /** actions associated with each slide */
  private Map<String, Bitmap> slideBitmaps=new HashMap<String, Bitmap>();
  /** bitmaps for each slide */
  private LinkedList<String> slideOrder=new LinkedList<String>();
  /** an ordered list of slide names */
  private Set<String> hiddenSlides=new HashSet<String>();
  private Set<String> tags=new HashSet<String>();
  private Map<String, SlideAction> bitmapIdToAction=new HashMap<String, SlideAction>();
  private List<IndexEntry> index=new ArrayList<IndexEntry>();

  public SlideSet(String zipname,Properties props) {
    this.zipname=zipname;
    name=props.getProperty("title");
    // numSlides=Integer.parseInt(props.getProperty(".numslides"));
    this.props=props;
    parseMeta(props);
    if (SlideLoader.lastIndex!=null) {
      index=SlideLoader.lastIndex;
    }
  }
  
  public List<IndexEntry> getIndex() {
    return(index);
  }

  public void setTransitionNoSave(View view, String slideName, String transName){
	  if (transName.equals("Use Default")) {
	      slideTransitions.remove(slideName);

	    } else {
	      slideTransitions.put(slideName,transName);
	      updatePropsAndSave(view,null);
	    }
  }
  public void setTransition(View view,String slideName,String transName) {
    if (transName.equals("Use Default")) {
      slideTransitions.remove(slideName);
      updatePropsAndSave(view,null);
    } else {
      slideTransitions.put(slideName,transName);
      updatePropsAndSave(view,null);
    }
  }

  public void setTransition(View view,String transName) {
    if (transName.equals("Use Default")) {
      defaultTransition=null;
      updatePropsAndSave(view,null);
    } else {
      defaultTransition=transName;
      updatePropsAndSave(view,null);
    }
  }

  public String getTransitionName(Context context,int num) {
    String name=getSlideName(num);
    if (name==null)
      return("fade"); // shouldn't happen but just in case
    return(getTransitionName(context,name));
  }

  public String getTransitionName(Context context,String slideName) {
    String name=slideTransitions.get(slideName);
    if (name!=null)
      return(name); // slide specific
    if (defaultTransition!=null)
      return(defaultTransition); // presentation specific
    SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(context); // application wide
    String defaultTransition=prefs.getString("defaultTransition","flip");
    return(defaultTransition);
  }

  public SlideAction getActionForId(String id) {
    return(bitmapIdToAction.get(id));
  }

  public Set<String> getActionIds() {
    return(bitmapIdToAction.keySet());
  }

  public void addAction(int num,SlideAction sa) {
    addAction(getSlideName(num),sa);
  }

  private String makeBitmapName() {
    int c=1;
    while (true) {
      String name=".Image-"+c;
      //added logic to make sure no collisions even with a deleted name from the zip (so overwrite is guaranteed)
      if ((bitmapIdToAction.get(name)==null) && !SlideLoader.existsInChangedOrZip(new File(getZipName()),name)) {
        return(name);
      }
      c++;
    }
  }

  public void addAction(String slide,SlideAction sa) {
    List<SlideAction> list=actionMap.get(slide);
    if (list==null) {
      list=new ArrayList<SlideAction>();
      actionMap.put(slide,list);
    }
    if (sa.getBitmapName()!=null) {
      // MLog.v("addAction bitmap has name,making a map entry");
      bitmapIdToAction.put(sa.getBitmapName(),sa);
    } else if (sa.getBitmap()!=null) {
      // MLog.v("addAction bitmap exists but no name yet, making one");
      String name=makeBitmapName(); //maybe don't give a name if it's not going to be saved (based on type)
      sa.setBitmapName(name);
      // MLog.v("addAction setting new bitmap name of "+name);
      bitmapIdToAction.put(name,sa);
    }
    list.add(sa);
  }

  public void addActionAndSave(View view,String slide,SlideAction sa,Runnable run) {
    addAction(slide,sa);
    updateProps();
    save(view,run);
  }

  public void updatePropsAndSave(View view,Runnable run) {
    updateProps();
    save(view,run);
  }

  public List<SlideAction> getSlideActions(int num) {
    return(getSlideAction(getSlideName(num)));
  }

  public List<SlideAction> getSlideAction(String name) {
    return(actionMap.get(name));
  }

  public void addBitmap(String name,Bitmap bitmap) {
    slideBitmaps.put(name,bitmap);
  }

  public void setBitmap(int num,Bitmap bitmap) {
    slideBitmaps.put(getSlideName(num),bitmap);
  }

  public Bitmap getBitmap(String name) { // this is the single call for thumbnails if dynamic overlay is desired -- SWB
    return(slideBitmaps.get(name));
  }

  public Bitmap getBitmap(int num) {
    return(getBitmap(getSlideName(num)));
  }

  public void clearBitmaps() {
    slideBitmaps.clear();
    for(String id:getActionIds()) {
      SlideAction sa=getActionForId(id);
      if (sa.getBitmap()!=null) {
        sa.setBitmap(null);
        sa.setBitmapName(null);
      }
    }
  }

  public boolean isHidden(String slideName) {
    return(hiddenSlides.contains(slideName));
    // return(props.getProperty("hidden."+slideName)!=null);
  }

  /*
   * This should be the only place in the system where a translation is made from a relative slide number to the slide name. The
   * slide name is identical to the file name inside the zip of the slide's image.
   */
  public String getSlideName(int num) {
    if (num>(slideOrder.size()-1))
      return(null); // off end of sequence
    return(slideOrder.get(num));
  }

  public int getSlideNumber(String name) {
    return(slideOrder.indexOf(name));
  }

  public boolean hasSlide(String slideName) {
    return(slideOrder.contains(slideName));
  }

  public boolean isHidden(int num) {
    return(isHidden(slideOrder.get(num)));
  }

  /**
   * Give a slide number 0-n and a direction return the next non-hidden slide number in that direction. It will return the same
   * number if no further move in that direction is possible.
   * 
   * @param num
   *          current slide number
   * @param prev
   *          true if moving back, false if moving forward
   * @return next non hidden slide
   */
  public int nextNonHiddenSlide(int num,boolean prev) {
    int curSlide=num;
    if (prev) {
      if (num>0)
        num--;
      while (num>=0&&isHidden(num)) {
        num--;
      }
      if (num==-1)
        num=curSlide;
      return(num);
    } else {
      if (num<(numSlides-1))
        num++;
      while (num<numSlides&&isHidden(num)) {
        num++;
      }
      if (num==numSlides)
        num=curSlide;
      return(num);
    }
  }

  public void setHidden(View view,int num,boolean hide) {
    setHidden(view,getSlideName(num),hide);
  }

  public void rename(View view,String title,Runnable runnable) {
    name=title;
    props.setProperty("title",title);
    save(view,runnable);
  }

  public void setHidden(View view,String slideName,boolean hide) {
    setHiddenNoSave(view, slideName, hide);
    MLog.v("setHidden and now doing save(view)");
    save(view);
  }
  public void setHiddenNoSave(View view, String slideName, boolean hide){
      if (hide) {
          hiddenSlides.add(slideName);
          props.setProperty("hidden."+stripExtension(slideName),"true");
        } else {
          hiddenSlides.remove(slideName);
          props.remove("hidden."+stripExtension(slideName));
        }
  }

  public void save(View view) {
    save(view,null);
  }

  public void save(View view,Runnable runnable) {
    save(view,null,runnable);
  }

  public void save(View view,final Set<String> skip,final Runnable runnable) {
    save(view,skip,null,null,runnable);
  }

  public void save(final View view,final Set<String> skip,final String name,final Bitmap bitmap,final Runnable runnable) {
    final SlideSet ss=this;
    (new Thread() {

      public void run() {
        SlideLoader.isDone=false;
        DialogUtil.showBusyCursor(view,"Updating Presentation...","Please wait...",null);
        SlideLoader.copyZipWithMods(ss,skip,name,bitmap); // might need to prevent two of these overlapping (or queue a
        // rewrite after a short interval once marked dirty)
        if (runnable!=null)
          runnable.run();
      }
    }).start();
  }
  
  public void saveAll(final View view, final Set<String> skip,final String[] names, final List<SlideInfo> slides, final Runnable runnable) {
      final SlideSet ss=this;
      (new Thread() {

        public void run() {
          SlideLoader.isDone=false;
          DialogUtil.showBusyCursor(view,"Updating Presentation...","Please wait...",null);
          SlideLoader.copyAllToZipWithMods(ss,skip,names,slides); // might need to prevent two of these overlapping (or queue a
          // rewrite after a short interval once marked dirty)
          if (runnable!=null)
            runnable.run();
        }
      }).start();
    }

  /**
   * Get a correct slide for a viewer to show if there have been edits.
   * 
   * @param num
   *          slide number viewer thinks its on
   * @param name
   *          slide name viewer thinks its on
   * @return
   */
  public int getCorrectSlide(int num,String name) {
    if (slideOrder.contains(name)) {
      return(getSlideNumber(name)); // find the new position of that slide
    } else {
      if (num>(getNumSlides()-1)) {
        num=getNumSlides()-1;
        if (!isHidden(num))
          return(num);
      }
      int nxt=nextNonHiddenSlide(num,true); // find earliest non-hidden
      if (nxt==num) { // if we didn't move
        nxt=nextNonHiddenSlide(num,false); // move later to find out (if possible)
      }
      return(nxt);
    }
  }
  
  public boolean exists(String prefix) {
    for(String name:slideOrder) {
      if (name.startsWith(prefix)) return(true);
    }
    return(false);
  }

  public String getUniqueSlideName() {
    int cnt=0;
    String prefix="Slide";
    while (true) {
      cnt++;
      String name=prefix+cnt+".";
      //added extra clause to ensure no collision even with old deleted slide in zip (before pack)
      if (!exists(name) && !SlideLoader.existsInChangedOrZip(new File(getZipName()),name+"PNG")) {
        return(name+"PNG");
      }
    }
  }
  
  /*
  public String getUniqueSlideName2() {
    int cnt=0;
    String prefix="Slide";
    String ext=".PNG";
    String ext2=".png"; //really should make sure stem isn't taken already
    while (true) {
      cnt++;
      String name=prefix+cnt+ext;
      if (!slideOrder.contains(name)&&!slideOrder.contains(prefix+cnt+ext2))
        return(name);
    }
  }
  */

  public List<SlideAction> getActionsForIntent(Intent intent) {
    String type=intent.getExtras().getString("type");
    List<SlideAction> actions= new ArrayList<SlideAction>();
    int down=8;
    SlideLoader sl=SlideLoader.getDefaultInstance();
    if (sl!=null)
         down=sl.getDownSampleSize();
    if (type.equals("link")) {
      String slidePath=intent.getExtras().getString("slidePath");
      String slideName="";
      int numSlides= intent.getExtras().getInt("numSlides");
      for(int x=0;x<numSlides;x++){
          slideName=intent.getExtras().getString("slideName"+x);
          
          
          Bitmap bmap=SlideLoader.getThumbnail(slidePath,slideName,down);
          SlideInfo si = new SlideInfo(bmap);
          int numActions = intent.getExtras().getInt("numActions"+x);
          for(int y=0; y<numActions; y++){
              SlideAction a = SlideAction.createInstance(intent.getExtras().getString("slideAction"+x+""+y));
              if (a.getBitmapName()!=null) {
                //add this to prevent failed lookups
                MLog.v("Eliminate embedded bitmap in thumbnail of thumbnail");
                a.setBitmapName(null);
              }
              MLog.v("Adding Actions to slide info");
              si.addAction(a);
          }
          Bitmap b= makeThumbnailWithActions(bmap, si);
          SlideAction sa=new SlideIntentAction("intent|com.sra.brieflet.SlideViewer|slide://"+slidePath+"#"+slideName+"|0|0|"
              +b.getWidth()+"|"+b.getHeight()+"|1");
          //danger, sa may have an old bitmap name from the previous presentation, when it saves it into the new presentation
          //it will have the wrong name -- SWB -- need a unique name NOW
          //the line below should fix it
          MLog.v("nulling bitmap name so it will be forced unique");
          sa.setBitmapName(null); //this will force it to make a unique name when addAction is eventually called on the return -- SWB          
          sa.setBitmap(b); //this is why we have a real bitmap for this thumbnail -- because we wanted it rendered with actions -- SWB
          actions.add(sa);
      }
    } else if (type.equals("generic")) {
      String props=intent.getExtras().getString("propstring");
      Bitmap bm=(Bitmap)intent.getExtras().get("bitmap");
      SlideAction sa=SlideAction.createInstance(props);
      MLog.v("nulling bitmap name so it will be forced unique");
      sa.setBitmapName(null); //force it to make a new unique name
      sa.setBitmap(bm);
      actions.add(sa);
      //return(sa);
    } else {
      return(null);
    }
    return actions;
  }

  public void insertAction(View view,Intent intent,int num,Runnable runnable) {
    List<SlideAction> actions=getActionsForIntent(intent);
    for(SlideAction sa: actions){
        String slide=getSlideName(num);
        addAction(slide,sa);
    }
    
    updateProps();
    save(view,runnable);
  }

  public void deleteAction(View view,int num,SlideAction action,Runnable runnable) {
    String name=getSlideName(num);
    actionMap.get(name).remove(action);
    String id=action.getBitmapName();
    if (id!=null) {
      bitmapIdToAction.remove(id);
      //should probably remove from changed if present
      File file=SlideLoader.getChangedFile(getZipName(),id,false);
      if (file.exists()) {
        file.delete(); //new, get rid of stored bitmap for action when it is deleted if in changed directory
      }
    }
    updateProps();
    save(view,runnable);
  }
  
  public String insertNoSave(int num,SlideInfo si,boolean before){
      String unique=getUniqueSlideName();
      if (before) {
        slideOrder.add(num,unique);
      } else {
        if (num==(getNumSlides()-1)) {
          slideOrder.add(unique); // at end
        } else {
          slideOrder.add(num+1,unique);
        }
      }
      numSlides++;
      List<SlideAction> actions=si.getActions();
      if (actions!=null) {
        for(SlideAction action:actions) {
          addAction(unique,action);
        }
      }
      return unique;
  }
  public Bitmap updateThumbnail(SlideInfo si, String unique, boolean needBitmap){
     
      Bitmap b=si.getBitmapFromFile();
      Bitmap thumb=makeThumbnailWithActions(b, si);
      addBitmap(unique,thumb);
      
      if(needBitmap){
          return b;
      }else{
          b.recycle();
          return null;
      }
      
  }
  private Bitmap makeThumbnailWithActions(Bitmap b, SlideInfo si){
   int down=8;
   SlideLoader sl=SlideLoader.getDefaultInstance(); // to avoid instantiating one if we don't have one yet
   if (sl!=null)
        down=sl.getDownSampleSize();
   
    Bitmap background=Bitmap.createBitmap(1280/down,SlideLoader.theight/down,Config.ARGB_8888);
    Canvas c=new Canvas(background);
    c.drawBitmap(b,null,new Rect(0,0,1280/down,SlideLoader.theight/down),new Paint());
    List<SlideAction> csactions=si.getActions();
    
    if (csactions!=null) {
        for(SlideAction action:csactions) {
          if (action.supportsThumbnail()&&action.isThumbVisible()) {
            Rect bnds=action.getBounds();
            sl.drawActionThumbnail(action,c,new Rect(bnds.left/down,bnds.top/down,bnds.right/down,
                bnds.bottom/down),false);
          }
        }
      }
    return background;
    
  }

  public void insert(View view,int num,SlideInfo si,boolean before,Runnable runnable) {
      /*
    String name=getUniqueSlideName();
    if (before) {
      slideOrder.add(num,name);
    } else {
      if (num==(getNumSlides()-1)) {
        slideOrder.add(name); // at end
      } else {
        slideOrder.add(num+1,name);
      }
    }
    numSlides++;
    List<SlideAction> actions=si.getActions();
    if (actions!=null) {
      for(SlideAction action:actions) {
        addAction(name,action);
      }
    }
    updateProps();
    // update thumbnail without a re-read using the more expensive technique since we already have it as a bitmap
    int down=8;
    SlideLoader sl=SlideLoader.getDefaultInstance(); // to avoid instantiating one if we don't have one yet
    if (sl!=null)
      down=sl.getDownSampleSize();
    Bitmap b=si.getBitmap();
    Bitmap thumb=Bitmap.createScaledBitmap(b,b.getWidth()/down,b.getHeight()/down,true);
    addBitmap(name,thumb);*/
    String unique=insertNoSave(num,si,before);
    updateProps();
    Bitmap b=updateThumbnail(si, unique, true);
    save(view,null,unique,b,runnable);
  }

  public void deleteSlide(View view,String slideName) {
    deleteSlide(view,slideName,null);
  }

  public void deleteSlide(View view,int num) {
    deleteSlide(view,getSlideName(num));
  }

  public void appendSlideName(String slideName) {
    slideOrder.add(slideName);
    numSlides++;
    updateProps();
  }

  public void setTitle(String name) {
    this.name=name; // caution: doesn't force a save, used in conjunction with SlideLoader save operations
  }

  public void deleteSlide(View view,int num,Runnable runnable) {
    deleteSlide(view,getSlideName(num),runnable);
  }

  public void deleteSlide(View view,String slideName,Runnable runnable) {
    deleteSlideNoSave(slideName);
    Set<String> skiplist=new HashSet<String>();
    skiplist.add(slideName);
    saveRemove(view, skiplist, runnable);
    
  }
  public void saveRemove(View view,Set<String> skiplist,Runnable runnable){
      updateProps();
      save(view,skiplist,runnable);
  }
  public void deleteSlideNoSave(String slideName){
      slideOrder.remove(slideName); // remove it from the order
      actionMap.remove(slideName); // remove actions if any
      slideBitmaps.remove(slideName); // remove bitmap if loaded
      numSlides--; // decrement number of slides
      //updateProps(); // update props for write
      //Set<String> skiplist=new HashSet<String>();
      //skiplist.add(slideName);
      //return skiplist;
  }

  public void updateProps() {
    props.clear(); // clear to start with
    // update title
    props.setProperty("title",name);
    // update order
    StringBuilder sb=new StringBuilder();
    int c=0;
    for(String name:slideOrder) {
      if (c++>0)
        sb.append(",");
      sb.append(name);
    }
    props.setProperty("order",sb.toString());
    // write hiddens
    for(String slidename:hiddenSlides) {
      String base=stripExtension(slidename);
      props.setProperty("hidden."+base,"true");
    }
    // write transitions
    for(String slidename:slideTransitions.keySet()) {
      String base=stripExtension(slidename);
      props.setProperty("transition."+base,slideTransitions.get(slidename));
    }
    // write default transition
    if (defaultTransition!=null) {
      props.setProperty("default.transition",defaultTransition);
    }
    // write tags
    sb=new StringBuilder();
    c=0;
    for(String name:tags) {
      if (c++>0)
        sb.append(",");
      sb.append(name);
    }
    props.setProperty("tags",sb.toString());
    // write action map
    MLog.v("writing action map");
    for(String slideName:actionMap.keySet()) {
      String slideName2=stripExtension(slideName);
      List<SlideAction> actions=actionMap.get(slideName);
      if (actions.size()>0) {
        sb=new StringBuilder();
        c=0;
        for(SlideAction action:actions) {
          if (c++>0)
            sb.append(",");
          sb.append("action"+c);
          props.setProperty(slideName2+".action.action"+c,action.getPropertyString());
          MLog.v("writing:"+action.getPropertyString());
        }
        props.setProperty(slideName2+".actions",sb.toString());
      }
    }
  }

  public List<SlideAction> getActions(String slide) {
    return(actionMap.get(slide));
  }

  public SlideAction getActionAtXY(int num,int x,int y) {
    return(getActionAtXY(getSlideName(num),x,y));
  }

  public SlideAction getActionAtXY(String slide,int x,int y) {
    List<SlideAction> actions=getActions(slide);
    MLog.v("getActionAtXY "+slide+" actions="+actions);
    if (actions==null)
      return(null);
    SlideAction best=null;
    int size=Integer.MAX_VALUE;
    for(SlideAction action:actions) {
      Rect bounds=action.getBounds();
      if (bounds.contains(x,y)) {
        int area=bounds.width()*bounds.height();
        if (area<size) {
          size=area;
          best=action;
        }
      }
    }
    return(best);
  }

  public List<SlideAction> getActions(int num) {
    return(actionMap.get(getSlideName(num)));
  }

  public String getName() {
    return name;
  }

  public String getZipName() {
    return zipname;
  }

  public int getNumSlides() {
    return numSlides;
  }

  /**
   * Scale all appropriate elements as the slide is converted from 1280x<theight> to the smaller given bounding box to preserve
   * aspect.
   * 
   * @param name
   *          name of slide
   * @param left
   *          left coordinate of new slide extent
   * @param top
   *          top of new slide extent
   * @param right
   *          right of new slide extent
   * @param bottom
   *          bottom of new slide extent
   */
  public void scaleSlide(String name,int left,int top,int right,int bottom) {
    List<SlideAction> actions=getActions(name);
    if (actions!=null) {
      for(SlideAction action:actions) {
        Rect rect=action.getBounds();
        float xscale=1f*(right-left)/1280;
        float yscale=1f*(bottom-top)/SlideLoader.theight;
        action.setBounds(new Rect((int)(left+xscale*rect.left),(int)(top+yscale*rect.top),(int)(left+xscale*rect.right),
            (int)(top+yscale*rect.bottom)));
        MLog.v("scaled bounds for "+name+" from "+rect+" to "+action.getBounds());
      }
    }
  }

  public Map<String, List<SlideAction>> getActionMap() {
    return actionMap;
  }

  private void parseMeta(Properties props) {
    // build slideOrder
    String order=props.getProperty("order"); // guaranteed to exist
    slideOrder.clear();
    if (order!=null) {
      String[] slideNames=order.split(",");
      for(String slideName:slideNames) {
        slideOrder.add(slideName);
      }
    }
    numSlides=slideOrder.size();
    // parse tags
    tags.clear();
    String tagsprop=props.getProperty("tags"); // guaranteed to exist
    if (tagsprop!=null) {
      for(String tagName:tagsprop.split(",")) {
        tags.add(tagName.toLowerCase());
      }
    }
    // parse the actions
    actionMap.clear(); // just in case an action gets removed in properties yet remains in the map
    // String slidelist=props.getProperty(".slidelist");
    for(String slidename:slideOrder) {
      String slidename2=stripExtension(slidename);
      String actions=props.getProperty(slidename2+".actions");
      // Brieflet.LogV(slidename+" has actions="+actions);
      if (actions!=null) {
        for(String actionname:actions.split(",")) {
          // Brieflet.LogV(slidename+" has action name="+actionname);
          String action=props.getProperty(slidename2+".action."+actionname);
          // Brieflet.LogV(getName()+": "+slidename+" has action name="+actionname+" action="+action);
          if (action!=null) {
            addAction(slidename,SlideAction.createInstance(action));
          }
        }
      }
    }
    // parse hidden
    for(String slidename:slideOrder) {
      String slidename2=stripExtension(slidename);
      if (props.getProperty("hidden."+slidename2)!=null) {
        hiddenSlides.add(slidename);
      }
      if (props.getProperty("transition."+slidename2)!=null) {
        slideTransitions.put(slidename,props.getProperty("transition."+slidename2,null));
      }
    }
    // parse default transition
    defaultTransition=props.getProperty("default.transition",null);
  }

  public static String stripExtension(String str) {
    int pos=str.lastIndexOf(".");
    if (pos>-1) {
      return(str.substring(0,pos));
    } else {
      return(str);
    }
  }

  public void setProperties(Properties props) {
    parseMeta(props);
    this.props=props;
  }

  public Properties getProperties() {
    return(props);
  }

  public void setProperty(String name,String value) {
    props.setProperty(name,value);
  }
  public Set<String> difference(List<Integer> slidesToKeep){
      Set<String> diff = new HashSet<String>();
      for(int x=0; x< slideOrder.size(); x++){
          if(!slidesToKeep.contains(x)){
              diff.add(slideOrder.get(x));
          }
      }
      return diff;
  }

}