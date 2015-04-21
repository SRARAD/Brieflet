/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
/**
 * An embedded slide action which launches an intent
 */
package com.sra.brieflet.actions;

import android.graphics.Rect;

public class LaunchAction extends SlideAction {
  String packageString;
  String classString;
  
  public LaunchAction(String str) {
    //intent<delim>...
    String delim=str.substring(6,7);
    String[] parts=str.split("\\"+delim);
    packageString=parts[1];
    classString=parts[2];
    name="Launch Action For "+classString;
    int left=Integer.parseInt(parts[3]);
    int top=Integer.parseInt(parts[4]);
    int right=Integer.parseInt(parts[5]);
    int bottom=Integer.parseInt(parts[6]);
    if (parts.length>7) { //backward compat, might not have parm
      thumbVisible=(parts[7].equals("1"));
    }
    rect=new Rect(left,top,right,bottom);
  }
  
  public String getPackageString() {
    return packageString;
  }
  
  public boolean supportsThumbnail() {
    return(true);
  }
  
  public String getClassString() {
    return classString;
  }
  public void setURIString(String classStr){
     classString=classStr;
  }
  @Override
  public String getPropertyString() {
    StringBuilder sb=new StringBuilder();
    sb.append("launch|");
    sb.append(packageString);
    sb.append("|");
    sb.append(classString);
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
    return(sb.toString());
  }

}