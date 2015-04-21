/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.xml.sax.XMLReader;

import zamzar.ZamZar;

import android.app.ActionBar;
import android.app.ActionBar.Tab;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings.Secure;
import android.text.Editable;
import android.text.Html;
import android.text.Html.ImageGetter;
import android.text.Html.TagHandler;
import android.text.Spanned;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.RemoteViews;

//import com.android.vending.licensing.AESObfuscator;
//import com.android.vending.licensing.LicenseChecker;
//import com.android.vending.licensing.LicenseCheckerCallback;
//import com.android.vending.licensing.ServerManagedPolicy;
import com.sra.brieflet.util.ClipboardUtil;
import com.sra.brieflet.util.DialogUtil;
import com.sra.brieflet.util.ErrorCaptureEmailSender;
import com.sra.brieflet.util.MLog;
import com.sra.brieflet.util.MTracker;
import com.sra.brieflet.util.StringResultListener;

public class Brieflet extends Activity {

  // GoogleAnalyticsTracker tracker;

  // private static final String BASE64_PUBLIC_KEY="license_key_was_here"
  // Generate your own 20 random bytes, and put them here.
  // private static final byte[] SALT=new byte[] {salt_array_was_here};
  public static final String TAG="sra";
  //private LicenseCheckerCallback mLicenseCheckerCallback;
  //private LicenseChecker mChecker;
  // A handler on the UI thread.
  private Handler mHandler;

  private String tabNames[]= {"Presentations","Help"};
  private Map<Tab, TabListener> tabListenerMap=new HashMap<Tab, TabListener>();
  //private static SlideList slideList;
  private HelpViewer helpViewer;

  /** enable logging during development */
  public static final boolean ENABLE_LOGGING=true;
  public static final boolean ENABLE_TRACKING=true;
  /** show alpha features */
  public static final boolean SHOW_ALPHA=true;

  private static ProgressDialog pDialog=null;
  private ProgressDialog loadingDialog;

  //public static final int LICENSE_DIALOG=0;
  public static final int LOADING_DIALOG=1;
  
  private static boolean hasAttach=false;

  public Brieflet() {}
  
  private void setHasAttach(){
	  try{
	  Class<?>[] parameters = {Fragment.class};
	  FragmentTransaction.class.getMethod("attach", parameters);
	  MLog.v("Has the method attach()");
	  hasAttach=true;
	  }catch(NoSuchMethodException nsme){
		  hasAttach=false;
		  MLog.v("Does not have the method attach()");
	  }
  }

  @Override
  protected void onNewIntent(Intent intent) {
    
    // this will get called when Presentations is already on the top of the
    // stack so it will get re-used instead of making another
    // need to handle this case
    setIntent(intent);
    MLog.v("onNewIntent "+intent);
    IntentHandler.handleIncomingIntents(this);
    // super.onNewIntent(intent);
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater=getMenuInflater();
    inflater.inflate(R.menu.main_menu,menu);
    return(true);
  }

  @Override
  protected void onRestart() {
    MLog.v("Brieflet onRestart...");
    super.onRestart();
  }

  
  @Override
  protected void onResume() {
    super.onResume();
    SlideLoader.activeAct=this;
    MLog.v("Brieflet onResume...");
    refreshSelectedView();
    MTracker.trackPageView("/"+this.getLocalClassName());
    
    /*
     * if (tracker!=null){ //tracker.getVisitorCustomVar(1);
     * tracker.trackPageView("/"+this.getLocalClassName()); } else {
     * MLog.v("tracker was null in onResume"); //thought it was better to flag
     * this since it happens often apparently -- SWB }
     */

  }
  @Override
  protected void onPause(){
      super.onPause();
      SlideLoader.activeAct=null;
      
      
  }

  public Notification makeNotification(String name,int progress,boolean ongoing, boolean autocancel) {

    final Resources res=getResources();
    Intent notificationIntent=new Intent(this,Brieflet.class);
    notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

    Notification.Builder builder=new Notification.Builder(this).setSmallIcon(R.drawable.brieflet2).setAutoCancel(false)
        .setTicker(name).setOnlyAlertOnce(true).setOngoing(ongoing).setAutoCancel(autocancel)
        .setContentIntent(PendingIntent.getActivity(this,0,notificationIntent,0));

    RemoteViews layout=new RemoteViews(getPackageName(),R.layout.download_progress);
    layout.setTextViewText(R.id.title_text,name);
    layout.setImageViewResource(R.id.status_image,R.drawable.brieflet2);
    layout.setProgressBar(R.id.status_progress_view,100,progress,false);

    /*
     * layout.setOnClickPendingIntent(R.id.notification_button,
     * getDialogPendingIntent
     * ("Tapped the 'dialog' button in the notification."));
     */
    builder.setContent(layout);
    // Bitmap largeIcon= BitmapFactory.decodeResource(res,R.drawable.brieflet2);

    // builder.setLargeIcon(largeIcon);
    return(builder.getNotification());

  }

  public NotificationManager getNotificationManager() {
    return((NotificationManager)getApplicationContext().getSystemService(getApplicationContext().NOTIFICATION_SERVICE));
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case android.R.id.home:
        refreshView();
        MTracker.trackEvent("Options","Home","Brieflet",1);
        return true;
      case R.id.About:
        // need to display SRA logo, authors in dialog
        aboutDialog();
        MTracker.trackEvent("Options","About","Brieflet",1);
        return true;
      case R.id.Refresh:
        // should do some sort of repaint, forcing the slide list to re-read
        // itself through a getslidesets call
        MTracker.trackEvent("Options","Refresh","Brieflet",1);
        refreshSelectedView();
        return true;
      case R.id.Add:
        // should do some sort of repaint, forcing the slide list to re-read
        // itself through a getslidesets call
        MTracker.trackEvent("Options","Add","Brieflet",1);
        addNew();
        return true;
      case R.id.EmailPPTConvert:
        sendJar();
        MTracker.trackEvent("Options","Email PPTCOnvert","Brieflet",1);
        return true;
      case R.id.Pack:
        packPresentations();
        return true;
      case R.id.Feedback:
        sendFeedback();
        MTracker.trackEvent("Options","Feedback","Brieflet",1);
        return true;
      case R.id.ExportPPTConvert:
        exportJar();
        MTracker.trackEvent("Options","Export PPTConvert","Brieflet",1);
        return true;
      case R.id.Exit:
        MTracker.trackEvent("Options","Exit","Brieflet",1);
        guardAction("Would you like to exit the Brieflet application?",new Runnable() {

          public void run() {
            shutdown();
          }
        });
        return true;
      case R.id.Preferences:
        MTracker.trackEvent("Options","Launch Preferences","Brieflet",1);
        Intent intent=new Intent(this,BriefletPreferences.class);
        try {
          // startActivity(null);
          startActivity(intent);
        } catch (Exception e) {
          MLog.e("Error launching preferences",e);
        }
        return true;
    }
    return super.onOptionsItemSelected(item);
  }

  public void postRefreshView() {
    runOnUiThread(new Runnable() {

      public void run() {
        refreshView();
      }
    });
  }

  public void refreshView() {
    // SlideLoader slideLdr=SlideLoader.getDefaultInstance();
    // slideList.refresh(slideLdr);
    ActionBar bar=super.getActionBar();
    bar.selectTab(bar.getTabAt(1));
    bar.selectTab(bar.getTabAt(0));
  }

  public void refreshSelectedView() {
    ActionBar bar=getActionBar();
    Tab sel=bar.getSelectedTab();
    int other=-1;
    if (bar.getTabAt(0).equals(sel)) {
      other=1;

    } else {
      other=0;
    }
    bar.selectTab(bar.getTabAt(other));
    bar.selectTab(sel);
  }

  private void addNew() {
    final SlideLoader sl=SlideLoader.getDefaultInstance(this);
    DialogUtil.getTextDialog("Create Presentation","Enter Title",null,new StringResultListener() {

      @Override
      public void setResult(String result) {
        sl.createSlideSet(Brieflet.this,result,new Runnable() {

          public void run() {}

          {
            postRefreshView();
          }
        });
      }
    },this);
  }

  public void exportJar() {
    try {
      InputStream in=getResources().openRawResource(R.raw.pptconvert);
      FileOutputStream out=new FileOutputStream("/sdcard/Slides/pptconvert.jar");
      byte[] buf=new byte[1024];
      int len;
      while ((len=in.read(buf,0,buf.length))>-1) {
        out.write(buf,0,len);
      }
      out.close();
      in.close();
      infoDialog("The Windows PowerPoint converter was exported to /sdcard/Slides/pptconverter.jar on your device.  Copy it to your Windows PC and double-click it to run the converter.");
    } catch (Exception e) {
      MLog.e(":"+e.toString(),e);
      infoDialog("Sorry, Brieflet had a problem exporting the Windows PowerPoint Converter to the /sdcard/Slides directory on your SDCard.");
    }
  }

  public void exportSample() {
    try {
      File file=new File("/sdcard/Slides");
      if (!file.exists()) {
        file.mkdirs();
      }
      File file2=new File("/sdcard/Slides/SamplePresentation.blt");
      if (file2.exists())
        return; // dont' overwrite
      InputStream in=getResources().openRawResource(R.raw.samplepresentation);
      FileOutputStream out=new FileOutputStream("/sdcard/Slides/SamplePresentation.blt");
      byte[] buf=new byte[1024];
      int len;
      while ((len=in.read(buf,0,buf.length))>-1) {
        out.write(buf,0,len);
      }
      MTracker.trackEvent("Main Actions","Export Sample","Brieflet",1);
      out.close();
      in.close();
    } catch (Exception e) {
      MLog.e(":"+e.toString(),e);
      infoDialog("Sorry, Brieflet had a problem exporting the sample presentation to /sdcard/Slides directory on your SDCard.");
    }
  }

  public void sendFeedback() {
    Intent intent=new Intent(Intent.ACTION_SEND);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.setType("plain/text");
    String[] adds=new String[1];
    adds[0]="sramobile@gmail.com";
    intent.putExtra(Intent.EXTRA_SUBJECT,"Feedback for Android Brieflet Application");
    intent.putExtra(Intent.EXTRA_EMAIL,adds);
    startActivity(intent);
  }

  public void packPresentations() {
    final SlideLoader sl=SlideLoader.getDefaultInstance(this);
    final Brieflet fbrief=this;
    (new Thread() {
      public void run() {
        SlideLoader.isDone=false;
        DialogUtil.showBusyCursor(fbrief,"Packing Presentations...","Please wait...",null);
        for(SlideSet ss:sl.getSlideSets()) {
          sl.packPresentation(ss);
        }
        SlideLoader.isDone=true;
      }
    }).start();
  }

  public void sendJar() {
    Intent intent=new Intent(Intent.ACTION_SEND);
    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    intent.setType("application/java-archive");
    intent.putExtra(Intent.EXTRA_SUBJECT,"Brieflet's PowerPoint Converter for Windows");
    try {
      InputStream in=getResources().openRawResource(R.raw.readme);
      byte[] buf=new byte[in.available()];
      in.read(buf,0,buf.length);
      in.close();
      intent.putExtra(Intent.EXTRA_TEXT,new String(buf));
    } catch (Exception e) {
      MLog.e(":"+e.toString(),e);
    }
    try {
      InputStream in=getResources().openRawResource(R.raw.pptconvert);
      FileOutputStream out=new FileOutputStream("/sdcard/Slides/pptconvert.jar");
      byte[] buf=new byte[1024];
      int len;
      while ((len=in.read(buf,0,buf.length))>-1) {
        out.write(buf,0,len);
      }
      out.close();
      in.close();
    } catch (Exception e) {
      MLog.e(":"+e.toString(),e);
    }
    intent.putExtra(Intent.EXTRA_STREAM,Uri.parse("file:///sdcard/Slides/pptconvert.jar"));
    startActivity(intent);
  }

  public static void deleteDir(String dirstr) {
    File dir=new File(dirstr);
    if (dir.exists()) {
      for(File file:dir.listFiles()) {
        if (file.isFile()) {
          MLog.v("Deleting:"+file.getAbsolutePath());
          file.delete();
        }
      }
      dir.delete();
    }
  }

  public void shutdown() {
    cancelNotifications();
    ClipboardUtil.cleanup(this);
    deleteDir("/sdcard/Slides/downloads");
    deleteDir("/sdcard/Slides/tmp");
    android.os.Process.killProcess(android.os.Process.myPid()); // let's really
    // try to shut
    // down a little
    // harder
    // Brieflet.this.finish();
  }

  public void infoDialog(String message) {
    AlertDialog.Builder builder=new AlertDialog.Builder(this);
    builder.setMessage(message);
    builder.setNeutralButton("Ok",new DialogInterface.OnClickListener() {

      public void onClick(DialogInterface dialog,int id) {
        // ok was clicked
      }
    });
    builder.show();
  }

  public void aboutDialog() {
    // this is a tricky pseudo HTML way of doing a TextView dialog, for better
    // control a custom dialog is obviously better but harder -- SWB
    // it may be pretty easy just to use a WebView, would need a custom dialog
    // to deal with getting the SRA logo on white (which is should be against)
    AlertDialog.Builder builder=new AlertDialog.Builder(this);
    byte[] buf;
    String aboutHtml="Error generating about box, sorry ;-) ";
    try {
      InputStream in=getResources().openRawResource(R.raw.about);
      buf=new byte[in.available()];
      in.read(buf,0,buf.length);
      in.close();
      aboutHtml=new String(buf);
    } catch (Exception e) {
      MLog.e(":"+e.toString(),e);
    }
    ImageGetter ig=new ImageGetter() {

      @Override
      public Drawable getDrawable(String source) {
        MLog.v("getDrawable src="+source);
	/*
        if (source.equals("logo.png")) {
          Drawable d=getResources().getDrawable(R.raw.logo);
          d.setBounds(0,0,d.getIntrinsicWidth(),d.getIntrinsicHeight());
          return(d);
        }
	*/
        return null;
      }
    };
    TagHandler th=new TagHandler() {

      @Override
      public void handleTag(boolean opening,String tag,Editable output,XMLReader xmlReader) {
        MLog.v(":handleTag opening="+opening+" tag="+tag);
      }
    };
    Spanned html=Html.fromHtml(aboutHtml,ig,th);
    builder.setMessage(html);
    builder.setNeutralButton("Ok",new DialogInterface.OnClickListener() {

      public void onClick(DialogInterface dialog,int id) {
        // ok was clicked
      }
    });
    builder.show();
  }

  public void guardAction(String message,final Runnable runnable) {
    DialogUtil.guardAction(message,runnable,this);
  }

  private String pad4(int i) {
    String str="0000"+i;
    return(str.substring(str.length()-4));
  }
  
  public File findIncomingPresFile() {
    return(findIncomingPresFile("Incoming"));
  }
  
  public static boolean exists(File dir,String namePrefix) {
    File[] files=dir.listFiles();
    for(File file:files) {
      if (file.getName().startsWith(namePrefix)) return(true);
    }
    return(false);
  }

  public File findIncomingPresFile(String stem) {
    File dir=new File("/sdcard/Slides");
    String prefix="/sdcard/Slides/"+stem;
    int c=0;
    while (true) {
      c++;
      if (!exists(dir,stem+pad4(c)+".")) {
        return(new File(prefix+pad4(c)+".blt"));
      }
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    try {
      super.onCreate(savedInstanceState);
      MTracker.initTracker(this);
      ErrorCapture.init(this);
      ErrorCaptureEmailSender.sendStackTraces(getString(R.string.ErrorReport_EmailAddress),this);
      //Eula.show(this);
      setHasAttach();

      final SharedPreferences preferences=getSharedPreferences("sample.copied",Activity.MODE_PRIVATE);
      // the section below copies out the sample if it hasn't been done
      if (!preferences.getBoolean("sample.copied2",false)) {
        exportSample();
        preferences.edit().putBoolean("sample.copied2",true).commit();
      }
      MLog.v("Brieflet onCreate called");
      // requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
      // FragmentManager fragMgr=this.getFragmentManager();
      MLog.v("Handling incoming intents in onCreate");
      IntentHandler.handleIncomingIntents(this);
      setContentView(R.layout.main);
      //runLicenseService();
      continueLoading();

      /*
       * tracker=GoogleAnalyticsTracker.getInstance(); // Start the tracker in
       * manual dispatch mode... tracker.start("UA-23457289-1",30,this);
       */// dispatch tracker every 30 seconds
      // tracker.setDebug(true); // put some debug messages in the log
      // tracker.setDryRun(true); // won't send the data to google analytics
      // tracker.setCustomVar(arg0, arg1, arg2, arg3);
      /** Switch to the fragment you were in **/
      if (savedInstanceState!=null) {
        int position=savedInstanceState.getInt("category");
        // String url= savedInstanceState.getString("url");
        if (position>0) {

          ActionBar bar=getActionBar();
          if (position==1) {
            TabListener tl=tabListenerMap.get(bar.getTabAt(position));
            helpViewer=(HelpViewer)tl.getFragment();
            helpViewer.loadBundle(savedInstanceState);
          }

          bar.setSelectedNavigationItem(position);
        }
      }
    } catch (Exception e) {
      MLog.e("********Brieflet="+e.toString(),e);
    }
  }

  private void continueLoading() {

    ActionBar bar=getActionBar();
    // FragmentManager fragMgr=getFragmentManager();
    tabListenerMap.clear();
    for(int i=0;i<tabNames.length;i++) {
      TabListener tl=null;
      if (tabNames[i].equals("Presentations")) {
        //slideList=new SlideList(this);// SlideList.getDefaultInstance(this);
        //tl=new TabListener(slideList,tabNames[i]);
        tl=new TabListener(this,tabNames[i],SlideList.class);
      } else if (tabNames[i].equals("Help")) {
        //helpViewer=new HelpViewer();
       // tl=new TabListener(helpViewer,tabNames[i]);
        tl=new TabListener(this,tabNames[i],HelpViewer.class);
      } else {
        // tl=new TabListener(new
        // PlaceholderFragment(tabNames[i]),tabNames[i],fragMgr);
      }
      Tab tab=bar.newTab().setText(tabNames[i]).setTabListener(tl);
      tabListenerMap.put(tab,tl);
      bar.addTab(tab);
    }
    bar.setDisplayOptions(ActionBar.DISPLAY_SHOW_TITLE|ActionBar.DISPLAY_USE_LOGO);
    bar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
    bar.setDisplayShowHomeEnabled(true);
    // StateDetector sd=new StateDetector();
    // sd.register(this);
  }

  // this method is needed for running licensing service
  /*
  private void runLicenseService() {
    MLog.v("Starting License check");
    mHandler=new Handler();
    // Try to use more data here. ANDROID_ID is a single point of attack.
    String deviceId=Secure.getString(getContentResolver(),Secure.ANDROID_ID);
    // Construct the LicenseCheckerCallback. The library calls this when done.
    mLicenseCheckerCallback=new MyLicenseCheckerCallback();
    // Construct the LicenseChecker with a Policy.
    mChecker=new LicenseChecker(this,new ServerManagedPolicy(this,new AESObfuscator(SALT,getPackageName(),deviceId)),
        BASE64_PUBLIC_KEY // Your public licensing key.
    );
    // Call a wrapper method that initiates the license check
    doCheck();
  }

  private class MyLicenseCheckerCallback implements LicenseCheckerCallback {

    public void allow() {
      if (isFinishing()) {
        // Don't update UI if Activity is finishing.
        return;
      }
      // continueLoading();
      // Should allow user access.
      displayResult(getString(R.string.allow));
    }

    public void dontAllow() {
      if (isFinishing()) {
        // Don't update UI if Activity is finishing.
        return;
      }

      displayResult(getString(R.string.dont_allow));
      // Should not allow access. In most cases, the app should assume
      // the user has access unless it encounters this. If it does,
      // the app should inform the user of their unlicensed ways
      // and then either shut down the app or limit the user to a
      // restricted set of features.
      // In this example, we show a dialog that takes the user to Market.
      MTracker.trackEvent("Main Actions","Deny Access","Brieflet",1);
      showDialog(LICENSE_DIALOG);
    }

    public void applicationError(ApplicationErrorCode errorCode) {
      if (isFinishing()) {
        // Don't update UI if Activity is finishing.
        return;
      }
      // This is a polite way of saying the developer made a mistake
      // while setting up or calling the license checker library.
      // Please examine the error code and fix the error.
      String result=String.format(getString(R.string.application_error),errorCode);
      displayResult(result);
    }
  }
  
  private void displayResult(final String result) {
    try {
      MLog.v(" License check step 3");
      mHandler.post(new Runnable() {

        public void run() {
          try {
            MLog.v(" License check step 4 "+result);
            setProgressBarIndeterminateVisibility(false);
            // if(result.equalsIgnoreCase("Allow the user access"))
            // continueLoading();
          } catch (Exception e) {
            MLog.e(" License check step **************exception4 "+e.toString(),e);
          }
        }
      });
    } catch (Exception e) {
      MLog.e("License check step *************exception3"+e.toString(),e);
    }
  }

  private void doCheck() {
    MLog.v(" License check step 2");
    setProgressBarIndeterminateVisibility(true);
    mChecker.checkAccess(mLicenseCheckerCallback);

  }

  protected Dialog onCreateDialog(int id) {
    switch (id) {
      case LICENSE_DIALOG:
        return new AlertDialog.Builder(this).setTitle(R.string.unlicensed_dialog_title).setMessage(R.string.unlicensed_dialog_body)
            .setCancelable(false).setPositiveButton(R.string.buy_button,new DialogInterface.OnClickListener() {

              public void onClick(DialogInterface dialog,int which) {
                Intent marketIntent=new Intent(Intent.ACTION_VIEW,Uri.parse("http://market.android.com/details?id="
                    +getPackageName()));
                startActivity(marketIntent);
                shutdown();
              }
            }).setNegativeButton(R.string.quit_button,new DialogInterface.OnClickListener() {

              public void onClick(DialogInterface dialog,int which) {
                finish();
                shutdown();
              }
            }).create();
      case LOADING_DIALOG:
        loadingDialog=new ProgressDialog(Brieflet.this);
        loadingDialog.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
        loadingDialog.setMessage("Downloading Slide Images...");
        return(loadingDialog);
      default:
        return(null);
    }
  }
  */

  private Map<Integer, String> chanTitle=new HashMap<Integer, String>(); // probably
                                                                         // should
                                                                         // have
                                                                         // struct
  private Map<Integer, Notification> chanNotification=new HashMap<Integer, Notification>();

  public synchronized void startLoading(int chan,String name) {
    NotificationManager notificationManager=getNotificationManager();

    // showDialog(LOADING_DIALOG);
    if(ZamZar.isChannelActive(chan)){
        chanTitle.put(chan,name);
        Notification downloadNotification=makeNotification(name,0,true,false);
        chanNotification.put(chan,downloadNotification);
        notificationManager.notify(100+chan,downloadNotification);
    }

  }
  public synchronized void cancelNotification(int chan){
      NotificationManager notificationManager=getNotificationManager();
      String removed=chanTitle.remove(chan);
      chanNotification.remove(chan);
      if(removed!=null)
          notificationManager.cancel(100+chan);
  }

  public synchronized void setNotificationName(int chan,String name) {
    chanTitle.put(chan,name);
    Notification downloadNotification=chanNotification.get(chan);
    downloadNotification.contentView.setTextViewText(R.id.title_text,name);
  }

  public synchronized void updateLoading(int chan,int p,String bot) {
    NotificationManager notificationManager=getNotificationManager();
    if (p>100) {
      Notification n=makeNotification(bot,100,false, true);
      notificationManager.cancel(100+chan);
      notificationManager.notify(200+chan,n);
      chanTitle.remove(chan);
      chanNotification.remove(chan);
    } else if (chanNotification.get(chan)!=null) {
      Notification downloadNotification=chanNotification.get(chan);
      downloadNotification.contentView.setTextViewText(R.id.status_text,p+"% "+bot);
      downloadNotification.contentView.setProgressBar(R.id.status_progress_view,100,p,false);
      notificationManager.notify(100+chan,downloadNotification);

      /*
       * Message msg=loadingHandler.obtainMessage(); msg.arg1=num;
       * loadingHandler.sendMessage(msg);
       */
    }
  }
  private void cancelNotifications(){
      getNotificationManager().cancelAll();
      /*
      NotificationManager nm= getNotificationManager();
      for(Integer chan : chanTitle.keySet()){
          int id= chan+100;
          nm.cancel(id);
      }*/
  }

  @Override
  protected void onPrepareDialog(int id,Dialog dialog) {
    switch (id) {
      case LOADING_DIALOG:
        loadingDialog.setProgress(0);
    }
  }

  final Handler loadingHandler=new Handler() {

    public void handleMessage(Message msg) {
      int total=msg.arg1;
      loadingDialog.setProgress(total);
      if (total>=100) {
        dismissDialog(LOADING_DIALOG);
      }
    }
  };

  @Override
  protected void onDestroy() {
    super.onDestroy();
    MLog.v("Brieflet onDestroy");
    //getNotificationManager().cancelAll();
    
    // Stop the tracker when it is no longer needed.
    MTracker.stop();

    /*
     * if (tracker!=null) tracker.stop();
     */
    /*
     * Needs more testing first if (SlideLoader.getDefaultInstance()!=null) {
     * SlideLoader.getDefaultInstance().shutdown(); }
     */
    //mChecker.onDestroy();
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    // Save UI state changes to the savedInstanceState.
    // This bundle will be passed to onCreate if the process is
    // killed and restarted.
    // super.onSaveInstanceState(savedInstanceState);
    ActionBar bar=getActionBar();
    int category=bar.getSelectedTab().getPosition();
    savedInstanceState.putInt("category",category);
    /** if web view, save the current page **/
    if (category==1) {
      TabListener tl=tabListenerMap.get(bar.getSelectedTab());
      helpViewer=(HelpViewer)tl.getFragment();
      helpViewer.saveState(savedInstanceState);
    }
    super.onSaveInstanceState(savedInstanceState);
    MLog.v("Saving Instances State");
  }
  public static class TabListener<T extends Fragment> implements ActionBar.TabListener {
      private final Activity mActivity;
      private final String mTag;
      private final Class<T> mClass;
      private final Bundle mArgs;
      private Fragment mFragment;
      private boolean added= false;

      public TabListener(Activity activity, String tag, Class<T> clz) {
          this(activity, tag, clz, null);
      }

      public TabListener(Activity activity, String tag, Class<T> clz, Bundle args) {
          mActivity = activity;
          mTag = tag;
          mClass = clz;
          mArgs = args;
          
          mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);

          // Check to see if we already have a fragment for this tab, probably
          // from a previously saved state.  If so, deactivate it, because our
          // initial state is that a tab isn't shown.
          if(hasAttach){
        	  Fragment f = mActivity.getFragmentManager().findFragmentByTag(mTag);
        	  if (f != null && !f.isDetached()) {
        		  FragmentTransaction ft = mActivity.getFragmentManager().beginTransaction();
        		  ft.detach(f);
        		  ft.commit();
        	  }
          }
      }
      public Fragment getFragment(){
    	  return mFragment;
      }

      public void onTabSelected(Tab tab, FragmentTransaction ft) {
    	  if(hasAttach){
    		  if (!added || mFragment==null) {
    			  if(mFragment==null)
    				  mFragment = Fragment.instantiate(mActivity, mClass.getName(), mArgs);
    			  
    			  ft.add(android.R.id.content, mFragment, mTag);
    			  added=true;
    		  } else {
    			  ft.attach(mFragment);
    		  }
    	  }else{
    		  try {
    			  	if(mFragment==null){
    			  		//MLog.v("Instantiating mFragment..." + mTag);
    			  		mFragment=Fragment.instantiate(mActivity, mClass.getName(), mArgs);
    			  	}
    			  	FragmentManager fm= mActivity.getFragmentManager();
    			  	Fragment f=fm.findFragmentById(R.id.topframe);
    			  	
    			  	if(f!=null){
    			  		ft.replace(R.id.topframe,mFragment,mTag);
    			  	}else{
    			  		MLog.v("onTabselected adding..."+ft.isAddToBackStackAllowed()+" mtag="+mTag);
    			  		ft.add(R.id.topframe,mFragment,mTag);
    			  	}
    		      } catch (Exception e) {
    		    	MLog.e("onTabselected exception ="+e.toString(),e);
    		    	ft.replace(R.id.topframe,mFragment,mTag);
    		      }
    		  
    	  }
    	  MTracker.trackPageView(mTag);
      }

      public void onTabUnselected(Tab tab, FragmentTransaction ft) {
    	  
    	  if(hasAttach){
    		  if (mFragment != null) {
    			  ft.detach(mFragment);
    		  }
    	  }else{
    	      ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
    	      MLog.v("Removing mFragment " + mTag);
    	      try{
    	    	  if(mFragment!=null)
    	    		  ft.remove(mFragment);
    	      }catch(Exception e){
    	    	  MLog.e("Caught exception when trying to remove fragment " +mTag, e);
    	      }
    	  }
      }

      public void onTabReselected(Tab tab, FragmentTransaction ft) {
         // Toast.makeText(mActivity, "Reselected!", Toast.LENGTH_SHORT).show();
      }
  }

  
/*
  public class TabListener implements ActionBar.TabListener {

    private Fragment mFragment;
    private String mtag=null;

    public TabListener(Fragment fragment,String tag) {
      mFragment=fragment;
      mtag=tag;
    }

    public Fragment getFragment() {
      return mFragment;
    }

    @Override
    public void onTabSelected(Tab tab,FragmentTransaction ft) {
      try {
        FragmentManager fragMgr=getFragmentManager();
        Fragment f=fragMgr.findFragmentById(R.id.topframe);
        ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);

        if (f!=null) {
          MLog.v("onTabselected replacing..."+ft.isAddToBackStackAllowed()+" mtag="+mtag);
          ft.replace(R.id.topframe,mFragment,mtag);
        } else {
          MLog.v("onTabselected adding..."+ft.isAddToBackStackAllowed()+" mtag="+mtag);
          ft.add(R.id.topframe,mFragment,mtag);
        }
        // ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
        // MLog.v("########## before adding..."+ft.isAddToBackStackAllowed()+" mtag="+mtag);
        // ft.add(R.id.topframe,mFragment,mtag);

      } catch (Exception e) {
        ft.replace(R.id.topframe,mFragment,mtag);
        MLog.e("onTabselected exception ="+e.toString(),e);
      }
      MTracker.trackPageView(mtag);
    }

    @Override
    public void onTabUnselected(Tab tab,FragmentTransaction ft) {
      FragmentManager fragMgr=getFragmentManager();
      Fragment f=fragMgr.findFragmentById(R.id.topframe);
      ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
      ft.remove(mFragment);
      fragMgr.popBackStack();

    }

    @Override
    public void onTabReselected(Tab tab,FragmentTransaction ft) {}

  }*/

  @Override
  public boolean onKeyDown(int keyCode,KeyEvent event) {
    if (getActionBar().getSelectedNavigationIndex()==1) {
      if (helpViewer!=null && helpViewer.onKeyDown(keyCode,event))
        return true;
    }
    return super.onKeyDown(keyCode,event);

  }

}
