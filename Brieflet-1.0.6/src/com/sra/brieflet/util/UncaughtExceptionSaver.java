/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
package com.sra.brieflet.util;

/**
 * ErrorCapture 
 * by Marcus -Ligi- Bueschleb 
 * http://ligi.de
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as 
 * published by the Free Software Foundation; 
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details. 
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 * 
 **/


import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.Thread.UncaughtExceptionHandler;


public class UncaughtExceptionSaver implements UncaughtExceptionHandler {
   UncaughtExceptionHandler oldHandler;

   public UncaughtExceptionSaver(UncaughtExceptionHandler oldHandler ){
      this.oldHandler=oldHandler;
   }

   @Override
   public void uncaughtException(Thread thread, Throwable throwable) {

      try {
         // version and timestamp for filename 
         // TODO check if additional entropy like some random is needed here
         String filename = ErrorCaptureMetaInfo.getFilesPath()+"/" 
                     + ErrorCaptureMetaInfo.getAppVersion() 
                     + "-"+System.currentTimeMillis()
                     + ErrorCaptureMetaInfo.getFileSuffix();

         MLog.v("Writing unhandled exception to: " +filename);
         // Write the stacktrace to disk
         BufferedWriter bos = new BufferedWriter(new FileWriter(filename));
         bos.write("Android Version: " + ErrorCaptureMetaInfo.getAndroidVersion()+ "\n");
         bos.write("Phone Model: " + ErrorCaptureMetaInfo.getPhoneModel() + "\n");
         bos.write("ErrorCapture Version: " + ErrorCaptureMetaInfo.getErrorCaptureVersion() + "\n");
         bos.write("Stacktrace: \n "+ getThrowableStackAsString(throwable));
         bos.write("Log: \n "+ Log.getCachedLog());
         bos.flush();
         bos.close();

      } catch (Exception ebos) {
         // Nothing much we can do about this - the game is over
         MLog.e( "Error saving exception stacktrace", ebos);
      }

      MLog.v(getThrowableStackAsString(throwable));

      //call original handler
      oldHandler.uncaughtException(thread, throwable);
   }


    public static String getThrowableStackAsString(Throwable t) {
     try {
       StringWriter sw = new StringWriter();
       t.printStackTrace(new PrintWriter(sw));
       return sw.toString();
     }
     catch(Exception e2) {
       return "bad exception stack";
     }
    }
}
