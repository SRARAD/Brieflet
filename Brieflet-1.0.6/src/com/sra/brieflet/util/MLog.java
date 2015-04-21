/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet.util;

import java.io.ByteArrayOutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

import android.util.Log;

import com.sra.brieflet.Brieflet;

public class MLog {

  public static void v(String msg) {
    if (Brieflet.ENABLE_LOGGING) {
      Log.v(Brieflet.TAG,Brieflet.TAG+": "+msg);
    }
  }

  public static void e(String msg,Throwable e) {
    if (Brieflet.ENABLE_LOGGING) {
      /*
      ByteArrayOutputStream baos=new ByteArrayOutputStream();
      PrintWriter out=new PrintWriter(new OutputStreamWriter(baos));
      e.printStackTrace(out);
      out.close();
      */
      Log.e(Brieflet.TAG,Brieflet.TAG+": "+msg,e);
    }
  }
}
