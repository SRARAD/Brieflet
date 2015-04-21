/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
/**
 * An embedded slide action which launches an intent
 */
package com.sra.brieflet.actions;

import android.graphics.Rect;

public class SlideIntentAction extends SlideAction {
  String intentString;
  String uriString;
  
  public SlideIntentAction(String str) {
    //intent<delim>...
    String delim=str.substring(6,7);
    String[] parts=str.split("\\"+delim);
    intentString=backwardCompat(parts[1]);
    uriString=parts[2];
    name="Intent Action "+intentString+" with URI "+uriString;
    int left=Integer.parseInt(parts[3]);
    int top=Integer.parseInt(parts[4]);
    int right=Integer.parseInt(parts[5]);
    int bottom=Integer.parseInt(parts[6]);
    if (parts.length>7) {
      thumbVisible=(parts[7].equals("1"));
    }
    if (parts.length>8) {
      setBitmapName(parts[8]);
    }
    rect=new Rect(left,top,right,bottom);
  }
  
  private String backwardCompat(String pkg) {
    if (pkg.equals("com.sra.presenter")) {
      return("com.sra.brieflet");
    } else if (pkg.startsWith("com.sra.presenter.")) {
      return("com.sra.brieflet."+pkg.substring(18));
    } else {
      return(pkg);
    }
  }
  
  public String getIntentString() {
    return intentString;
  }
  
  public String getUriString() {
    return uriString;
  }
  public void setURIString(String uri){
     uriString=uri;
  }
  @Override
  public String getPropertyString() {
    StringBuilder sb=new StringBuilder();
    sb.append("intent|");
    sb.append(intentString);
    sb.append("|");
    sb.append(uriString); //might choose a delim guaranteed safe against this uri
    sb.append("|");
    sb.append(rect.left+"|");
    sb.append(rect.top+"|");
    sb.append(rect.right+"|");
    sb.append(rect.bottom+"|");
    if (thumbVisible) {
      sb.append("1");
    } else {
      sb.append("0");
    }
    if (getBitmapName()!=null) {
      sb.append("|"+getBitmapName());
    }
    return(sb.toString());
  }

  @Override
  public boolean supportsThumbnail() {
    if (bitmap!=null) return(true);
    return(intentString.startsWith("com.sra.brieflet.SlideViewer")); //slide intents support thumbnails
  }

}