/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
/**
 * A slide action for embedded video
 */
package com.sra.brieflet.actions;

import android.graphics.Rect;

import com.sra.brieflet.util.MLog;

public class SlideVideoAction extends SlideAction implements MediaAction {
  String filename;
  long startMillis;
  long durationMillis;
  boolean loop=false;
  boolean autoStart=false;
  
  public SlideVideoAction(String str) {
    //video<delim>
    String delim=str.substring(5,6);
    String[] parts=str.split("\\"+delim);
    filename=parts[1];
    name="Video Action For "+filename;
    int left=Integer.parseInt(parts[2]);
    int top=Integer.parseInt(parts[3]);
    int width=Integer.parseInt(parts[4]);
    int height=Integer.parseInt(parts[5]);
    rect=new Rect(left,top,left+width,top+height);
    startMillis=Long.parseLong(parts[6]);
    durationMillis=Long.parseLong(parts[7]);
    if (parts.length>8) {
      thumbVisible=(parts[8].equals("1"));
    }
    if (parts.length>9) {
      loop=(parts[9].equals("1"));
    }
    if (parts.length>10) {
      autoStart=(parts[10].equals("1"));
    }
  }
  
  public String getFilename() {
    return filename;
  }
  
  public boolean supportsThumbnail() {
    return(true);
  }
  
  public long getStartMillis() {
    return startMillis;
  }
  
  public long getDurationMillis() {
    return durationMillis;
  }
  
  public void setURIString(String filename){
     this.filename=filename;
  }
  
  public int getWidth() {
    return(rect.width());
  }
  
  public int getHeight() {
    return(rect.height());
  }
  
  public boolean isLoop() {
    return loop;
  }
  
  public void setLoop(boolean loop) {
    this.loop=loop;
  }
  
  public boolean isAutoStart() {
    return autoStart;
  }
  
  public void setAutoStart(boolean autoStart) {
    this.autoStart=autoStart;
  }

  @Override
  public String getPropertyString() {
    StringBuilder sb=new StringBuilder();
    sb.append("video|");
    sb.append(filename);
    sb.append("|");
    sb.append(rect.left+"|");
    sb.append(rect.top+"|");
    sb.append(rect.width()+"|");
    sb.append(rect.height()+"|");
    sb.append(startMillis+"|");
    sb.append(durationMillis);
    if (thumbVisible) {
      sb.append("|1");
    } else {
      sb.append("|0");
    }
    if (loop) {
      sb.append("|1");
    } else {
      sb.append("|0");
    }
    if (autoStart) {
      sb.append("|1");
    } else {
      sb.append("|0");
    }
    return(sb.toString());
  }
  
}