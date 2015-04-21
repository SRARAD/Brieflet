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



import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;

import com.sra.brieflet.ErrorCapture;

public class ErrorCaptureEmailSender {

   public static boolean sendStackTraces(final String email, final Context context) {
      if (ErrorCapture.getStackTraceFiles().length > 0) {
         new AlertDialog.Builder(context).setTitle("Problem Report")
         .setMessage("A problem was detected with a recent run of Brieflet.  To help us improve the application, could you please send us the error report?")
               .setPositiveButton("Send", new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int whichButton) {
                     final Intent emailIntent = new Intent(android.content.Intent.ACTION_SEND);
                     emailIntent.setType("plain/text");
                     emailIntent.putExtra(android.content.Intent.EXTRA_EMAIL, new String[]{email});
                     emailIntent.putExtra(android.content.Intent.EXTRA_SUBJECT, "Error Report for " + ErrorCaptureMetaInfo.getAppPackageName());
                     emailIntent.putExtra(android.content.Intent.EXTRA_TEXT, ErrorCapture.getStackTraceText(10));
                     context.startActivity(Intent.createChooser(emailIntent, "Send Error Report"));
                     ErrorCapture.deleteStacktraceFiles();
                  }
               }).setNegativeButton("No", new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int whichButton) {
                     ErrorCapture.deleteStacktraceFiles();
                  }
               }).setNeutralButton("Later", new DialogInterface.OnClickListener() {
                  public void onClick(DialogInterface dialog, int whichButton) {
                  }
               })
               .show();
         return true;
     }
      return false;
   }
}
