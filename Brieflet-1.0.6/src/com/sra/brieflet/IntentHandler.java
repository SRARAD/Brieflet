/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import zamzar.ZamZar;
import android.content.ContentResolver;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore.Images;

import com.sra.brieflet.actions.SlideAction;
import com.sra.brieflet.util.ClipboardUtil;
import com.sra.brieflet.util.DialogUtil;
import com.sra.brieflet.util.MLog;
import com.sra.brieflet.util.MTracker;

public class IntentHandler {

  public static void handleIncomingIntents(final Brieflet act) {
    try {
      Intent intent=act.getIntent();
      MLog.v("Here's the intent" + intent);
      if (intent.getBooleanExtra("briefletProcessed",false)) {
        MLog.v("Not processing intent again");
        return; // exit if already processed
      }
      if (intent!=null) {
        Uri uri=intent.getData();
        MLog.v("uri of content is:"+uri);
        if (uri!=null) MLog.v("scheme="+intent.getScheme());
        if (uri!=null&&(intent.getScheme().equals("content")||intent.getScheme().equals("file"))) {
          MLog.v("IntentHandler content or file uri");
          InputStream in=act.getContentResolver().openInputStream(intent.getData());
          File outfile=null;
          String stem=null;
          if (intent.getScheme().equals("file")) {
            Uri fileuri=intent.getData();
            String path=fileuri.getPath();
            int pos0=path.lastIndexOf("/");
            int pos1=0;
            if (pos0>-1) {
              pos1=path.indexOf(".",pos0);
            } else {
              pos1=path.indexOf(".");
            }
            if (pos0>-1) {
              if (pos1>-1) {
                stem=path.substring(pos0+1,pos1);    
              } else {
                stem=path.substring(pos0+1);
              }
            } else {
              if (pos1>-1) {
               stem=path.substring(0,pos1); 
              } else {
               stem=path;                
              }
            }
            outfile=act.findIncomingPresFile(stem);
            MLog.v("fileuri path="+fileuri.getPath()+" stem="+stem);
          } else {
            outfile=act.findIncomingPresFile();
          }
          File tmpfile=SlideLoader.makeTempFile();
          FileOutputStream out=new FileOutputStream(tmpfile);
          byte[] buf=new byte[1024];
          int len;
          while ((len=in.read(buf,0,buf.length))>-1) {
            out.write(buf,0,len);
          }
          out.close();
          in.close();
          SlideLoader.analyze(act,tmpfile,outfile,true,stem);
          MLog.v("Analyze returned -- marking intent as processed");
          intent.putExtra("briefletProcessed",true);
          // DialogUtil.infoDialog("Received Presentation","Presentation Received:"+outfile.getAbsolutePath(),null,act);
          // since we haven't constructed the view, not sure a refresh is
          // needed
          // and then perhaps auto-launched
        } else if (intent.getAction().equals(Intent.ACTION_VIEW)) {
          // if a VIEW but not content scheme
          if (intent.getType().startsWith("audio/")) {
            String path=intent.getDataString();
            int pos=path.indexOf("/mnt/sdcard");
            if (pos>-1) {
              path=path.substring(pos);
              SlideAction action=SlideAction.createInstance("audio|"+path+"|500|270|680|390|0|-1|0|0|0|0|-1|0");
              ClipboardUtil.copyActionToClipboard(action,act);
              MTracker.trackEvent("Import","Received link","Intent Handler",2);
              DialogUtil
                  .infoDialog(
                      "Imported URI",
                      "The received link as been converted to an Action and has been put on your clipboard.  Display the slide you'd like to place it on and choose Paste Action from the context menu.",
                      null,act);
            }
            intent.putExtra("briefletProcessed",true);
          }
        }
      }
      // Todo: audio will be received here as well but will go to clipboard,
      // intercept audio/* soon
      if (intent.getAction().equals(Intent.ACTION_SEND)) {
        Bundle extras=intent.getExtras();
        if (extras.containsKey(Intent.EXTRA_STREAM)) {
          Uri uri=(Uri)extras.getParcelable(Intent.EXTRA_STREAM);
          final String mimeType=intent.getType();
          if (uri.getScheme().equals("file")) {
            if (mimeType.startsWith("audio/")) {
              String path=uri.getPath();
              SlideAction action=SlideAction.createInstance("audio|"+path+"|500|270|572|342|0|-1|1|0|0|0|-1|0");
              ClipboardUtil.copyActionToClipboard(action,act);
              MTracker.trackEvent("Import","Received link","Intent Handler",2);
              DialogUtil
                  .infoDialog(
                      "Imported URI",
                      "The received link as been converted to an Action and has been put on your clipboard.  Display the slide you'd like to place it on and choose Paste Action from the context menu.",
                      null,act);
              intent.putExtra("briefletProcessed",true);
            }
          } else if (uri.getScheme().equals("content")) {
            ContentResolver cr=act.getContentResolver();
            MLog.v("uri="+uri);
            Cursor cursor=cr.query(uri,null,null,null,null); // this query fails
                                                             // for gallery
                                                             // images on
                                                             // Samsung (throws
                                                             // null pointer)
            // above is possibly due to exception in GalleryProvider.java line
            // 139 on the Samsung in writing to the parcel!
            cursor.moveToFirst();
            final String path=cursor.getString(cursor.getColumnIndexOrThrow(Images.Media.DATA));
            MLog.v("received media:"+path+" mimeType="+mimeType);
            intent.putExtra("briefletProcessed",true);
            if (mimeType.startsWith("audio/")) {
              MLog.v("saw audio but of content type -- not handled yet");
            } else {
              (new Thread() {

                public void run() {
                  try {
                    Thread.sleep(500);
                  } catch (Exception e) {}
                  ; // give a moment for the app to start up first
                  DialogUtil.showBusyCursor(act,"Importing Media...","Please wait...",null);
                  MTracker.trackEvent("Import","Received Media","Intent Handler",2);
                  SlideLoader.getDefaultInstance(act).createOrAppendToSlideSetFromImage(act," |*Received Media*",mimeType,path,
                      new Runnable() {

                        public void run() {
                          act.postRefreshView();
                        }
                      });
                }
              }).start();
            }
          }
        } else if (extras.containsKey(Intent.EXTRA_TEXT)) {
          String uri=extras.getString(Intent.EXTRA_TEXT);
          Bitmap bitmap=(Bitmap)extras.get("share_screenshot");
          if (bitmap!=null)
            MLog.v("bitmap="+bitmap+" "+bitmap.getWidth()+"x"+bitmap.getHeight());
          int map_index=uri.indexOf("http://m.google.com");
          if (map_index>0)
            uri=uri.substring(map_index);
          int pos=uri.indexOf("http://www.zamzar.com/getFiles.php");
          if (pos>-1) {
            MLog.v("Detected a zamzar download URI");
            final String str=uri;
            (new Thread() {
              public void run() {
                final File outf=ZamZar.downloadZamzar(act,str);
                /*
                if (outf!=null) {
                  act.postRefreshView(); //I think there might be danger act is no longer running or displayed -- how to catch this case???
                  act.runOnUiThread(new Runnable() {
                    public void run() {
                      SlideLoader.analyze(act,outf);
                    }
                  });
                }
                */
              }
            }).start();
            intent.putExtra("briefletProcessed",true);
            return;
          }

          SlideAction action=SlideAction.createInstance("intent|android.intent.action.VIEW|"+uri+"|500|270|680|390|1");
          action.setBitmap(bitmap);
          ClipboardUtil.copyActionToClipboard(action,act);
          intent.putExtra("briefletProcessed",true);
          MTracker.trackEvent("Import","Received link","Intent Handler",2);
          DialogUtil
              .infoDialog(
                  "Imported URI",
                  "The received link as been converted to an Action and has been put on your clipboard.  Display the slide you'd like to place it on and choose Paste Action from the context menu.",
                  null,act);
        }
      }
      MLog.v("onCreate but with intent: "+intent);
    } catch (Throwable err) {
      MLog.e("Error handling incoming intent",err);
    }
  }

}
