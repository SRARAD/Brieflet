/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Bitmap.Config;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.media.ThumbnailUtils;
import android.preference.PreferenceManager;
import android.provider.MediaStore;
import android.view.View;

import com.sra.brieflet.SlideSet.IndexEntry;
import com.sra.brieflet.actions.LaunchAction;
import com.sra.brieflet.actions.SlideAction;
import com.sra.brieflet.actions.SlideAudioAction;
import com.sra.brieflet.actions.SlideIntentAction;
import com.sra.brieflet.actions.SlideVideoAction;
import com.sra.brieflet.util.BitmapScaler;
import com.sra.brieflet.util.BriefletMediaPlayer;
import com.sra.brieflet.util.DialogUtil;
import com.sra.brieflet.util.IntentUtil;
import com.sra.brieflet.util.MLog;
import com.sra.brieflet.util.MusicUtil;

/**
 * Slide loader is a singleton. It also maintains state about the "current"
 * slide set and serves to deliver individual images from the corresponding
 * archive and to rewrite the archive when changes are made.
 * 
 */

public class SlideLoader {

  // Todo: a specialized BriefletMediaPlayer pointer will live here so it
  // persist across SlideViewer and VideoViewer
  // private String zipPath; //phase out, use slide set instead
  // private String[] slideNames; //phase out, use slide set instead
  // private static Bitmap[] thumbs;
  // (bitmaps too big for bind)
  private BriefletMediaPlayer mp;
  public static boolean isDone=false;
  public static ProgressDialog currentpd=null;
  public static boolean buildingThumbs=false;
  public static boolean abortThumbs=false;
  public SlideGrid activeGrid=null;
  public static Brieflet activeAct=null;
  private Context context;
  // private Activity activity;
  // public Brieflet pres;
  private String slideDirName="/sdcard/Slides";
  private Map<String, SlideSet> slideSetMap;
  static SlideSet currentSlideSet; // this should be used for all stateful
  // queries soon -- SWB
  private static SlideLoader instance;
  private int downsamplesize=8;
  private String filterTag=null;
  public static int theight=752; // 752 or 720
  private Bitmap[] overlayBitmap=new Bitmap[2];
  private int lastOverlay=0;
  public static List<IndexEntry> lastIndex;
  private static Random rand=new Random();
  private Map<SlideAction, Bitmap> thumbCache=new HashMap<SlideAction, Bitmap>(); // need
  
  Object lockIndex=new Object();

  // to
  // manage
  // this
  // cache
  // --
  // SWB

  public static void updateProgressMessage(Activity act,final String msg) {
    if (act!=null) {
      act.runOnUiThread(new Runnable() {

        public void run() {
          if (currentpd!=null) {
            currentpd.setMessage(msg);
          }
        }
      });
    }
  }

  public void shutdown() {
    context=null;
    instance=null;
    currentSlideSet=null;
    if (mp!=null) {
      mp.stop();
      mp.release();
    }
  }

  public void clearCache() {
    thumbCache.clear();
  }

  public BriefletMediaPlayer getMP() {
    if (mp!=null)
      return(mp);
    mp=new BriefletMediaPlayer();
    return(mp);
  }

  public SlideLoader() {}

  /**
   * Get division factor for downsampling thumbnails.
   * 
   * @return division factor for downsampling thumbnails
   */
  public int getDownSampleSize() {
    return(downsamplesize);
  }

  public SlideSet getSlideSetByTitle(String name) {
    //TODO: I saw a concurrent modification exception on slideSetMap here while sharing a video with Brieflet, how?
    //maybe slide list was being refreshed???  -- maybe need to synchronize this
    //really should not do this until index is done
    ensureSlideSetMap();
    synchronized(lockIndex) {
    for(SlideSet ss:slideSetMap.values()) { // avoid reindex for now
      // recently
      //MLog.v("Comparing <"+name+"> to <"+ss.getName()+">"); //comment out to get out quicker at least
      if (ss.getName().equals(name))
        return(ss);
    }
    }
    return(null);
  }

  public SlideLoader(Context context) {
    this.context=context;
    // indexSlideSets(); //this should be subsumed by explicit calls starting
    // with slidelist, but watch for any issues -- SWB
  }

  public SlideSet getCurrentSlideSet() {
    return(currentSlideSet);
  }

  public static synchronized SlideLoader getDefaultInstance(Context context) {
    try {
      if (instance!=null)
        return(instance);
      if (context==null) {
        MLog.v("********SlideLoader getDefaultInstance context = null");
      }
      instance=new SlideLoader(context);
    } catch (Exception e) {
      MLog.e("SlideLoader getDefaultInstance ",e);
      instance=new SlideLoader(context);
    }
    return(instance);
  }

  public static SlideLoader getDefaultInstance() {
    return(instance);
  }

  public void setFilter(String tag) {
    filterTag=tag;
  }

  public Collection<SlideSet> getSlideSets() {
    indexSlideSets(); // do it every time if fast enough
    return(slideSetMap.values());
  }

  public Collection<SlideSet> getSlideSets(Activity act) {
    indexSlideSets(act); // do it every time if fast enough
    return(slideSetMap.values());
  }

  public SlideSet getSlideSetByPath(String path) {
    File file=new File(path);
    return(slideSetMap.get(file.getName()));
  }

  public void addSlideSet(SlideSet ss) {
    File file=new File(ss.getZipName());
    slideSetMap.put(file.getName(),ss);
  }

  private HashSet<String> reported=new HashSet<String>();

  public SlideSet indexSlideSet(File file) {
    Properties props=getProperties(file);
    if (props!=null) {
      String tags=props.getProperty("tags");
      if (filterTag==null) {
        SlideSet ss=new SlideSet(file.getAbsolutePath(),props);
        if (ss.getNumSlides()>0) {
          slideSetMap.put(file.getName(),ss);
          return(ss);
        } else {
          MLog.v("No slides for "+file);
        }
      } else {
        if (tags!=null) {
          for(String tag:tags.split(",")) {
            if (filterTag.toLowerCase().equals(tag.toLowerCase())) {
              SlideSet ss=new SlideSet(file.getAbsolutePath(),props);
              if (ss.getNumSlides()>0) {
                slideSetMap.put(file.getName(),ss);
                return(ss);
              }
            }
          }
        }
      }
    } else {
      MLog.v("Null props for "+file);
    }
    return(null);
  }

  public void indexSlideSets() {
    indexSlideSets(null);
  }

  public void indexSlideSets(Activity act) {
    synchronized(lockIndex) {
    /*
     * if (act!=null) { isDone=false;
     * DialogUtil.showBusyCursor(act,"Scanning for Presentations..."
     * ,"Please wait...",null); }
     */
    slideSetMap=new HashMap<String, SlideSet>();
    File slideDir=new File(slideDirName);
    if (!slideDir.exists()) {
      slideDir.mkdirs(); // make the directory if it doesn't exist before we
      // first read it
    }
    StringBuilder sb=new StringBuilder();
    boolean bad=false;
    File[] files=slideDir.listFiles();
    int tot=files.length;
    int cnt=0;
    for(File file:files) {
      cnt++;
      if (file.getName().endsWith(".zip")||file.getName().endsWith("prs")||file.getName().endsWith("blt")) {
        SlideSet ss=null;
        try {
          ss=indexSlideSet(file);
        } catch (Exception e) {
          // TODO Auto-generated catch block
          e.printStackTrace();
        }
        if (ss==null) {
          /*
           * Properties props=getProperties(file); if (props!=null) { String
           * tags=props.getProperty("tags"); if (filterTag==null) { SlideSet
           * ss=new SlideSet(file.getAbsolutePath(),props); if
           * (ss.getNumSlides()>0) { slideSetMap.put(file.getName(),ss); } }
           * else { if (tags!=null) { for(String tag:tags.split(",")) { if
           * (filterTag.toLowerCase().equals(tag.toLowerCase())) { SlideSet
           * ss=new SlideSet(file.getAbsolutePath(),props); if
           * (ss.getNumSlides()>0) { slideSetMap.put(file.getName(),ss); }
           * break; } } } } } else {
           */
          if (!reported.contains(file.getAbsolutePath())) {
            bad=true;
            sb.append("\nFile:"+file.getAbsolutePath());
            reported.add(file.getAbsolutePath());
          }
        }
      }
      // if (act!=null)
      // this.updateProgressMessage(act,cnt+"/"+tot+" Files Scanned");
    }
    if (bad) {
      String message="The following files were detected which may be corrupted or otherwise incompatible with Brieflet.  You may want to remove these from the Slides directory.\n"
          +sb.toString();
      MLog.v(message);
      // it would be nice if the dialog below worked but we can't guarantee the
      // activity is running apparently -- SWB
      // DialogUtil.infoDialog("Warning",message,null,context);
    }
    // if (act!=null) isDone=true;
    }
  }

  public Drawable getSlide(int num) {
    return(getSlide(num,null));
  }

  /**
   * This is the only method to retrieve a full resolution slide. It looks up
   * the name for the given sequential number in order to retrieve the slide
   * name which is the file name inside the zip of the image.
   */
  public Drawable getSlide(int num,SlideAction exclude) { // if we're going to
    // do dynamic overlay,
    // this is the place -- SWB
    Bitmap bitmap=getSlideBitmap(num);
    if (bitmap==null)
      return(null);
    Bitmap nbitmap=drawOverlays(num,bitmap,exclude);
    BitmapDrawable d=new BitmapDrawable(context.getResources(),nbitmap);
    d.setAntiAlias(true);
    return(d);
  }

  /**
   * Make sure rect not inverted, either return original or a copy if modified.
   * 
   * @param rect
   * @return non-inverted Rect
   */
  public static Rect fixRect(Rect rect) {
    if (rect.left<rect.right&&rect.top<rect.bottom)
      return(rect);
    return(new Rect(Math.min(rect.left,rect.right),Math.min(rect.top,rect.bottom),Math.max(rect.left,rect.right),Math.max(rect.top,
        rect.bottom)));
  }

  // new strategy, use CanvasView to draw all thumbnails

  public void drawActionThumbnail(SlideAction action,Canvas canvas) {
    Rect rect=fixRect(action.getBounds());
    drawActionThumbnail(action,canvas,rect,true);
  }

  public void drawActionThumbnail(SlideAction action,Canvas canvas,Rect rect,boolean cache) {
    Bitmap thumb=thumbCache.get(action); // need to flush old images, need to
    // refetch if dims differ too much
    if (thumb!=null) {
      // Rect rect=fixRect(action.getBounds());
      canvas.drawBitmap(thumb,null,rect,new Paint());
    } else {
      // Rect rect=fixRect(action.getBounds());
      if (action instanceof SlideVideoAction) {
        SlideVideoAction sva=(SlideVideoAction)action;
        Bitmap thumb0=ThumbnailUtils.createVideoThumbnail(sva.getFilename(),MediaStore.Images.Thumbnails.MINI_KIND);
        if (thumb0!=null) { // if not bitmap is found (or not such video), this
          // should not render it
          thumb=Bitmap.createScaledBitmap(thumb0,rect.width(),rect.height(),true);
          if (cache)
            thumbCache.put(action,thumb);
          canvas.drawBitmap(thumb,null,rect,new Paint());
        }
      } else if (action instanceof SlideAudioAction) {
        SlideAudioAction saa=(SlideAudioAction)action;
        MLog.v("URI: "+saa.getUri());
        Bitmap thumb0=MusicUtil.getThumb(context,saa.getUri());
        if (thumb0!=null) {
          thumb=Bitmap.createScaledBitmap(thumb0,rect.width(),rect.height(),true);
          if (cache)
            thumbCache.put(action,thumb);
          canvas.drawBitmap(thumb,null,rect,new Paint());
        } else {
          MusicUtil.drawDefaultArtwork(context,canvas,rect,saa.getUri());
          MLog.v("Thumb was null!");
        }
      } else if (action instanceof LaunchAction) { // doesn't use cache
        LaunchAction la=(LaunchAction)action; // Guard against application not
        // found and don't render an icon
        Drawable icon=IntentUtil.getAppIcon(context,la.getPackageString(),la.getClassString());
        if (icon!=null) {
          icon.setBounds(rect);
          icon.draw(canvas);
        }
      } else if (action instanceof SlideIntentAction) {
        SlideIntentAction sia=(SlideIntentAction)action;
        if (sia.getIntentString().startsWith("com.sra.brieflet.SlideViewer")) {
          String uri=sia.getUriString();
          // form: slide://path#slide
          int pos=uri.lastIndexOf("#");
          String slideName=uri.substring(pos+1);
          String slidePath=uri.substring(8,pos);

          Bitmap b=action.getBitmap();
          if (b==null)
            b=getThumbnail(slidePath,slideName); // this won't render a
          // thumbnail if no such slide
          // is found

          if (b!=null) {
            if (cache)
              thumbCache.put(action,b);
            canvas.drawBitmap(b,null,rect,new Paint());
          }
        } else if (action.getBitmap()!=null) {
          canvas.drawBitmap(action.getBitmap(),null,rect,new Paint());
        }
      } else if (action.getBitmap()!=null) {
        canvas.drawBitmap(action.getBitmap(),null,rect,new Paint());
      }
    }
  }

  private Bitmap drawOverlays(int num,Bitmap bitmap,SlideAction exclude) {
    // assume bitmap is fullscreen for now (already stretched)
    List<SlideAction> actions=currentSlideSet.getActions(num);
    if (actions==null)
      return(bitmap);
    Bitmap nbitmap=null;
    Canvas canvas=null;
    Paint p=new Paint();
    for(SlideAction action:actions) {
      if (exclude==null||!exclude.equals(action)) {
        if (action.supportsThumbnail()&&action.isThumbVisible()) {
          if (action.getBounds().width()>0&&action.getBounds().height()>0) {
            if (canvas==null) {
              try {
                // experimental bitmap pooling -- use a pool of two to draw
                // overlays so we can do transitions
                lastOverlay=1-lastOverlay;
                if (overlayBitmap[lastOverlay]==null) {
                  overlayBitmap[lastOverlay]=Bitmap.createBitmap(1280,theight,Config.ARGB_8888);
                }
                overlayBitmap[lastOverlay].eraseColor(Color.BLACK); // fill for
                // consistency,
                // might be
                // re-used
                nbitmap=overlayBitmap[lastOverlay]; // watch for out of memory
                // here -- SWB
              } catch (Error e) {
                return(bitmap); // just use bitmap with no overlays if we have
                // allocation problems
              }
              canvas=new Canvas(nbitmap);
              canvas.drawBitmap(bitmap,null,new Rect(0,0,1280,theight),p); // scale
              // it
              // to
              // full
              // screen
              // (which
              // is
              // what
              // the
              // display
              // does anyway)
              // canvas.drawBitmap(bitmap,0,0,p); // should center or something
              bitmap=null; // get rid of it fast
            }
            drawActionThumbnail(action,canvas);
            /*
             * SlideVideoAction sva=(SlideVideoAction)action; Bitmap
             * thumb0=ThumbnailUtils
             * .createVideoThumbnail(sva.getFilename(),MediaStore
             * .Images.Thumbnails.MINI_KIND); if (thumb0==null) { // can't do a
             * thumb Paint p2=new Paint(); p.setStyle(Style.FILL);
             * p.setColor(Color.BLUE); canvas.drawRect(sva.getBounds(),p2); }
             * else { Bitmap
             * thumb=Bitmap.createScaledBitmap(thumb0,sva.getWidth(
             * ),sva.getHeight(),true);
             * canvas.drawBitmap(thumb,sva.getLeft(),sva.getTop(),p); }
             */
          }
        }
      }
    }
    if (nbitmap==null) {
      int down=getDownSampleSize();
      Bitmap thumb=Bitmap.createScaledBitmap(bitmap,bitmap.getWidth()/down,bitmap.getHeight()/down,true); // since
      // an
      // thumbnail
      // might
      // have
      // gone
      // away
      currentSlideSet.setBitmap(num,thumb);
      return(bitmap);
    } else {
      int down=getDownSampleSize();
      Bitmap thumb=Bitmap.createScaledBitmap(nbitmap,nbitmap.getWidth()/down,nbitmap.getHeight()/down,true);
      currentSlideSet.setBitmap(num,thumb);
      return(nbitmap);
    }
  }

  static Bitmap getChangedBitmap(SlideSet ss,String name) {
    // MLog.v("getChangedBitmap "+name);
    File file=getChangedFile(ss.getZipName(),name,false);
    if (!file.exists())
      return(null);
    try {
      FileInputStream in=new FileInputStream(file);
      Bitmap b=BitmapFactory.decodeStream(in);
      in.close();
      return(b);
    } catch (IOException e) {
      MLog.e("Error loading local bitmap: "+name,e);
    }
    return(null);
  }

  public Bitmap getSlideBitmap(SlideSet ss,String name,boolean down) {
    // might need to fetch from changed directory first -- OK added logic -- SWB
    String zipPath=ss.getZipName(); // added
    Bitmap b=getChangedBitmap(ss,name);
    if (b!=null)
      return(b);
    try {
      ZipFile zf=new ZipFile(zipPath);
      ZipEntry entry=zf.getEntry(name);
      if (entry!=null) {
        BitmapFactory.Options opts=null;
        if (down) {
          opts=new BitmapFactory.Options();
          opts.inSampleSize=downsamplesize; // down sample by 8 (power of two
          // for speed)
        }
        b=BitmapFactory.decodeStream(zf.getInputStream(entry),null,opts);
        // b=BitmapFactory.decodeStream(zf.getInputStream(entry));
        zf.close();
        return(b);
      }
      zf.close();
    } catch (IOException e) {
      MLog.e("Error retrieving slide bitmap",e);
    }
    return(null);
  }

  public Bitmap getSlideBitmap(int num) { // don't keep them in memory
    // might need to fetch from changed directory first -- OK added logic -- SWB
    return(getSlideBitmap(currentSlideSet,currentSlideSet.getSlideName(num),false));
    /*
     * String zipPath=currentSlideSet.getZipName(); // added String
     * name=currentSlideSet.getSlideName(num); Bitmap
     * b=getChangedBitmap(currentSlideSet,name); if (b!=null) return(b); try {
     * ZipFile zf=new ZipFile(zipPath); ZipEntry entry=zf.getEntry(name); if
     * (entry!=null) { b=BitmapFactory.decodeStream(zf.getInputStream(entry));
     * zf.close(); return(b); } zf.close(); } catch (IOException e) {
     * MLog.e("Error retrieving slide bitmap",e); } return(null);
     */
  }
  
  public boolean copyOutSlide(int num,String filename) {
    return(copyOutSlide(currentSlideSet.getSlideName(num),filename));
  }

  public boolean copyOutSlide(String name,String filename) {
    // might need to fetch from changed directory first -- OK added logic -- SWB
    String zipPath=currentSlideSet.getZipName();
    //String name=currentSlideSet.getSlideName(num);
    File infile=getChangedFile(zipPath,name,false);
    if (infile.exists()) {
      try {
        FileInputStream in=new FileInputStream(infile);
        FileOutputStream out=new FileOutputStream(filename);
        byte[] buf=new byte[4096];
        int len=0;
        while ((len=in.read(buf,0,buf.length))>-1) {
          out.write(buf,0,len);
        }
        in.close();
        out.close();
        return(true);
      } catch (Exception e) {
        MLog.e("Error retrieving entry from file",e);
      }
    } else {
      try {
        ZipFile zf=new ZipFile(zipPath);
        ZipEntry entry=zf.getEntry(name);
        if (entry==null)
          return(false);
        InputStream in=zf.getInputStream(entry);
        FileOutputStream out=new FileOutputStream(filename);
        byte[] buf=new byte[4096];
        int len=0;
        while ((len=in.read(buf,0,buf.length))>-1) {
          out.write(buf,0,len);
        }
        zf.close();
        out.close();
        return(true);
      } catch (IOException e) {
        MLog.e("Error retrieving entry from zip",e);
      }
    }
    return(false);
  }

  private static boolean isPotentialSlideImage(String name) {
    File file=new File(name);
    name=file.getName();
    // MLog.v("potential slide image:"+name);
    return(!name.startsWith(".")&&endsWithImageExtension(name));
  }

  private static boolean endsWithImageExtension(String name) {
    return((name.toLowerCase().endsWith(".png")||name.toLowerCase().endsWith(".jpg")||name.toLowerCase().endsWith(".gif")||name
        .toLowerCase().endsWith(".bmp")));
  }

  /**
   * Get the string form of the changes directory associated with the zip file.
   * 
   * @param zipfile
   * @return changes directory in string form
   */
  public static String getChangedDir(File zipfile) {
    String path=zipfile.getAbsolutePath();
    int pos=path.lastIndexOf(".");
    if (pos>-1) {
      path=path.substring(0,pos);
    }
    return(path+".chg");
  }

  /**
   * Get the outside-the-zip changed form of a file if any.
   * 
   * @param zipfile
   *          presentation zip file
   * @param name
   *          name of the desired zip entry
   * @return a local changed version of the file (outside the zip) if any
   */
  public static File getChangedFile(File zipfile,String name,boolean mkdir) {
    String dirstr=getChangedDir(zipfile);
    if (mkdir) {
      File dir=new File(dirstr);
      if (!dir.exists())
        dir.mkdirs();
    }
    File file=new File(dirstr+"/"+slideEntryToFileName(name));
    return(file);
  }

  public static File getChangedFile(String zipstr,String name,boolean mkdir) {
    return(getChangedFile(new File(zipstr),name,mkdir));
  }

  /**
   * Specifically retrieve properties from the meta.txt associated with the zip
   * (or an outside changed version) if any
   * 
   * @param zipfile
   *          presentation zip file
   * @return properties from the meta.txt or null if not found
   */
  private static Properties getMeta(File zipfile) {
    Properties props=new Properties();
    File mfile=getChangedFile(zipfile,"meta.txt",false);
    if (mfile.exists()) {
      try {
        props.load(new FileInputStream(mfile));
      } catch (Exception e) {
        MLog.e("Error reading local meta.txt",e);
      }
    } else {
      try {
        ZipFile zf=new ZipFile(zipfile);
        ZipEntry entry=zf.getEntry("meta.txt");
        if (entry!=null) {
          props.load(zf.getInputStream(entry));
        }
        zf.close();
      } catch (Exception e) {
        MLog.e("Error reading meta.txt from zip",e);
      }

    }
    return(props);
  }

  public static boolean existsInChangedOrZip(File zipfile,String name) {
    boolean result=false;
    File mfile=getChangedFile(zipfile,name,false);
    if (mfile.exists())
      return(true);
    try {
      ZipFile zf=new ZipFile(zipfile);
      ZipEntry entry=zf.getEntry(name);
      result=(entry!=null);
      zf.close();
    } catch (Exception e) {
      MLog.e("Error checking for entry in zip",e);
    }
    return(result);
  }

  private static Properties getProperties(File zipfile) {
    Properties props=getMeta(zipfile); // priority access to meta.txt if
    // available
    if (props.getProperty("order")!=null) {
      return(props);
    }
    lastIndex=null;
    // MLog.v("getProperties for "+zipfile);
    try {
      ZipFile zf=new ZipFile(zipfile);
      Enumeration entries=zf.entries();
      ZipEntry entry;
      StringBuilder sb=new StringBuilder();
      int c=0;
      // Properties props=new Properties();
      List<String> names=new ArrayList<String>();
      boolean foundMeta=false;
      while (entries.hasMoreElements()) {
        entry=(ZipEntry)entries.nextElement();
        String name=entry.getName();
        // MLog.v("name="+name);
        if (isPotentialSlideImage(name)) {
          names.add(name);
          if (c++>0)
            sb.append(",");
          sb.append(name);
        }
        if (name.equals("title.txt")||name.equals("meta.txt")) { // backward
          foundMeta=true;
          // compatible
          // with one
          // line
          // title.txt
          if (name.equals("title.txt")) {
            BufferedReader in=new BufferedReader(new InputStreamReader(zf.getInputStream(entry)));
            String line;
            while ((line=in.readLine())!=null) {
              props.setProperty("title",line.trim());
              break;
            }
            in.close();
          } else if (name.equals("meta.txt")) { // new format is properties
            // format with lots of
            // properties
            props.load(zf.getInputStream(entry));
          }
        } else if (name.equals("index.txt")) {
          List<IndexEntry> index=new ArrayList<IndexEntry>();
          BufferedReader in=new BufferedReader(new InputStreamReader(zf.getInputStream(entry)));
          String line;
          String digits="123456789";
          while ((line=in.readLine())!=null) {
            String[] fields=line.split("\\|");
            String slidename=null;
            if (digits.indexOf(fields[0].substring(0,1))>-1) {
              slidename="Slide"+fields[0];
            } else {
              slidename=fields[0];
            }
            float top=Float.parseFloat(fields[2]);
            float left=Float.parseFloat(fields[3]);
            float width=Float.parseFloat(fields[4]);
            float height=Float.parseFloat(fields[5]);
            index.add(new IndexEntry(slidename,fields[1],new Rect((int)(left*12.8),(int)(top*theight/100),(int)((left+width)*12.8),
                (int)((top+height)*theight/100))));
          }
          in.close();
          lastIndex=index;
        }
      }
      if (!foundMeta) {
        props.setProperty("title",zipfile.getName());
        props.setProperty(".autotitle","true");
      }
      // MLog.v("SRA.slidelist="+sb.toString());
      if (sb.toString().length()>0) {
        // props.setProperty(".slidelist",sb.toString());
        if (props.getProperty("order")==null) {
          // to be more general we parse each name for numeric part
          boolean allmatch=true;
          boolean somematch=false;
          Map<Integer, String> nameMap=new HashMap<Integer, String>();
          for(String n:names) {
            File f=new File(n);
            String n2=f.getName();
            int p=n2.indexOf(".");
            if (p>-1) {
              if (n2.startsWith("Slide")) {
                String nstr=n2.substring(5,p);
                int num=-1;
                try {
                  num=Integer.parseInt(nstr);
                  somematch=true;
                  nameMap.put(num,n);
                } catch (Exception e) {
                  allmatch=false;
                }
              } else {
                allmatch=false;
              }
            } else {
              allmatch=false;
            }
          }
          // MLog.v("All files matched pattern="+allmatch);
          StringBuilder sb2=new StringBuilder();
          if (somematch) { // hopefully most but only include those
            int c1=1;
            while (nameMap.get(c1)!=null) {
              if (c1>1) {
                sb2.append(",");
              }
              sb2.append(nameMap.get(c1));
              c1++;
            }
          }
          // since no order specified, we bias based on finding files of pattern
          // SlideN.PNG
          /*
           * StringBuilder sb2=new StringBuilder(); int c2=1; while (true) {
           * String pat="Slide"+c2+".PNG"; if (names.indexOf(pat)>-1) { if
           * (c2>1) sb2.append(","); sb2.append(pat); c2++; } else { break; } }
           */
          if (sb2.toString().length()>0) {
            props.setProperty("order",sb2.toString()); // if found ordered PPT
            // style names
          } else {
            props.setProperty("order",sb.toString()); // if no pattern, take all
            // png files
          }
        }
      }
      // props.setProperty(".numslides",Integer.toString(c));
      zf.close();
      return(props);
    } catch (Exception e) {
      MLog.e("Error creating props from zip",e);
    }
    return(null);
  }

  public void createSlideSet(Brieflet pres,String title,Runnable runnable) {
    File file=pres.findIncomingPresFile();
    CRC32 crc=new CRC32();
    byte[] buf=new byte[4096];
    int len=0;
    Properties props=new Properties();
    props.setProperty("title",title);
    props.setProperty("order","template.PNG");
    try {
      ZipOutputStream out=new ZipOutputStream(new FileOutputStream(file));
      out.setLevel(6);
      ZipEntry entry;
      // write out new version of properties for this entry
      entry=new ZipEntry("meta.txt");
      ByteArrayOutputStream baos=new ByteArrayOutputStream();
      props.setProperty("lastUpdate",Long.toString(System.currentTimeMillis()));
      props.store(baos,null);
      byte[] entbuf=baos.toByteArray();
      baos.close();
      entry.setSize(entbuf.length);
      crc.reset();
      crc.update(entbuf);
      entry.setCrc(crc.getValue());
      out.putNextEntry(entry);
      out.write(entbuf);
      // write out the template image
      entry=new ZipEntry("template.PNG");
      baos=new ByteArrayOutputStream();
      InputStream in=pres.getResources().openRawResource(R.raw.template);
      while ((len=in.read(buf,0,buf.length))>-1) {
        baos.write(buf,0,len);
      }
      in.close();
      entbuf=baos.toByteArray();
      entry.setSize(entbuf.length);
      crc.reset();
      crc.update(entbuf);
      entry.setCrc(crc.getValue());
      out.putNextEntry(entry);
      out.write(entbuf);
      out.finish();
      out.close();
      runnable.run();
    } catch (Exception e) {
      MLog.e("Error creating presentation",e);
    }
  }

  private SlideInfo createSlideForMedia(String mimeType,String filename) {
    SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(context);
    String scolor=prefs.getString("defaultSlideBackground","White");
    boolean custom=true;
    int color=0;
    if (!scolor.startsWith("Custom")) {
      custom=false;
      color=context.getResources().getColor(
          context.getResources().getIdentifier(scolor.toLowerCase(),"color",context.getPackageName()));
    }
    MLog.v("scolor="+scolor+" color="+Integer.toHexString(color));
    try {
      if (mimeType.startsWith("image/")) {
        File file=new File(filename);
        FileInputStream test=new FileInputStream(file);
        BitmapFactory.Options o=new BitmapFactory.Options();
        o.inJustDecodeBounds=true;
        BitmapFactory.decodeStream(test,null,o);
        MLog.v("size="+o.outWidth+"x"+o.outHeight);
        double scale=Math.min(1280.0/o.outWidth,1.0*theight/o.outHeight);
        int targetWidth=o.outWidth;
        if (o.outWidth>1280||o.outHeight>theight) {
          targetWidth=(int)(scale*o.outWidth); // only do scale down (not up)
        }
        test.close();
        BitmapScaler scaler=new BitmapScaler(file,targetWidth);
        Bitmap bitmap=Bitmap.createBitmap(1280,theight,Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        if (custom) {
          // should draw template if available, otherwise default to black
          File tfile=new File("/sdcard/Slides/template.png");
          if (tfile.exists()) {
            Bitmap b=BitmapFactory.decodeStream(new FileInputStream(tfile));
            Bitmap b2=Bitmap.createScaledBitmap(b,1280,theight,true);
            canvas.drawBitmap(b2,0,0,new Paint());
          } else {
            canvas.drawColor(Color.BLACK);
          }
        } else {
          canvas.drawColor(color);
        }
        Bitmap b2=scaler.getScaled();
        int offx=(1280-b2.getWidth())/2;
        int offy=(SlideLoader.theight-b2.getHeight())/2;
        canvas.drawBitmap(b2,offx,offy,new Paint());
        return(new SlideInfo(bitmap));
        // return(new SlideInfo(scaler.getScaled()));
      } else if (mimeType.startsWith("video/")) {
        String size=prefs.getString("defaultVideoSize","640x480"); // must be of
        // this form
        // in
        // arrays.xml
        MLog.v("vidsize="+size);
        int pos=size.indexOf("x");
        int vw=Integer.parseInt(size.substring(0,pos));
        int vh=Integer.parseInt(size.substring(pos+1));
        int vx=(1280-vw)/2;
        int vy=(theight-vh)/2;
        MLog.v("vx="+vx+" vy="+vy+" vw="+vw+" vh="+vh);
        // this logic may go away and be done at run time soon -- SWB
        // Bitmap
        // thumb0=ThumbnailUtils.createVideoThumbnail(filename,MediaStore.Images.Thumbnails.FULL_SCREEN_KIND);
        // Bitmap thumb=thumb0.createScaledBitmap(thumb0,vw,vh,true);
        Bitmap bitmap=Bitmap.createBitmap(1280,theight,Config.ARGB_8888);
        Canvas canvas=new Canvas(bitmap);
        if (custom) {
          // should draw template if available, otherwise default to black
          File tfile=new File("/sdcard/Slides/template.png");
          if (tfile.exists()) {
            Bitmap b=BitmapFactory.decodeStream(new FileInputStream(tfile));
            Bitmap b2=Bitmap.createScaledBitmap(b,1280,theight,true);
            canvas.drawBitmap(b2,0,0,new Paint());
          } else {
            canvas.drawColor(Color.BLACK);
          }
        } else {
          canvas.drawColor(color);
        }
        // canvas.drawBitmap(thumb,vx,vy,new Paint());
        SlideInfo info=new SlideInfo(bitmap);
        // MLog.v("video filename <"+filename+">");
        info.addAction(new SlideVideoAction("video|"+filename+"|"+vx+"|"+vy+"|"+vw+"|"+vh+"|0|-1|1"));
        return(info);
      }
    } catch (Exception e) {
      MLog.e("Error creating slide for media:"+filename,e);
    }
    return(null);
  }

  public void createOrAppendToSlideSetFromImage(Brieflet pres,String title,String mimeType,String filename,final Runnable runnable) {
    SlideSet ss=getSlideSetByTitle(title);
    File imgFile=new File(filename);
    if (ss==null) { // can't assume this is just an image starting the
      // presentation
      File file=pres.findIncomingPresFile();
      ss=new SlideSet(file.getAbsolutePath(),new Properties());
      String name="Slide1.PNG";
      SlideInfo info=createSlideForMedia(mimeType,filename);
      final Bitmap bitmap=info.getBitmapFromMem();
      if (info.getActions().size()>0) {
        for(SlideAction action:info.getActions()) {
          ss.addAction(name,action);
        }
      }
      ss.setTitle(title);
      ss.appendSlideName(name); // append slide and update properties
      final String sname=name;
      final SlideSet ss2=ss;
      final SlideLoader sl=this;
      (new Thread() {

        public void run() {
          SlideLoader.createZipWithMods(ss2,sname,bitmap);
          sl.addSlideSet(ss2); // add in case we don't do a full refresh
          if (runnable!=null)
            runnable.run();
        }
      }).start();
    } else {
      String name="Slide1.PNG"; // imgFile.getName()
      if (ss.hasSlide(name)) {
        int cnt=2;
        while (ss.hasSlide("Slide"+cnt+".PNG")) {
          cnt++;
        }
        name="Slide"+cnt+".PNG";
      }
      SlideInfo info=createSlideForMedia(mimeType,filename);
      final Bitmap bitmap=info.getBitmapFromMem();
      if (info.getActions().size()>0) {
        for(SlideAction action:info.getActions()) {
          ss.addAction(name,action);
        }
      }
      ss.setTitle(title);
      ss.appendSlideName(name); // append slide and update properties
      final String sname=name;
      final SlideSet ss2=ss;
      (new Thread() {

        public void run() {
          SlideLoader.copyZipWithMods(ss2,null,sname,bitmap);
          if (runnable!=null)
            runnable.run();
        }
      }).start();
    }
  }

  /**
   * Need to scale image down to appropriate size before storing
   * 
   * @param title
   *          title of slide
   * @param filename
   *          file name of image
   * @param runnable
   *          runnable to call on completion
   */
  /*
   * public void createSlideSetFromImage(Brieflet pres,String title,String
   * filename,Runnable runnable) { File imgFile=new File(filename); BitmapScaler
   * scaler=null; try { FileInputStream test=new FileInputStream(imgFile);
   * BitmapFactory.Options o=new BitmapFactory.Options();
   * o.inJustDecodeBounds=true; BitmapFactory.decodeStream(test,null,o);
   * MLog.v("size="+o.outWidth+"x"+o.outHeight); double
   * scale=Math.min(1280.0/o.outWidth,1.0*theight/o.outHeight); int
   * targetWidth=o.outWidth; if (o.outWidth>1280||o.outHeight>theight) {
   * targetWidth=(int)(scale*o.outWidth); // only do scale down (not up) }
   * test.close(); scaler=new BitmapScaler(imgFile,targetWidth); } catch
   * (Exception e) { MLog.e("image reading error",e); } File
   * file=pres.findIncomingPresFile(); CRC32 crc=new CRC32(); byte[] buf=new
   * byte[4096]; int len=0; Properties props=new Properties();
   * props.setProperty("title",title);
   * props.setProperty("order",imgFile.getName()); try { ZipOutputStream out=new
   * ZipOutputStream(new FileOutputStream(file)); out.setLevel(6); ZipEntry
   * entry; // write out new version of properties for this entry entry=new
   * ZipEntry("meta.txt"); ByteArrayOutputStream baos=new
   * ByteArrayOutputStream();
   * props.setProperty("lastUpdate",Long.toString(System.currentTimeMillis()));
   * props.store(baos,null); byte[] entbuf=baos.toByteArray(); baos.close();
   * entry.setSize(entbuf.length); crc.reset(); crc.update(entbuf);
   * entry.setCrc(crc.getValue()); out.putNextEntry(entry); out.write(entbuf);
   * // write out the image entry=new ZipEntry(imgFile.getName()); baos=new
   * ByteArrayOutputStream();
   * scaler.getScaled().compress(CompressFormat.PNG,100,baos); scaler=null;
   * entbuf=baos.toByteArray(); entry.setSize(entbuf.length); crc.reset();
   * crc.update(entbuf); entry.setCrc(crc.getValue()); out.putNextEntry(entry);
   * out.write(entbuf); out.finish(); out.close(); runnable.run(); } catch
   * (Exception e) { MLog.e("Error creating presentation",e); } }
   */

  public void renameSlideSet(View view,String zipname,String newtitle,Runnable runnable) {
    SlideSet ss=slideSetMap.get(zipname);
    MLog.v("rename called with zip="+zipname+" ss="+ss);
    if (ss!=null) {
      ss.rename(view,newtitle,runnable);
    }
  }

  public static File duplicateFileName(String name) {
    String num="0123456789";
    String ext="";
    int pos=name.lastIndexOf(".");
    if (pos>-1) {
      ext=name.substring(pos);
      name=name.substring(0,pos);
    }
    pos=name.length()-1;
    while (pos>0) {
      if (num.indexOf(name.substring(pos,pos+1))==-1)
        break;
      pos--;
    }
    name=name.substring(0,pos+1); // trim numbers
    int cnt=2;
    while (true) {
      File file=new File(name+cnt+ext);
      if (!file.exists()) {
        return(file);
      }
      cnt++;
    }
  }

  public static void duplicateSlideSet(View view,File zipname,String title,Runnable runnable) {
    try {
      FileInputStream in=new FileInputStream(zipname);
      File outfile=duplicateFileName(zipname.getAbsolutePath());
      FileOutputStream out=new FileOutputStream(outfile);
      byte[] buf=new byte[4096];
      int len;
      while ((len=in.read(buf,0,buf.length))>-1) {
        out.write(buf,0,len);
      }
      in.close();
      out.close();
      SlideLoader sl=SlideLoader.getDefaultInstance();
      if (sl!=null) {
        SlideSet ss=sl.indexSlideSet(outfile);
        ss.rename(view,title,runnable);
      } else {
        runnable.run(); // this shouldn't happen but if so run runnable anyway
      }
    } catch (Exception e) {
      MLog.e("Error duplicate presentation",e);
    }
  }

  /*
   * Copy out the specified slide set to tmp.zip including writing out the
   * properties in the slide set to the meta.txt file in the zip. All entries
   * are copied through aside from meta.txt or title.txt which is replaced with
   * the properties.
   * 
   * This method should be extended to copy in additional image files (perhaps
   * coming from URIs or paths on disk). This is the key function needed to
   * implement:
   * 
   * -slide ordering -slide hiding -adding or removing slides (permanently from
   * the presentation)
   * 
   * Performance appears to be very fast (e.g. not noticeable to the user).
   * 
   * This method should probably rename tmp.zip to the original upon completion.
   */

  static void copyZipWithMods(SlideSet ss) {
    copyZipWithMods(ss,null,null,null);
  }

  static void copyZipWithMods(SlideSet ss,Set<String> skip) {
    copyZipWithMods(ss,skip,null,null);
  }

  public static File makeTempFile() {
    File dir=new File("/sdcard/Slides/tmp");
    if (!dir.exists())
      dir.mkdirs();
    while (true) {
      File file=new File("/sdcard/Slides/tmp/tmp"+rand.nextInt()+".blt");
      if (!file.exists())
        return(file);
    }
  }

  static void createZipWithMods(SlideSet ss,String name,Bitmap bitmap) {
    String fromzip=ss.getZipName();
    File tozip=makeTempFile();
    // String tozip="/sdcard/Slides/tmp.zip";
    CRC32 crc=new CRC32();
    try {
      ZipOutputStream out=new ZipOutputStream(new FileOutputStream(tozip));
      out.setLevel(6);
      ZipEntry entry;
      entry=new ZipEntry("meta.txt");
      ByteArrayOutputStream baos=new ByteArrayOutputStream();
      ss.setProperty("lastUpdate",Long.toString(System.currentTimeMillis()));
      ss.getProperties().store(baos,null);
      byte[] entbuf=baos.toByteArray();
      baos.close();
      entry.setSize(entbuf.length);
      crc.reset();
      crc.update(entbuf);
      entry.setCrc(crc.getValue());
      out.putNextEntry(entry);
      out.write(entbuf);
      if (bitmap!=null&&name!=null) {
        entry=new ZipEntry(name);
        baos=new ByteArrayOutputStream();
        bitmap.compress(CompressFormat.PNG,100,baos);
        entbuf=baos.toByteArray();
        entry.setSize(entbuf.length);
        crc.reset();
        crc.update(entbuf);
        entry.setCrc(crc.getValue());
        out.putNextEntry(entry);
        out.write(entbuf);
      }
      out.finish();
      out.close();
      // File tmpFile=new File(tozip);
      if (!tozip.renameTo(new File(fromzip))) {
        MLog.v("Error renaming tmp zip to original zip file for "+fromzip);
        // tmpFile.delete();
      }
    } catch (Exception e) {
      MLog.e("SlideLoader "+e.toString(),e);
    }
    isDone=true; // even when exception probably want to say we're done
  }

  static void updateActionThumbnailChanges(SlideSet ss) {
    String zipname=ss.getZipName();
    for(String actionid:ss.getActionIds()) {
      File file=getChangedFile(zipname,actionid,false);
      if (file.exists()) {
        continue;
      }
      // we will comment out the check of the zip since we can't know if the
      // thumbnails
      // were deleted and re-added before a pack, so while thumbnails can't
      // change, it might
      // be a brand new thumbnail with the same name (since the names get
      // re-used), therefore
      // we must overwrite for now
      // OK, making sure both bitmaps and slides don't get names which are
      // either in changed or the zip
      boolean inzip=false;
      try {
        ZipFile zf=new ZipFile(zipname);
        inzip=(zf.getEntry(actionid)!=null);
        zf.close();
      } catch (Exception e) {
        MLog.e("Error accessing zipfile: "+zipname,e);
      }
      if (inzip) {
        continue;
      }
      // this currently will copy out bitmaps for actions which can be loaded by
      // other means, this
      // is a good idea for browser thumbnails (which can't be gotten any other
      // way), and slide thumbnails
      // which may not include embedded action pictures otherwise
      // but it is potentially redundant for applications and for songs which
      // can retrieve their targets -- SWB
      // MLog.v("Possible new action id="+actionid);
      SlideAction sa=ss.getActionForId(actionid);
      Bitmap b=sa.getBitmap();
      if (b!=null) {
        // MLog.v("Saving out bitmap for "+actionid);
        file=getChangedFile(zipname,actionid,true);
        try {
          FileOutputStream out=new FileOutputStream(file);
          b.compress(CompressFormat.PNG,100,out);
          out.close();
        } catch (Exception e) {
          MLog.e("Error writing thumbnail: "+file.getName(),e);
        }
      }
    }
  }

  static void recordChanges(SlideSet ss,Set<String> skip,String[] names,List<SlideInfo> slides) {
    updateActionThumbnailChanges(ss);
    String zipname=ss.getZipName();
    // remove any files marked as skip from the changed directory
    if (skip!=null) {
      for(String name:skip) {
        File file=getChangedFile(zipname,name,false);
        if (file.exists()) {
          file.delete();
        }
      }
    }
    // copy any slides to the changed directory
    if (names!=null&&slides!=null) {
      int x=0;
      for(SlideInfo slide:slides) {
        String name=names[x++];
        File file=getChangedFile(zipname,name,true);
        byte[] buf=new byte[4096];
        int len;
        try {
          InputStream in=slide.getBitmapInputStream();
          FileOutputStream out=new FileOutputStream(file);
          while ((len=in.read(buf,0,buf.length))>-1) {
            out.write(buf,0,len);
          }
          in.close();
          out.close();
        } catch (Exception e) {
          MLog.e("Error copying bitmap to changed directory",e);
        }
      }
    }
    // update the meta.txt file in changed
    saveChangedMeta(ss);
  }

  static void recordChanges(SlideSet ss,Set<String> skip,String name,Bitmap bitmap) {
    updateActionThumbnailChanges(ss);
    String zipname=ss.getZipName();
    // remove any files marked as skip from the changed directory
    if (skip!=null) {
      for(String nm:skip) {
        File file=getChangedFile(zipname,nm,false);
        if (file.exists()) {
          file.delete();
        }
      }
    }
    // copy out the bitmap
    if (name!=null&&bitmap!=null) {
      File file=getChangedFile(zipname,name,true);
      try {
        FileOutputStream out=new FileOutputStream(file);
        bitmap.compress(CompressFormat.PNG,100,out);
        out.close();
      } catch (Exception e) {
        MLog.e("Error copying bitmap to changed directory",e);
      }
    }
    // update the meta.txt file in changed
    saveChangedMeta(ss);
  }

  static void copyAllToZipWithMods(SlideSet ss,Set<String> skip,String[] names,List<SlideInfo> slides) {
    // this will need to be instrumented to use the changed directory -- OK,
    // should work -- SWB
    recordChanges(ss,skip,names,slides);
    /*
     * String fromzip=ss.getZipName(); File tozip=makeTempFile(); // String
     * tozip="/sdcard/Slides/tmp.zip"; CRC32 crc=new CRC32(); byte[] buf=new
     * byte[4096]; int len=0; try { ZipFile zf=new ZipFile(fromzip);
     * ZipOutputStream out=new ZipOutputStream(new FileOutputStream(tozip));
     * out.setLevel(6); Enumeration entries=zf.entries(); ZipEntry entry;
     * HashSet<String> aIds=new HashSet<String>(); // Note: should skip entries
     * // that are no longer actions // as well!!! -- SWB boolean skipone; while
     * (entries.hasMoreElements()) { MLog.v("copying prev entries");
     * skipone=false; entry=(ZipEntry)entries.nextElement(); if
     * (entry.getName().startsWith(".Image-")) { aIds.add(entry.getName()); if
     * (ss.getActionForId(entry.getName())==null) { skipone=true; // remove from
     * zip if action has been deleted } } if
     * (entry.getName().equals("meta.txt")||entry.getName().equals("title.txt"))
     * { // skip this since we'll write out a new one } else { if
     * (!skipone&&(skip==null||!skip.contains(entry.getName()))) { // if // we
     * // don't // want // to skip it out.putNextEntry(entry); // straight copy
     * InputStream in=zf.getInputStream(entry); while
     * ((len=in.read(buf,0,buf.length))>-1) { out.write(buf,0,len); }
     * in.close(); } } } entry=new ZipEntry("meta.txt"); ByteArrayOutputStream
     * baos=new ByteArrayOutputStream();
     * ss.setProperty("lastUpdate",Long.toString(System.currentTimeMillis()));
     * ss.getProperties().store(baos,null); byte[] entbuf=baos.toByteArray();
     * baos.close(); entry.setSize(entbuf.length); crc.reset();
     * crc.update(entbuf); entry.setCrc(crc.getValue());
     * out.putNextEntry(entry); out.write(entbuf); int x=0; for(SlideInfo
     * slide:slides) { Bitmap b=slide.getBitmapFromFile(); String name=names[x];
     * MLog.v("copying new entries"); if (b!=null&&name!=null) {
     * MLog.v("writing name: "+name+" and bitmap"); entry=new ZipEntry(name);
     * baos=new ByteArrayOutputStream();
     * b.compress(CompressFormat.PNG,100,baos); entbuf=baos.toByteArray();
     * entry.setSize(entbuf.length); crc.reset(); crc.update(entbuf);
     * entry.setCrc(crc.getValue()); out.putNextEntry(entry); out.write(entbuf);
     * }
     * 
     * x++; }
     * 
     * for(String id:ss.getActionIds()) { if (!aIds.contains(id)) { SlideAction
     * sa=ss.getActionForId(id); if (sa.getBitmap()!=null) { entry=new
     * ZipEntry(id); baos=new ByteArrayOutputStream();
     * sa.getBitmap().compress(CompressFormat.PNG,100,baos);
     * entbuf=baos.toByteArray(); entry.setSize(entbuf.length); crc.reset();
     * crc.update(entbuf); entry.setCrc(crc.getValue());
     * out.putNextEntry(entry); out.write(entbuf); } } } out.finish();
     * out.close(); zf.close(); // File tmpFile=new File(tozip);
     * tozip.renameTo(new File(fromzip)); removeChangedDir(new File(fromzip)); }
     * catch (Exception e) {
     * MLog.e("Error in update of "+fromzip+":"+e.toString(),e); }
     */
    isDone=true;
  }

  static void saveChangedMeta(SlideSet ss) {
    File file=getChangedFile(ss.getZipName(),"meta.txt",true);
    ss.setProperty("lastUpdate",Long.toString(System.currentTimeMillis()));
    try {
      FileOutputStream out=new FileOutputStream(file);
      ss.getProperties().store(out,null);
      out.close();
    } catch (Exception e) {
      MLog.e("Error saving changed meta.txt",e);
    }
  }

  static void saveChangedBitmap(SlideSet ss,String name,Bitmap b) {
    File file=getChangedFile(ss.getZipName(),name,true);
    try {
      FileOutputStream out=new FileOutputStream(file);
      b.compress(CompressFormat.PNG,100,out);
      out.close();
    } catch (IOException e) {
      MLog.e("Error saving changed bitmap: "+name,e);
    }
  }

  static void removeChangedDir(File zipfile) {
    String dir=getChangedDir(zipfile);
    MLog.v("Removing change directory: "+dir);
    Brieflet.deleteDir(dir);
  }

  static String slideEntryToFileName(String entryName) {
    try {
      String result=URLEncoder.encode(entryName,"utf-8");
      // MLog.v("slideEntryToFileName "+entryName+" --> "+result);
      return(result);
    } catch (Exception e) {
      MLog.e("Error encoding "+entryName,e);
    }
    return(entryName);
  }

  static String fileNameToSlideEntry(String fileName) {
    try {
      String result=URLDecoder.decode(fileName,"utf-8");
      return(result);
    } catch (Exception e) {
      MLog.e("Error decoding "+fileName,e);
    }
    return(fileName);
  }

  /**
   * Pack a presentation back into the zip if there have been changes reflecting
   * in a changes directory. This assumes that any edits have already been
   * written out to the changes directory. It does use the in-memory SlideSet to
   * determine if any images are no longer needed in the zip.
   * 
   * @param ss
   *          the slideset to pack
   */
  static void packPresentation(SlideSet ss) {
    File fromzip=new File(ss.getZipName());
    String dirstr=getChangedDir(fromzip);
    File dir=new File(dirstr);
    if (!dir.exists())
      return; // no need to pack, no on-disk changes
    // make a set of the names discovered in the changed directory (except for
    // .txt files)
    File[] files=dir.listFiles();
    Set<String> changed=new HashSet<String>();
    boolean found=false;
    for(File file:files) {
      if (file.isFile()) {
        found=true;
        if (!file.getName().endsWith(".txt")) {
          if (file.getName().startsWith(".Image-")) {
            if (ss.getActionForId(file.getName())!=null) {
              changed.add(fileNameToSlideEntry(file.getName()));
            } else {
              MLog.v("Skipping changed "+file.getName()+" since not in presentation.");
            }
          } else {
            changed.add(fileNameToSlideEntry(file.getName()));
          }
        }
      }
    }
    if (!found) {
      Brieflet.deleteDir(dirstr);
      return;
    }
    MLog.v("Starting pack of "+fromzip);
    File tozip=makeTempFile();
    CRC32 crc=new CRC32();
    byte[] buf=new byte[4096];
    int len=0;
    try {
      ZipFile zf=new ZipFile(fromzip);
      ZipOutputStream out=new ZipOutputStream(new FileOutputStream(tozip));
      out.setLevel(6);
      Enumeration entries=zf.entries();
      ZipEntry entry;
      boolean skipone;
      ByteArrayOutputStream baos=new ByteArrayOutputStream();
      // iterate the entries of the existing zip
      while (entries.hasMoreElements()) {
        skipone=false;
        entry=(ZipEntry)entries.nextElement();
        // if an entry matches an action thumbnail pattern, make sure it's used
        // in the slideset otherwise skip it
        if (entry.getName().startsWith(".Image-")) {
          if (ss.getActionForId(entry.getName())==null) {
            skipone=true; // remove from zip if action has been deleted
          }
        }
        if (entry.getName().equals("meta.txt")||entry.getName().equals("title.txt")) {
          // skip meta files since meta.txt will be copied later
        } else if (!skipone) {
          // if a changed file exists for the entry, copy it in instead of the
          // zip entry
          if (changed.contains(entry.getName())) {
            File cfile=getChangedFile(fromzip,entry.getName(),false);
            if (!cfile.exists()) {
              MLog.v("Expected "+cfile.getAbsolutePath()+" but not found!");
            } else {
              baos.reset();
              FileInputStream in=new FileInputStream(cfile);
              while ((len=in.read(buf,0,buf.length))>-1) {
                baos.write(buf,0,len);
              }
              in.close();
              byte[] entbuf=baos.toByteArray();
              entry.setSize(entbuf.length);
              crc.reset();
              crc.update(entbuf);
              entry.setCrc(crc.getValue());
              out.putNextEntry(entry);
              out.write(entbuf);
            }
            changed.remove(entry.getName());
            // if the entry is a slide in the presentation or is a text index
            // copy it across
          } else if (ss.hasSlide(entry.getName())||entry.getName().equals("index.txt")) {
            out.putNextEntry(entry); // straight copy
            InputStream in=zf.getInputStream(entry);
            while ((len=in.read(buf,0,buf.length))>-1) {
              out.write(buf,0,len);
            }
            in.close();
          }
        }
      }
      // more efficient to do this inline with same bufs, arrays, etc...
      // copy the meta from the changed file (it must exist or the change
      // directory should not)
      File cfile=getChangedFile(fromzip,"meta.txt",false);
      entry=new ZipEntry("meta.txt");
      baos.reset();
      FileInputStream in=new FileInputStream(cfile);
      while ((len=in.read(buf,0,buf.length))>-1) {
        baos.write(buf,0,len);
      }
      in.close();
      byte[] entbuf=baos.toByteArray();
      entry.setSize(entbuf.length);
      crc.reset();
      crc.update(entbuf);
      entry.setCrc(crc.getValue());
      out.putNextEntry(entry);
      out.write(entbuf);
      // any remaining changed entries should be in the presentation or they
      // should not exist in the changed directory, copy them in
      for(String name:changed) {
        cfile=getChangedFile(fromzip,name,false);
        entry=new ZipEntry(name);
        baos.reset();
        in=new FileInputStream(cfile);
        while ((len=in.read(buf,0,buf.length))>-1) {
          baos.write(buf,0,len);
        }
        in.close();
        entbuf=baos.toByteArray();
        entry.setSize(entbuf.length);
        crc.reset();
        crc.update(entbuf);
        entry.setCrc(crc.getValue());
        out.putNextEntry(entry);
        out.write(entbuf);
      }
      out.finish();
      out.close();
      zf.close();
      tozip.renameTo(fromzip);
      removeChangedDir(fromzip);
    } catch (Exception e) {
      MLog.e("Error in pack of "+fromzip+":"+e.toString(),e);
    }
  }

  static void copyZipWithMods(SlideSet ss,Set<String> skip,String name,Bitmap bitmap) {
    // this needs further instrumentation to use the changed directory -- OK,
    // should work -- SWB
    recordChanges(ss,skip,name,bitmap);
    /*
     * if (skip==null&&bitmap==null) { saveChangedMeta(ss); isDone=true; return;
     * } String fromzip=ss.getZipName(); File tozip=makeTempFile(); // String
     * tozip="/sdcard/Slides/tmp.zip"; CRC32 crc=new CRC32(); byte[] buf=new
     * byte[4096]; int len=0; try { ZipFile zf=new ZipFile(fromzip);
     * ZipOutputStream out=new ZipOutputStream(new FileOutputStream(tozip));
     * out.setLevel(6); Enumeration entries=zf.entries(); ZipEntry entry;
     * HashSet<String> aIds=new HashSet<String>(); // Note: should skip entries
     * // that are no longer actions // as well!!! -- SWB boolean skipone; while
     * (entries.hasMoreElements()) { skipone=false;
     * entry=(ZipEntry)entries.nextElement(); if
     * (entry.getName().startsWith(".Image-")) { aIds.add(entry.getName()); if
     * (ss.getActionForId(entry.getName())==null) { skipone=true; // remove from
     * zip if action has been deleted } } if
     * (entry.getName().equals("meta.txt")||entry.getName().equals("title.txt"))
     * { // skip this since we'll write out a new one } else { if
     * (!skipone&&(skip==null||!skip.contains(entry.getName()))) { // if // we
     * // don't // want // to skip it out.putNextEntry(entry); // straight copy
     * InputStream in=zf.getInputStream(entry); while
     * ((len=in.read(buf,0,buf.length))>-1) { out.write(buf,0,len); }
     * in.close(); } } } entry=new ZipEntry("meta.txt"); ByteArrayOutputStream
     * baos=new ByteArrayOutputStream();
     * ss.setProperty("lastUpdate",Long.toString(System.currentTimeMillis()));
     * ss.getProperties().store(baos,null); byte[] entbuf=baos.toByteArray();
     * baos.close(); entry.setSize(entbuf.length); crc.reset();
     * crc.update(entbuf); entry.setCrc(crc.getValue());
     * out.putNextEntry(entry); out.write(entbuf); if (bitmap!=null&&name!=null)
     * { entry=new ZipEntry(name); baos=new ByteArrayOutputStream();
     * bitmap.compress(CompressFormat.PNG,100,baos); entbuf=baos.toByteArray();
     * entry.setSize(entbuf.length); crc.reset(); crc.update(entbuf);
     * entry.setCrc(crc.getValue()); out.putNextEntry(entry); out.write(entbuf);
     * } for(String id:ss.getActionIds()) { if (!aIds.contains(id)) {
     * SlideAction sa=ss.getActionForId(id); if (sa.getBitmap()!=null) {
     * entry=new ZipEntry(id); baos=new ByteArrayOutputStream();
     * sa.getBitmap().compress(CompressFormat.PNG,100,baos);
     * entbuf=baos.toByteArray(); entry.setSize(entbuf.length); crc.reset();
     * crc.update(entbuf); entry.setCrc(crc.getValue());
     * out.putNextEntry(entry); out.write(entbuf); } } } out.finish();
     * out.close(); zf.close(); // File tmpFile=new File(tozip);
     * tozip.renameTo(new File(fromzip)); removeChangedDir(new File(fromzip)); }
     * catch (Exception e) {
     * MLog.e("Error in update of "+fromzip+":"+e.toString(),e); }
     */
    isDone=true;
  }

  private void setThumbnailSizeFromPreference() {
    SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(context);
    String size=prefs.getString("thumbnailSize","small");
    if (size.equals("small")) {
      downsamplesize=8;
    } else if (size.equals("medium")) { //not used anymore
      downsamplesize=6;
    } else if (size.equals("large")) {
      downsamplesize=6;
    }
  }

  public Bitmap getThumbnail(String zipPath,String slideName) {
    return getThumbnail(zipPath,slideName,4); // use 4 for consistency
  }

  public static Bitmap getThumbnail(String zipPath,String slideName,int downsamplesize) {
    // need to potentially use changed directory -- OK, added -- SWB
    File infile=getChangedFile(zipPath,slideName,false);
    if (infile.exists()) {
      try {
        FileInputStream in=new FileInputStream(infile);
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=downsamplesize; // down sample by 8 (power of two
        // for speed)
        Bitmap b=BitmapFactory.decodeStream(in,null,opts);
        in.close();
        return(b);
      } catch (Exception e) {
        MLog.e("Error retrieving thumbnail from file",e);
      }
    } else {
      try {
        ZipFile zf=new ZipFile(zipPath);
        ZipEntry entry=zf.getEntry(slideName);
        if (entry==null)
          return(null);
        BitmapFactory.Options opts=new BitmapFactory.Options();
        opts.inSampleSize=downsamplesize; // down sample by 8 (power of two
        // for speed)
        Bitmap b=BitmapFactory.decodeStream(zf.getInputStream(entry),null,opts);
        zf.close();
        return(b);
      } catch (IOException e) {
        MLog.e("Error retrieving thumbnail from zip",e);
      }
    }
    return(null);
  }

  public static Rect getSlideSizeRange(File zipfile) {
    int minWidth=Integer.MAX_VALUE;
    int maxWidth=0;
    int minHeight=Integer.MAX_VALUE;
    int maxHeight=0;
    StringBuilder sb2=new StringBuilder();
    Properties props=getProperties(zipfile);
    final SlideSet ss=new SlideSet(zipfile.getAbsolutePath(),props);
    String order=props.getProperty("order");
    if (order==null)
      return(null);
    final Set<String> slideNames=new HashSet<String>();
    for(String name:order.split(",")) {
      slideNames.add(name);
    }
    if (slideNames.size()==0)
      return(null);
    try {
      ZipFile zf=new ZipFile(zipfile);
      ZipEntry entry;
      Enumeration entries=zf.entries();
      while (entries.hasMoreElements()) {
        entry=(ZipEntry)entries.nextElement();
        String name=entry.getName();
        if (slideNames.contains(name)) {
          BitmapFactory.Options o=new BitmapFactory.Options();
          o.inJustDecodeBounds=true;
          BitmapFactory.decodeStream(zf.getInputStream(entry),null,o);
          minWidth=Math.min(minWidth,o.outWidth);
          maxWidth=Math.max(maxWidth,o.outWidth);
          minHeight=Math.min(minHeight,o.outHeight);
          maxHeight=Math.max(maxHeight,o.outHeight);
        }
      }
      zf.close();
      return(new Rect(minWidth,maxWidth,minHeight,maxHeight));
    } catch (IOException e) {
      MLog.e("Error analyzing slides",e);
    }
    return(null);
  }

  /**
   * Analyze for possible sidebars or downscaling.
   * 
   * @param act
   *          activity for notification
   * @param zipfile
   *          incoming file
   * @param outfile
   *          ultimate file result should be placed in
   * @param silent
   *          keep silent
   * @param title
   *          optional title to write to the resulting file
   * @return
   */
  public static boolean analyze(final Brieflet act,final File zipfile,final File outfile,boolean silent,final String title) {
    MLog.v("Starting analyze zipfile="+zipfile+" outfile="+outfile);
    int minWidth=Integer.MAX_VALUE;
    int maxWidth=0;
    int minHeight=Integer.MAX_VALUE;
    int maxHeight=0;
    int cnt=0;
    StringBuilder sb2=new StringBuilder();
    Properties props=getProperties(zipfile);
    if (!silent) {
      String sidebars=props.getProperty("sidebars");
      if (sidebars!=null&&sidebars.equals("true")) {
        sb2.append("This presentation already has sidebars.");
        DialogUtil.infoDialog("",sb2.toString(),null,act);
        if (outfile!=null)
          zipfile.renameTo(outfile);
        return true;
      }
    }

    final SlideSet ss=new SlideSet(zipfile.getAbsolutePath(),props);
    if (title!=null) {
      if (props.getProperty(".autotitle")!=null) {
        ss.setTitle(title);
        props.remove(".autotitle");
      }
    }
    String order=props.getProperty("order");
    if (order==null) {
      MLog.v("slide order null");
      return(false);
    }
    final Set<String> slideNames=new HashSet<String>();
    for(String name:order.split(",")) {
      slideNames.add(name);
    }
    if (slideNames.size()==0) {
      MLog.v("slide names empty");
      return(false);
    }
    try {
      ZipFile zf=new ZipFile(zipfile);
      ZipEntry entry;
      Enumeration entries=zf.entries();
      while (entries.hasMoreElements()) {
        entry=(ZipEntry)entries.nextElement();
        String name=entry.getName();
        if (slideNames.contains(name)) {
          cnt++;
          BitmapFactory.Options o=new BitmapFactory.Options();
          o.inJustDecodeBounds=true;
          BitmapFactory.decodeStream(zf.getInputStream(entry),null,o);
          minWidth=Math.min(minWidth,o.outWidth);
          maxWidth=Math.max(maxWidth,o.outWidth);
          minHeight=Math.min(minHeight,o.outHeight);
          maxHeight=Math.max(maxHeight,o.outHeight);
        }
      }
      zf.close();
    } catch (IOException e) {
      MLog.e("Error analyzing slides",e);
    }
    /*
     * StringBuilder sb=new StringBuilder(); sb.append("number of slides="+cnt);
     * sb.append("\nminWidth="+minWidth); sb.append("\nmaxWidth="+maxWidth);
     * sb.append("\nminHeight="+minHeight); sb.append("\nmaxHeight="+maxHeight);
     */

    boolean scale=false;
    if (minWidth==maxWidth&&minHeight==maxHeight) {
      if (minWidth==1280&&minHeight>700&&minHeight<770) {
        // don't need sidebars
        if (!silent) {
          sb2.append("This presentation adequately fills your tablet's screen and does not require sidebars.");
          DialogUtil.infoDialog("",sb2.toString(),null,act);
        }
        MLog.v("no sidebars needed");
        if (outfile!=null)
          zipfile.renameTo(outfile);
      } else {
        float scaleX=1280f/minWidth;
        float scaleY=752f/minHeight;
        int pX=(int)(scaleX*100);
        int pY=(int)(scaleY*100);
        sb2.append("Your presentation has "+cnt+" slides that are "+minWidth+"x"+minHeight+".");
        sb2.append("  In order to fill your tablet's screen these would be scaled "+pX+"% in width");
        sb2.append(" and "+pY+"% in height.  Your slides can also be scaled but have their aspect ratio preserved");
        sb2.append(" with the addition of black sidebars.  Would you prefer to have");
        sb2.append(" sidebars?");
        DialogUtil.guardAction(sb2.toString(),new Runnable() {

          public void run() {
            (new Thread() {

              public void run() {
                scaleZipFile(act,ss,zipfile,slideNames);
                if (outfile!=null)
                  zipfile.renameTo(outfile);
                if (activeAct!=null)
                  activeAct.postRefreshView();
              }
            }).start();
          }
        },new Runnable() {

          public void run() {
            (new Thread() {

              public void run() {
                // should work title if non null into file rewrite in calls
                // below (but meta.txt not parsed and rewritten if present
                // currently)
                Rect r=SlideLoader.getSlideSizeRange(zipfile);
                if (r!=null) {
                  if (r.top>1280||r.bottom>800) {
                    isDone=false;
                    DialogUtil.showBusyCursor(act,"Scaling Down Images...","Please wait...",null);
                    if (outfile!=null) {
                      File tmpfile=makeTempFile();
                      SlideLoader.scaleAllImagesInZipFile(act,-1,zipfile,tmpfile,ss);
                      tmpfile.renameTo(outfile);
                    } else {
                      File tmpfile=makeTempFile();
                      SlideLoader.scaleAllImagesInZipFile(act,-1,zipfile,tmpfile,ss);
                      tmpfile.renameTo(zipfile);
                    }
                    isDone=true;
                    if (activeAct!=null)
                      activeAct.postRefreshView();
                  } else {
                    if (outfile!=null)
                      zipfile.renameTo(outfile);
                    if (activeAct!=null)
                      activeAct.postRefreshView();
                  }
                }
              }
            }).start();
          }
        },act);
      }
    } else {
      sb2.append("Your presentation has "+cnt+" slides with");
      if (minWidth==maxWidth) {
        sb2.append(" a width of "+minWidth);
      } else {
        sb2.append(" width ranging from "+minWidth+" to "+maxWidth);
      }
      if (minHeight==maxHeight) {
        sb2.append(" and a height of "+minHeight);
      } else {
        sb2.append(" and heights ranging from "+minHeight+" to "+maxHeight);
      }
      sb2.append(".  The slides would each be scaled to fit your tablet's screen.  Alternatively you could");
      sb2.append(" choose to scale but preserve the aspect ratio of each slide and add black sidebars.  Would you prefer");
      sb2.append(" to have sidebars?");
      DialogUtil.guardAction(sb2.toString(),new Runnable() {

        public void run() {
          (new Thread() {

            public void run() {
              scaleZipFile(act,ss,zipfile,slideNames);
              if (outfile!=null)
                zipfile.renameTo(outfile);
              if (activeAct!=null)
                activeAct.postRefreshView();
            }
          }).start();
        }
      },new Runnable() {

        public void run() {
          (new Thread() {

            public void run() {
              // should work title if non null into file rewrite in calls below
              // (but meta.txt not parsed and rewritten if present currently)
              Rect r=SlideLoader.getSlideSizeRange(zipfile);
              if (r!=null) {
                if (r.top>1280||r.bottom>800) {
                  isDone=false;
                  DialogUtil.showBusyCursor(act,"Scaling Down Images...","Please wait...",null);
                  if (outfile!=null) {
                    File tmpfile=makeTempFile();
                    SlideLoader.scaleAllImagesInZipFile(act,-1,zipfile,tmpfile,ss);
                    tmpfile.renameTo(outfile);
                  } else {
                    File tmpfile=makeTempFile();
                    SlideLoader.scaleAllImagesInZipFile(act,-1,zipfile,tmpfile,ss);
                    tmpfile.renameTo(zipfile);
                  }
                  isDone=true;
                  if (activeAct!=null)
                    activeAct.postRefreshView();
                } else {
                  if (outfile!=null)
                    zipfile.renameTo(outfile);
                  if (activeAct!=null)
                    activeAct.postRefreshView();
                }
              }
            }
          }).start();
        }
      },act);
    }
    // DialogUtil.infoDialog("Received Presentation",sb.toString(),null,act);
    return(true);
  }

  public static void scaleZipFile(Brieflet act,SlideSet ss,File zipfile,Set<String> slideNames) {
    // when called to add sidebars off an existing presentation, a pack is
    // already guaranteed -- watch if this changes -- SWB
    // add sidebars to each slide in the zip to make them conform to
    // 1280x<theight>
    isDone=false;
    DialogUtil.showBusyCursor(act,"Transforming Presentation...","Please wait...",null);
    File tozip=makeTempFile();
    // String tozip="/sdcard/Slides/tmp.zip";
    CRC32 crc=new CRC32();
    byte[] buf=new byte[4096];
    int len=0;
    try {
      ZipFile zf=new ZipFile(zipfile);
      ZipOutputStream out=new ZipOutputStream(new FileOutputStream(tozip));
      int tot=zf.size();
      out.setLevel(6);
      Enumeration entries=zf.entries();
      ZipEntry entry;
      Bitmap background=Bitmap.createBitmap(1280,theight,Config.ARGB_8888);
      int cnt=0;
      while (entries.hasMoreElements()) {
        cnt++;
        entry=(ZipEntry)entries.nextElement();
        if (slideNames.contains(entry.getName())) {
          BitmapFactory.Options o=new BitmapFactory.Options();
          o.inJustDecodeBounds=true;
          BitmapFactory.decodeStream(zf.getInputStream(entry),null,o);
          double scale=Math.min(1280.0/o.outWidth,1.0*theight/o.outHeight);
          int targetWidth=o.outWidth;
          targetWidth=(int)(scale*o.outWidth);
          // using a bitmap scaler ensure that very large bitmaps don't cause
          // heap to run out
          // we may want to use one on non-sidebar imports as well -- SWB
          BitmapScaler scaler=new BitmapScaler(zf,entry,targetWidth);
          Canvas c=new Canvas(background);
          c.drawColor(Color.BLACK);
          int w=scaler.getScaled().getWidth();
          int h=scaler.getScaled().getHeight();
          int nx=(1280-w)/2;
          int ny=(theight-h)/2;
          ss.scaleSlide(entry.getName(),nx,ny,nx+w,ny+h);
          c.drawBitmap(scaler.getScaled(),nx,ny,new Paint());
          ByteArrayOutputStream baos=new ByteArrayOutputStream();
          background.compress(CompressFormat.PNG,100,baos);
          byte[] entbuf=baos.toByteArray();
          entry.setSize(entbuf.length);
          crc.reset();
          crc.update(entbuf);
          entry.setCrc(crc.getValue());
          out.putNextEntry(entry);
          out.write(entbuf);
        } else if (entry.getName().equals("meta.txt")) {
          // skip for now
        } else {
          out.putNextEntry(entry); // straight copy
          InputStream in=zf.getInputStream(entry);
          while ((len=in.read(buf,0,buf.length))>-1) {
            out.write(buf,0,len);
          }
          in.close();
        }
        if (act!=null)
          SlideLoader.updateProgressMessage(act,cnt+"/"+tot+" Entries Processed");
      }
      ss.updateProps();
      entry=new ZipEntry("meta.txt"); // write meta once done
      ByteArrayOutputStream baos=new ByteArrayOutputStream();
      ss.setProperty("lastUpdate",Long.toString(System.currentTimeMillis()));
      ss.getProperties().store(baos,null);
      byte[] entbuf=baos.toByteArray();
      baos.close();
      entry.setSize(entbuf.length);
      crc.reset();
      crc.update(entbuf);
      entry.setCrc(crc.getValue());
      out.putNextEntry(entry);
      out.write(entbuf);
      out.finish();
      out.close();
      zf.close();
      // File tmpFile=new File(tozip);
      tozip.renameTo(zipfile);
      removeChangedDir(zipfile);
      if (ss.getNumSlides()>0) {
        getDefaultInstance().slideSetMap.put(zipfile.getName(),ss); // make sure
        // we update
        // cache
        // about
        // scaling
      }

    } catch (Exception e) {
      MLog.e("Error in update of "+zipfile.getAbsolutePath()+":"+e.toString(),e);
    }
    isDone=true;
  }

  public static void scaleAllImagesInZipFile(Brieflet act,int chan,File fromzip,File tozip) {
    scaleAllImagesInZipFile(act,chan,fromzip,tozip,null);
  }

  /**
   * Convert all image files in zip (not starting with dot) to scale preserving
   * aspect within the screen bounds of 1280x<theight> and put them out as PNG.
   * This is essentially for potentially oversized images and to compress the
   * files. This does not add sidebars -- that can be done optionally later.
   * 
   * @param fromzip
   *          input zip file
   * @param tozip
   *          output zip file
   */
  public static void scaleAllImagesInZipFile(Brieflet act,int chan,File fromzip,File tozip,SlideSet ss) {
    // for the time being a pack isn't required because this can't be an editted
    // presentation, but watch out if it later can be --
    // SWB
    CRC32 crc=new CRC32();
    byte[] buf=new byte[4096];
    int len=0;
    try {
      ZipFile zf=new ZipFile(fromzip);
      ZipOutputStream out=new ZipOutputStream(new FileOutputStream(tozip));
      out.setLevel(6);
      Enumeration entries=zf.entries();
      ZipEntry entry;
      int tot=zf.size();
      int cnt=0;
      if (act!=null&&chan>-1) {
        act.setNotificationName(chan,"Converting "+fromzip.getName());
      }
      while (entries.hasMoreElements()) {
        cnt++;
        entry=(ZipEntry)entries.nextElement();
        String name=entry.getName();
        if (!name.startsWith(".")&&endsWithImageExtension(name)) {
          BitmapFactory.Options o=new BitmapFactory.Options();
          o.inJustDecodeBounds=true;
          BitmapFactory.decodeStream(zf.getInputStream(entry),null,o);
          double scale=Math.min(1280.0/o.outWidth,1.0*theight/o.outHeight);
          int targetWidth=o.outWidth;
          targetWidth=(int)(scale*o.outWidth);
          // using a bitmap scaler ensure that very large bitmaps don't cause
          // heap to run out
          BitmapScaler scaler=new BitmapScaler(zf,entry,targetWidth);
          Bitmap bitmap=scaler.getScaled();
          ByteArrayOutputStream baos=new ByteArrayOutputStream();
          bitmap.compress(CompressFormat.PNG,100,baos);
          byte[] entbuf=baos.toByteArray();
          entry.setSize(entbuf.length);
          crc.reset();
          crc.update(entbuf);
          entry.setCrc(crc.getValue());
          out.putNextEntry(entry);
          out.write(entbuf);
        } else {
          if (ss==null||!name.equals("meta.txt")) {
            out.putNextEntry(entry); // straight copy
            InputStream in=zf.getInputStream(entry);
            while ((len=in.read(buf,0,buf.length))>-1) {
              out.write(buf,0,len);
            }
            in.close();
          }
        }
        if (act!=null&&chan>-1) {
          act.updateLoading(chan,(int)(100*cnt/tot),cnt+"/"+tot+" entries processed");
        } else if (act!=null) {
          SlideLoader.updateProgressMessage(act,cnt+"/"+tot+" Entries Processed");
        }
      }
      if (ss!=null) {
        // in case we need to write in because ss is provided (presumably with a
        // title)
        ss.updateProps();
        entry=new ZipEntry("meta.txt"); // write meta once done
        ByteArrayOutputStream baos=new ByteArrayOutputStream();
        ss.setProperty("lastUpdate",Long.toString(System.currentTimeMillis()));
        ss.getProperties().store(baos,null);
        byte[] entbuf=baos.toByteArray();
        baos.close();
        entry.setSize(entbuf.length);
        crc.reset();
        crc.update(entbuf);
        entry.setCrc(crc.getValue());
        out.putNextEntry(entry);
        out.write(entbuf);
      }
      out.finish();
      out.close();
      zf.close();
    } catch (Exception e) {
      MLog.e("Error in update of "+fromzip+":"+e.toString(),e);
    }
  }

  public SlideSet preloadSlides(String zipPath) {
    if (currentSlideSet!=null) {
      currentSlideSet.clearBitmaps(); //accidentally forgot this after fast thumbnail loading --SWB
    }
    ensureSlideSetMap();
    currentSlideSet=slideSetMap.get(new File(zipPath).getName());
    return(currentSlideSet);
  }
  
  private void ensureSlideSetMap() {
    if (slideSetMap==null) { //just in case
      MLog.v("Caught slidesetmap as null, reindexing...");
      indexSlideSets();
    }
  }

  /**
   * An implementation of loadSlides which load all thumbnails with entries from
   * the changed directory overriding those from the zip file. Thumbnails are
   * loaded directly for action thumbnails but are manufactured for full slide
   * thumbnails.
   * 
   * @param zipPath
   */
  public void loadSlidesUsingChanged(SlideSet ss) {
    buildingThumbs=true;
    abortThumbs=false;
    setThumbnailSizeFromPreference();
    String zipname=ss.getZipName();
    // iterate the action ids (which name thumbnails)
    for(String actionid:ss.getActionIds()) {
      if (abortThumbs) {
        MLog.v("Aborting thumbs");
        isDone=true;
        buildingThumbs=false;
        return;
      }
      Bitmap b=getSlideBitmap(ss,actionid,false);
      if (b!=null) {
        SlideAction sa=ss.getActionForId(actionid);
        if (sa!=null) {
          sa.setBitmap(b);
          sa.setBitmapName(actionid);
        } else {
          MLog.v("Did not find action for "+actionid);
        }
      }
    }
    if (abortThumbs) {
      MLog.v("Aborting thumbs");
      isDone=true;
      buildingThumbs=false;
      return;
    }
    Bitmap background=Bitmap.createBitmap(1280/downsamplesize,theight/downsamplesize,Config.ARGB_8888);
    Canvas c=new Canvas(background);
    for(int i=0;i<ss.getNumSlides();i++) {
      if (abortThumbs) {
        MLog.v("Aborting thumbs");
        isDone=true;
        buildingThumbs=false;
        return;
      }
      String name=ss.getSlideName(i);
      if (ss.hasSlide(name)) {
        Bitmap b=getSlideBitmap(ss,name,true);
        double scale=Math.min(1280.0/downsamplesize/b.getWidth(),1.0*theight/downsamplesize/b.getHeight());
        List<SlideAction> csactions=ss.getActions(name);
        c.drawColor(Color.BLACK);
        c.drawBitmap(b,null,new Rect(0,0,1280/downsamplesize,theight/downsamplesize),new Paint());
        if (csactions!=null) {
          for(SlideAction action:csactions) {
            if (abortThumbs) {
              MLog.v("Aborting thumbs");
              isDone=true;
              buildingThumbs=false;
              return;
            }
            if (action.supportsThumbnail()&&action.isThumbVisible()) {
              Rect bnds=action.getBounds();
              drawActionThumbnail(action,c,new Rect(bnds.left/downsamplesize,bnds.top/downsamplesize,bnds.right/downsamplesize,
                  bnds.bottom/downsamplesize),false);
            }
          }
        }
        ss.addBitmap(name,background.copy(Bitmap.Config.ARGB_8888,true));
        if (activeGrid!=null) {
          activeGrid.postRefreshView();
        }
      }
    }
    isDone=true;
    buildingThumbs=false;
  }

  /*
   * Note, the below method will soon be removed if the new changed directory
   * stuff works well...
   */

  /**
   * This method loads all the thumbnails from the zip file. This includes
   * actions thumbnails which are loaded directly without scaling as well as
   * manufacturing thumbnails for the overall slides.
   * 
   * @param zipPath
   *          the path to the zip file
   */
  /*
  public void loadSlides(String zipPath) {
    // this may have to get bitmaps from the changed directory if available --
    // SWB
    buildingThumbs=true;
    abortThumbs=false;
    setThumbnailSizeFromPreference();
    // MLog.v("zipPath="+zipPath);
    if (currentSlideSet!=null)
      currentSlideSet.clearBitmaps(); // to diminish storage
    currentSlideSet=slideSetMap.get(new File(zipPath).getName());
    try {
      ZipFile zf=new ZipFile(zipPath);
      ZipEntry entry;
      Enumeration entries=zf.entries();
      // first pass needs to get bitmaps if any for rendering in second pass
      while (entries.hasMoreElements()) {
        if (abortThumbs) {
          MLog.v("Aborting thumbs");
          isDone=true;
          buildingThumbs=false;
          zf.close();
          return;
        }
        entry=(ZipEntry)entries.nextElement();
        String name=entry.getName();
        if (name.startsWith(".Image-")) {
          Bitmap b=BitmapFactory.decodeStream(zf.getInputStream(entry));
          SlideAction sa=currentSlideSet.getActionForId(name);
          if (sa!=null) {
            sa.setBitmap(b);
            sa.setBitmapName(name);
          } else {
            MLog.v("Did not find action for "+name);
          }
        }
      }
      if (abortThumbs) {
        MLog.v("Aborting thumbs");
        isDone=true;
        buildingThumbs=false;
        zf.close();
        return;
      }
      entries=zf.entries();
      Bitmap background=Bitmap.createBitmap(1280/downsamplesize,theight/downsamplesize,Config.ARGB_8888);
      // scale all thumbnails to the fullscreen downsampled size (which is what
      // it does in the real viewer)
      Canvas c=new Canvas(background);
      while (entries.hasMoreElements()) {
        if (abortThumbs) {
          MLog.v("Aborting thumbs");
          isDone=true;
          buildingThumbs=false;
          zf.close();
          return;
        }
        entry=(ZipEntry)entries.nextElement();
        String name=entry.getName();
        // MLog.v("entry name="+name);
        // this needs to load slides by name into a map -- should not assume
        // sequential -- SWB
        if (currentSlideSet.hasSlide(name)/* isPotentialSlideImage(name) ) {
          BitmapFactory.Options opts=new BitmapFactory.Options();
          opts.inSampleSize=downsamplesize; // down sample by 8 (power of two
          // for speed)
          Bitmap b=BitmapFactory.decodeStream(zf.getInputStream(entry),null,opts);
          double scale=Math.min(1280.0/downsamplesize/b.getWidth(),1.0*theight/downsamplesize/b.getHeight());
          /*
           * int w=(int)(scale*b.getWidth()); int h=(int)(scale*b.getHeight());
           * int nx=(1280/downsamplesize-w)/2; int
           * ny=(theight/downsamplesize-h)/2;
          
          List<SlideAction> csactions=currentSlideSet.getActions(name);
          c.drawColor(Color.BLACK);
          c.drawBitmap(b,null,new Rect(0,0,1280/downsamplesize,theight/downsamplesize),new Paint());
          if (csactions!=null) {
            for(SlideAction action:csactions) {
              if (abortThumbs) {
                MLog.v("Aborting thumbs");
                isDone=true;
                buildingThumbs=false;
                zf.close();
                return;
              }
              if (action.supportsThumbnail()&&action.isThumbVisible()) {
                /*
                 * if (c==null) { b=b.copy(Bitmap.Config.ARGB_8888,true); //
                 * make it mutable at // expense of copy // (but its just a //
                 * thumbnail at least) c=new Canvas(b); }
                 
                Rect bnds=action.getBounds();
                drawActionThumbnail(action,c,new Rect(bnds.left/downsamplesize,bnds.top/downsamplesize,bnds.right/downsamplesize,
                    bnds.bottom/downsamplesize),false);
              }
            }
          }
          currentSlideSet.addBitmap(name,background.copy(Bitmap.Config.ARGB_8888,true));
          if (activeGrid!=null) {
            activeGrid.postRefreshView();
          }
        }
      }
      zf.close();
    } catch (IOException e) {
      MLog.e("Error loading slides",e);
    }
    isDone=true;
    buildingThumbs=false;
  }
  */
  
  public void loadSlidesInBackground(SlideSet new_set, int num) {
	  	String zipPath=new_set.getZipName();
	    buildingThumbs=true;
	    setThumbnailSizeFromPreference();
	    if (new_set!=null)
	      new_set.clearBitmaps(); // to diminish storage
	    new_set=slideSetMap.get(new File(zipPath).getName());
	    try {
	      ZipFile zf=new ZipFile(zipPath);
	      ZipEntry entry;
	      Enumeration entries=zf.entries();
	      // first pass needs to get bitmaps if any for rendering in second pass
	      while (entries.hasMoreElements()) {
	        entry=(ZipEntry)entries.nextElement();
	        String name=entry.getName();
	        if (name.startsWith(".Image-")) {
	          Bitmap b=BitmapFactory.decodeStream(zf.getInputStream(entry));
	          SlideAction sa=new_set.getActionForId(name);
	          if (sa!=null) {
	            sa.setBitmap(b);
	            sa.setBitmapName(name);
	          } else {
	            MLog.v("Did not find action for "+name);
	          }
	        }
	      }
	      entries=zf.entries();
	      Bitmap background=Bitmap.createBitmap(1280/downsamplesize,theight/downsamplesize,Config.ARGB_8888);
	      // scale all thumbnails to the fullscreen downsampled size (which is what
	      // it does in the real viewer)
	      Canvas c=new Canvas(background);
	      while (entries.hasMoreElements()) {
	        entry=(ZipEntry)entries.nextElement();
	        String name=entry.getName();
	        // MLog.v("entry name="+name);
	        // this needs to load slides by name into a map -- should not assume
	        // sequential -- SWB
	        if (new_set.hasSlide(name)/* isPotentialSlideImage(name) */) {
	        	if(name.equals(new_set.getSlideName(num))){
	        		
	  	          BitmapFactory.Options opts=new BitmapFactory.Options();
	  	          opts.inSampleSize=downsamplesize; // down sample by 8 (power of two
	  	          // for speed)
	  	          Bitmap b=BitmapFactory.decodeStream(zf.getInputStream(entry),null,opts);

	  	          List<SlideAction> csactions=new_set.getActions(name);
	  	          c.drawColor(Color.BLACK);
	  	          c.drawBitmap(b,null,new Rect(0,0,1280/downsamplesize,theight/downsamplesize),new Paint());
	  	          if (csactions!=null) {
	  	            for(SlideAction action:csactions) {
	  	              if (action.supportsThumbnail()&&action.isThumbVisible()) {
	  	                Rect bnds=action.getBounds();
	  	                drawActionThumbnail(action,c,new Rect(bnds.left/downsamplesize,bnds.top/downsamplesize,bnds.right/downsamplesize,
	  	                    bnds.bottom/downsamplesize),false);
	  	              }
	  	            }
	  	          }
	        	}

	          new_set.addBitmap(name,background.copy(Bitmap.Config.ARGB_8888,true));
	        }
	      }
	      zf.close();
	    } catch (IOException e) {
	      MLog.e("Error loading slides",e);
	    }
	    isDone=true;
	    buildingThumbs=false;
	  }

}
