/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
package com.sra.brieflet.util;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;

public class StateDetector extends BroadcastReceiver {
  
  //HDMI detection does not appear to work
  
  //String str="android.intent.action.HEADSET_PLUG";
  String str="android.intent.action.HDMI_PLUG";
  
  public void register(Context context) {
    IntentFilter intentFilter=new IntentFilter(str);
    if (context.registerReceiver(this,intentFilter)==null) {
      MLog.v("Could not register HDMI receiver.");
    }
  }
  
  public void unregister(Context context) {
    context.unregisterReceiver(this);
  }

  @Override
  public void onReceive(Context context,Intent intent) {
    if (intent.getAction().equalsIgnoreCase(str)) {
      MLog.v("saw hdmi change");      
    }
  }

}
