/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
package com.sra.brieflet;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import com.sra.brieflet.actions.SlideAction;
import com.sra.brieflet.util.MLog;

public class SlideInfo {
  private Uri bitmap_file;
  private Bitmap bitmap;
  Context context;
  private List<SlideAction> actions=new ArrayList<SlideAction>();
  
  public SlideInfo(Uri file, Context c) {
    bitmap_file=file;
    context=c;
  }
  
  public SlideInfo(Bitmap b){
      bitmap=b;
  }
  
  public void addAction(SlideAction action) {
    actions.add(action);
  }
  
  public InputStream getBitmapInputStream() throws FileNotFoundException {
    return(context.getContentResolver().openInputStream(bitmap_file));
  }
  
  public Bitmap getBitmapFromFile() {
      try {
        return BitmapFactory.decodeStream(context.getContentResolver().openInputStream(bitmap_file));
    } catch (FileNotFoundException e) {
        MLog.e("Error decoding file: " +bitmap_file, e);
        e.printStackTrace();
        return null;
    }
  }
  public Bitmap getBitmapFromMem(){
      return bitmap;
  }
  
  public List<SlideAction> getActions() {
    return(actions);
  }
}