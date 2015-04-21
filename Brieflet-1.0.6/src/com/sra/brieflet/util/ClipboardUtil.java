/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet.util;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.ClipData;
import android.content.ClipDescription;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;

import com.sra.brieflet.Brieflet;
import com.sra.brieflet.SlideInfo;
import com.sra.brieflet.SlideLoader;
import com.sra.brieflet.SlideSet;
import com.sra.brieflet.SlideViewer;
import com.sra.brieflet.actions.SlideAction;

public class ClipboardUtil {

  private static String clipboardDir="/mnt/sdcard/Slides/copy";

  public static File getCopyDir() {
    File file=new File(clipboardDir);
    if (!file.exists())
      file.mkdirs();
    return(file);
  }

  public static File getCopyFileName(String prefix,String slidename) {
    return(getCopyFileName(prefix,slidename,false));
  }
  
  public static File getCopyFileName(String prefix,String slidename,boolean makeOnly) {
    File dir=getCopyDir();
    if (slidename.indexOf("/")>-1) {
      slidename=slidename.replaceAll("/","-"); //need to take out slashes if files are in subdirs
    }
    String cand=null;
    if (slidename.startsWith(".Image-")) {
      cand=prefix+"-"+slidename.substring(1);      
    } else {
      cand=prefix+"-"+SlideSet.stripExtension(slidename);
    }
    File file=new File(dir,cand+".PNG");
    if (makeOnly) return(file);
    if (file.exists()) {
      int c=0;
      while (true) {
        file=new File(dir,cand+(c++)+".PNG");
        if (!file.exists())
          break;
      }
    }
    return(file);
  }
  public static void copySlidesToClipboard(SlideSet ss, List<Integer> slides, Context context) throws Exception{
    //TODO: this needs to copy out any stored bitmaps for actions and reference the bitmaps with the actions
    //just copy out the named bitmap in a filename in the copy directory using the zip name and the bitmap name (not change to what's on clipboard)
      SlideLoader sl=SlideLoader.getDefaultInstance(context);
      ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
      ClipData clip=ClipData.newUri(context.getContentResolver(),"Brieflet Slide",Uri.fromFile(getCopyDir()));
      File zipname=new File(ss.getZipName());
      clip.addItem(new ClipData.Item(ss.stripExtension(zipname.getName()))); //add stem name for retrieval of thumbs if any
      for(Integer num: slides){
          File copyFile=getCopyFileName(ss.stripExtension(zipname.getName()),ss.getSlideName(num));
          // write out bitmap to our copy folder
          sl.copyOutSlide(num, copyFile.getPath());
          //MLog.v("Copy file name "+copyFile.getPath());
          
          // get the actions
          List<SlideAction> actions=ss.getActions(num);
          clip.addItem(new ClipData.Item(Uri.fromFile(copyFile)));
          if (actions!=null) {
              for(SlideAction action:actions) {
                if (action.getBitmapName()!=null) {
                  File copyFile2=getCopyFileName(ss.stripExtension(zipname.getName()),action.getBitmapName());
                  sl.copyOutSlide(action.getBitmapName(),copyFile2.getPath());
                }
                clip.addItem(new ClipData.Item(action.getPropertyString()));
              }
          }

      }
      clipboard.setPrimaryClip(clip);
  }

  /*
  public static void copySlideToClipboard(SlideSet ss,int num,Context context) throws Exception {
    SlideLoader sl=SlideLoader.getDefaultInstance(context);
    Bitmap bitmap=sl.getSlideBitmap(num);
    File zipname=new File(ss.getZipName());
    File copyFile=getCopyFileName(ss.stripExtension(zipname.getName()),ss.getSlideName(num));
    // write out bitmap to our copy folder
    FileOutputStream out=new FileOutputStream(copyFile);
    bitmap.compress(CompressFormat.PNG,100,out);
    out.close();
    // get the actions
    List<SlideAction> actions=ss.getActions(num);
    ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clip=ClipData.newUri(context.getContentResolver(),"Brieflet Slide",Uri.fromFile(copyFile));
    // add the action strings as additional items
    if (actions!=null) {
      for(SlideAction action:actions) {
        clip.addItem(new ClipData.Item(action.getPropertyString()));
      }
    }
    clipboard.setPrimaryClip(clip);
  }
  */
  
  public static void copySlideLinkToClipboard(SlideSet ss,int num,Context context) throws Exception {
    ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
    Intent intent=new Intent(context,SlideViewer.class);
    intent.putExtra("type","link");
    intent.putExtra("slidePath",ss.getZipName());
    intent.putExtra("slideName",ss.getSlideName(num));
    ClipData clip=ClipData.newIntent("Brieflet Slide Action",intent);
    clipboard.setPrimaryClip(clip);
  }
  
  public static void copySlideLinksToClipboard(SlideSet ss, List<Integer> slides, Context context) throws Exception{
      ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
      Intent intent=new Intent(context,SlideViewer.class);
      intent.putExtra("type","link");
      intent.putExtra("slidePath",ss.getZipName());
      intent.putExtra("numSlides",slides.size());
      int x=0;
      for(Integer i : slides){
          intent.putExtra("slideName"+x,ss.getSlideName(i));
          List<SlideAction> actions= ss.getActions(i);
          if(actions==null)
              actions=new ArrayList<SlideAction>();

          intent.putExtra("numActions"+x,actions.size());
 
          for(int y=0; y<actions.size(); y++){
              SlideAction a = actions.get(y);
              //Note: this could be a problem if the action has a thumbnail name that's not being sent along
              //it will either not resolve or will look up the wrong thumbnail (but for the thumbnail of the thumbnail only)
              intent.putExtra("slideAction"+x+""+y, a.getPropertyString());
          }
          x++;
      }
      ClipData clip=ClipData.newIntent("Brieflet Slide Action",intent);
      clipboard.setPrimaryClip(clip);
  }
  
  public static void copyActionToClipboard(SlideAction action,Context context) throws Exception {
    ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
    Intent intent=new Intent(context,Brieflet.class);
    intent.putExtra("type","generic");
    intent.putExtra("propstring",action.getPropertyString());
    if (action.getBitmap()!=null) {
      intent.putExtra("bitmap",action.getBitmap());
    }
    ClipData clip=ClipData.newIntent("Brieflet Slide Action",intent);
    clipboard.setPrimaryClip(clip);
  }

  public static boolean isSlideActionOnClipboard(Context context) {
    ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
    if (!clipboard.hasPrimaryClip())
      return(false);
    ClipData clipData=clipboard.getPrimaryClip();
    int cnt=clipData.getItemCount();
    if (cnt==0) return(false);
    Intent intent=clipData.getItemAt(0).getIntent();
    MLog.v("intent="+intent);
    ClipDescription cd=clipboard.getPrimaryClipDescription();
    String mimeType=cd.getMimeType(0);
    CharSequence label=cd.getLabel();
    MLog.v("mimeType="+mimeType+" label="+label);
    MLog.v("label on clip="+label);
    return(label!=null && label.equals("Brieflet Slide Action"));
  }
  
  public static Intent getSlideActionFromClipboard(Context context) throws Exception {
    if (!isSlideActionOnClipboard(context))
      return(null);
    ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clipData=clipboard.getPrimaryClip();
    int cnt=clipData.getItemCount();
    if (cnt==0) return(null);
    Intent intent=clipData.getItemAt(0).getIntent();
    return(intent);
  }
  
  public static boolean isSlideOnClipboard(Context context) {
    ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
    if (!clipboard.hasPrimaryClip())
      return(false);
    ClipDescription cd=clipboard.getPrimaryClipDescription();
    String mimeType=cd.getMimeType(0);
    CharSequence label=cd.getLabel();
    MLog.v("mimeType="+mimeType+" label="+label);
    return(label!=null && label.equals("Brieflet Slide"));
  }

  public static void cleanup(Context context) {
    if (isSlideOnClipboard(context)) {
      ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
      clipboard.setPrimaryClip(ClipData.newPlainText("",""));
    }
    File dir=getCopyDir();
    for(File file:dir.listFiles()) {
      if (file.isFile()) {
        MLog.v("Deleting:"+file.getAbsolutePath());
        file.delete();
      }
    }
    dir.delete();
  }
  
  /*
  public static SlideInfo getSlideFromClipboard(Context context) throws Exception {
    if (!isSlideOnClipboard(context))
      return(null);
    ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
    ClipData clipData=clipboard.getPrimaryClip();
    int cnt=clipData.getItemCount();
    if (cnt==0) return(null);
    Uri uri=clipData.getItemAt(0).getUri();
    //Bitmap bitmap=BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));
    SlideInfo info=new SlideInfo(uri,context);
    for(int i=1;i<cnt;i++) {
      ClipData.Item item=clipData.getItemAt(i);
      info.addAction(SlideAction.createInstance(item.getText().toString()));
    }
    return(info);
  }
  */
  
  public static List<SlideInfo> getSlidesFromClipboard(Context context){
    //TODO: this needs to handle named bitmaps associated with actions
    //just check if an action has a bitmap name on it and grab the corresponding file in the copy directory
      if (!isSlideOnClipboard(context))
          return(null);
      List<SlideInfo> slides = new ArrayList<SlideInfo>();
      ClipboardManager clipboard=(ClipboardManager)context.getSystemService(Context.CLIPBOARD_SERVICE);
      ClipData clipData=clipboard.getPrimaryClip();
      int cnt=clipData.getItemCount();
      if (cnt==0) return(null);
      MLog.v("clipboard count="+cnt);
      SlideInfo currSlideInfo=null;
      ClipData.Item item1=clipData.getItemAt(1);
      String stem=(String)item1.getText();
      MLog.v("stem="+stem);
      for(int i=2; i<cnt;i++){
          ClipData.Item item=clipData.getItemAt(i);
          if(item.getUri()!=null){
              //new slide
              //Bitmap bitmap=BitmapFactory.decodeStream(context.getContentResolver().openInputStream(item.getUri()));
            MLog.v(i+"=uri="+item.getUri());
              currSlideInfo=new SlideInfo(item.getUri(),context);
              slides.add(currSlideInfo);
          }else{
              //slide actions
              if(currSlideInfo!=null){
                MLog.v(i+"=action="+item.getText());
                SlideAction sa=SlideAction.createInstance(item.getText().toString());
                if (sa.getBitmapName()!=null) {
                  File copyFile2=getCopyFileName(stem,sa.getBitmapName(),true);
                  MLog.v("Looking for bitmap name:"+sa.getBitmapName()+" in "+copyFile2.getAbsolutePath());
                  if (copyFile2.exists()) {
                   sa.setBitmapName(null);
                   sa.setBitmap(BitmapFactory.decodeFile(copyFile2.getAbsolutePath()));
                  }
                }
                  currSlideInfo.addAction(sa);
              }
              
          }
      }
      return slides;
  }

}
