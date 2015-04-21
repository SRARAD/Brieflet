/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sra.brieflet.R;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.AdapterView.OnItemClickListener;

public class IntentUtil {

  public static List<App> getApps(Context context) {
    PackageManager pm=context.getPackageManager();
    Intent mainIntent=new Intent(Intent.ACTION_MAIN,null);
    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
    List<ResolveInfo> apps=pm.queryIntentActivities(mainIntent,0);
    Collections.sort(apps,new ResolveInfo.DisplayNameComparator(pm));
    List<App> result=new ArrayList<App>();
    for(ResolveInfo app:apps) {
      //if (!app.activityInfo.packageName.equals("com.sra.brieflet")) {
        App a=new App();
        a.title=app.loadLabel(pm);
        a.icon=app.loadIcon(pm); // app.activityInfo.loadIcon?
        a.intent=new Intent(Intent.ACTION_MAIN);
        a.intent.addCategory(Intent.CATEGORY_LAUNCHER);
        a.intent.setComponent(new ComponentName(app.activityInfo.applicationInfo.packageName,app.activityInfo.name));
        a.intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        result.add(a);
      //}
    }
    return(result);
  }
  
  public static Drawable getAppIcon(Context context,String pkgstr,String clsstr) {
    PackageManager pm=context.getPackageManager();
    try {
      return(pm.getActivityIcon(new ComponentName(pkgstr,clsstr)));
    } catch (NameNotFoundException e) {
    }
    return(null);
  }
  
  public static void chooseApp(Activity act,final AppResultListener listener) {
    final List<App> apps=getApps(act);
    //CharSequence[] items=new CharSequence[apps.size()];
    //for(int i=0;i<apps.size();i++) items[i]=apps.get(i).title;
    
    LayoutInflater inflater = (LayoutInflater)act.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View view = (View) inflater.inflate(R.layout.listview,(ViewGroup) act.findViewById(R.id.applist));
    AppAdapter appAdapter=new AppAdapter(act,apps);
    ListView lv=(ListView)view.findViewById(R.id.applist);
    lv.setAdapter(appAdapter);    
    AlertDialog.Builder builder = new AlertDialog.Builder(act);
    builder.setTitle("Pick An Application To Create The Link Action");
    builder.setView(view);
    final AlertDialog alert = builder.create();  
    
    lv.setOnItemClickListener(new OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> arg0,View arg1,int item,long arg3) {
        	listener.setResult(apps.get(item));
        	alert.dismiss();
        }
      });
      alert.show();
    /*
    builder.setItems(items, new DialogInterface.OnClickListener() {
        public void onClick(DialogInterface dialog,int item) {
          listener.setResult(apps.get(item));
        }
    });
    
    alert.show();*/
  }

  public static void test(Context context) {
    for(App app:getApps(context)) {
      MLog.v("App:"+app.title);
    }
  }

}
