/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
package com.sra.brieflet.util;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.text.Editable;
import android.view.View;
import android.widget.EditText;

import com.sra.brieflet.SlideLoader;


public class DialogUtil {
  
  /**
   * Show a confirmation prompt before executing a Runnable
   * @param message the message to display
   * @param runnable a runnable to execute if they click on Yes
   * @param context the context in which to build the dialog
   */
  public static void guardAction(String message,final Runnable runnable,Context context) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(message)
           .setCancelable(false)
           .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int id) {
                 runnable.run();
               }
           })
           .setNegativeButton("No", new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int id) {
                    dialog.cancel();
               }
           });
    AlertDialog alert = builder.create();
    alert.show();
  }
  
  public static void guardAction(String message,final Runnable runnable,final Runnable runnable2,Context context) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(message)
           .setCancelable(false)
           .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int id) {
                 runnable.run();
               }
           })
           .setNegativeButton("No", new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int id) {
                 dialog.cancel();
                 runnable2.run();
               }
           });
    AlertDialog alert = builder.create();
    alert.show();
  }
  
  public static void getTextDialog(String title,String message,String contents,final StringResultListener listener,Context context) {
    final EditText input = new EditText(context);
    if (contents!=null) input.setText(contents);
    new AlertDialog.Builder(context)
    .setTitle(title)
    .setMessage(message)
    .setView(input)
    .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
            Editable value = input.getText();
            listener.setResult(value.toString());
        }
    }).setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog, int whichButton) {
        }
    }).show();    
  }
  
  public static void infoDialog(String title,String message,final Runnable runnable,Context context) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    builder.setMessage(message).setTitle(title)
           .setCancelable(false)
           .setPositiveButton("Ok", new DialogInterface.OnClickListener() {
               public void onClick(DialogInterface dialog, int id) {
                 if (runnable!=null) runnable.run();
               }
           });
    AlertDialog alert = builder.create();
    alert.show();
  }
  
  public static void showBusyCursor(final View view,final String title,final String msg,final Runnable runnable ){
    view.postDelayed(new Runnable() { public void run() {
      startProgress(view.getContext(),title,msg,runnable);
    }},100);
  }
  
  private static void startProgress(Context context,String title,String msg,final Runnable runnable) {
    if (SlideLoader.isDone)return; 
    final ProgressDialog pd = ProgressDialog.show(context, title, msg, true, false);
    SlideLoader.currentpd=pd;
    new Thread(new Runnable(){
       public void run(){
          long t1=System.currentTimeMillis();
          long t2=t1+200000; //make sure it stops after 200 seconds if something weird happens
          while (!SlideLoader.isDone && t1<t2){
             t1=System.currentTimeMillis();
             try { Thread.sleep(100); } catch (Exception e) {}; //dont' spin -- better to stop thread though
          }
          pd.dismiss();
          SlideLoader.currentpd=null;
          if (runnable!=null)
             runnable.run();
       }
    }).start();
  }
  
  public static void showBusyCursor(final Activity activity,final String title,final String msg,final Runnable runnable ){
	if(activity!=null){
    activity.runOnUiThread(new Runnable() { public void run() {
      startProgress(activity,title,msg,runnable);
    }});
	}
  }  
  
  public static void chooseArrayItem(final Context context,String title,int labelResourceId,final int valueResourceId,final StringResultListener listener) {
    AlertDialog.Builder builder = new AlertDialog.Builder(context);
    CharSequence[] choices0=context.getResources().getStringArray(labelResourceId);
    //hack
    CharSequence[] choices=new CharSequence[choices0.length+1];
    for(int i=0;i<choices0.length;i++) choices[i]=choices0[i];
    choices[choices0.length]="Use Default";
    final int last=choices0.length;
    builder.setTitle(title);
    builder.setItems(choices,new DialogInterface.OnClickListener() {
          public void onClick(DialogInterface dialog,int item) {
            String[] values=context.getResources().getStringArray(valueResourceId);
            if (item==last) {
              listener.setResult("Use Default");
            } else {
              listener.setResult(values[item]);
            }
          }
    });
    AlertDialog alert = builder.create();  
    alert.show();
  } 
}
