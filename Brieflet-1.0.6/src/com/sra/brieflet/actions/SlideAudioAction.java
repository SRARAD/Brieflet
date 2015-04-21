/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
/**
 * A slide action for embedded video
 */
package com.sra.brieflet.actions;

import android.graphics.Rect;

public class SlideAudioAction extends SlideAction implements MediaAction {
  
  public final static int CONTROL_PLAY=0;
  public final static int CONTROL_PAUSE=1;
  public final static int CONTROL_STOP=2;
  
  String uri; //uri to content or null/empty if this is just a control command
  long startMillis; //start offset in millis
  long durationMillis; //duration in millis (-1 means end) -- not supported yet
  boolean loop=false; //loop the clip
  boolean autoStart=false; //autostart the clip
  boolean cont=false; //continue playing after transition
  float volume=0.7f; //volume to play clip
  int control=CONTROL_PLAY; //transport control command
  
  public SlideAudioAction(String str) {
    //audio|uri|left|top|right|bottom|start|duration|thumb|loop|autoStart|cont|volume|control|bitmapname
    String delim=str.substring(5,6);
    String[] parts=str.split("\\"+delim);
    uri=parts[1];
    if (uri.equals("null")||uri.equals("")) {
      uri=null;
    }
    name="Audio Action For "+uri;
    int left=Integer.parseInt(parts[2]);
    int top=Integer.parseInt(parts[3]);
    int right=Integer.parseInt(parts[4]);
    int bottom=Integer.parseInt(parts[5]);
    rect=new Rect(left,top,right,bottom);
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
    if (parts.length>11) {
      cont=(parts[11].equals("1"));
    }
    if (parts.length>12) {
      volume=(Float.parseFloat(parts[12]));
    }
    if (parts.length>13) {
      control=Integer.parseInt(parts[13]);
    }
    if (parts.length>14) {
      setBitmapName(parts[14]);
    }
  }
  
  public String getUri() {
    return uri;
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
  
  public void setURIString(String uri) {
     this.uri=uri;
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
  
  public boolean isContinuous() {
    return cont;
  }
  
  public void setContinuous(boolean cont) {
    this.cont=cont;
  }
  
  public float getVolume() {
    return volume;
  }
  
  public void setVolume(float volume) {
    this.volume=volume;
  }
  
  public int getControl() {
    return control;
  }
  
  public void setControl(int control) {
    this.control=control;
  }

  public void setUri(String uri) {
    this.uri=uri;
  }
  
  public void setStartMillis(long startMillis) {
    this.startMillis=startMillis;
  }
  
  public void setDurationMillis(long durationMillis) {
    this.durationMillis=durationMillis;
  }

  private void wbool(StringBuilder sb,boolean var) {
    if (var) {
      sb.append("|1");
    } else {
      sb.append("|0");
    }
  }

  //audio|uri|left|top|right|bottom|start|duration|thumb|loop|cont|autoStart|volume|bitmapname
  @Override
  public String getPropertyString() {
    StringBuilder sb=new StringBuilder();
    sb.append("audio|");
    if (uri==null) {
      sb.append("null"); //just to be explicit about it
    } else {
      sb.append(uri);
    }
    sb.append("|");
    sb.append(rect.left+"|");
    sb.append(rect.top+"|");
    sb.append(rect.right+"|");
    sb.append(rect.bottom+"|");
    sb.append(startMillis+"|");
    sb.append(durationMillis);
    wbool(sb,thumbVisible);
    wbool(sb,loop);
    wbool(sb,autoStart);
    wbool(sb,cont);
    sb.append("|"+volume);
    sb.append("|"+control);
    if (getBitmapName()!=null) {
      sb.append("|"+getBitmapName());
    }
    return(sb.toString());
  }
  
}