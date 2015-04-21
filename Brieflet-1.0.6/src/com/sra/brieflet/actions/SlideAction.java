/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
/*
 * Base class for actions which may be embedded on slides. 
 */
package com.sra.brieflet.actions;

import android.graphics.Bitmap;
import android.graphics.Rect;

public abstract class SlideAction {
  Rect rect;
  String name;
  boolean thumbVisible=true;
  String bitmapName;
  Bitmap bitmap;
  
  /*
   * Some actions may have a bitmap furnished at
   * creation time that needs to be persisted.  Others
   * will generate them from internal system resources
   * as necessary.
   */
  public void setBitmap(Bitmap bitmap) {
    this.bitmap=bitmap;
    //if (bitmap!=null) thumbVisible=true;
  }
  
  public Bitmap getBitmap() {
    return(bitmap);
  }
  
  public void setBitmapName(String name) {
    this.bitmapName=name;
  }
  
  public String getBitmapName() {
    return(bitmapName);
  }
  
  public Rect getBounds() {
    return(rect);  
  }
  
  public void setBounds(Rect rect) {
    this.rect=rect;
  }
  
  public String getName() {
    return(name);
  }
  
  public boolean isThumbVisible() {
    return(thumbVisible);
  }
  
  public void setThumbnailVisible(boolean visible) {
    thumbVisible=visible;
  }
  
  public int getX() {
    return(rect.left);
  }
  
  public int getY() {
    return(rect.top);
  }
  
  public int getLeft() {
    return(rect.left);
  }
  
  public int getRight() {
    return(rect.right);
  }
  
  public int getTop() {
    return(rect.top);
  }
  
  public int getBottom() {
    return(rect.bottom);
  }
  
  public abstract String getPropertyString(); 
  
  public abstract void setURIString(String uri); 
  
  public abstract boolean supportsThumbnail();
  
  public static SlideAction createInstance(String str) {
    if (str.startsWith("video")) {
      return(new SlideVideoAction(str));
    } else if (str.startsWith("intent")) {
      return(new SlideIntentAction(str));
    } else if (str.startsWith("launch")) {
      return(new LaunchAction(str));
    } else if (str.startsWith("audio")) {
      return(new SlideAudioAction(str));
    } else {
      return(null);
    }
  }
}