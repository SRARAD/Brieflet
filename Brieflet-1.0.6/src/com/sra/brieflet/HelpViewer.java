/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet;

import java.io.IOException;
import java.io.InputStream;

import android.app.Fragment;
import android.content.Context;
import android.content.res.Resources.NotFoundException;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.sra.brieflet.util.MLog;
import com.sra.brieflet.util.MTracker;

public class HelpViewer extends Fragment {

  class MyWebView extends WebView {

    public MyWebView(Context context) {

      super(context);
      this.setLayerType(View.LAYER_TYPE_SOFTWARE,null);
    }

    @Override
    public boolean onKeyDown(int keyCode,KeyEvent event) {
      if ((keyCode==KeyEvent.KEYCODE_BACK)&&canGoBack()) {
        // MLog.v("webview now going back");
        goBack();
        return true;
      }
      // MLog.v("KEYCODE_BACK? " + (keyCode == KeyEvent.KEYCODE_BACK));
      // MLog.v("can go back? " + canGoBack());
      return super.onKeyDown(keyCode,event);
    }
  }

  private WebView webview;
  private Bundle bundle=new Bundle();
  private int fontSize=0;

  public HelpViewer() {
  // MLog.v("cons called for HelpViewer " );
  }

  private boolean loadPage(String name) {
    MLog.v("loadPage "+name);
    if (!name.startsWith("blt://"))
      return(false);
    name=name.substring(6);
    int pos=name.lastIndexOf(".");
    if (pos>-1)
      name=name.substring(0,pos);
    try {
      int id=getResources().getIdentifier(name,"raw","com.sra.brieflet");
      InputStream in=getResources().openRawResource(id);
      byte[] buffer=new byte[in.available()];
      in.read(buffer);
      in.close();
      MTracker.trackPageView(name);
      webview.loadData(new String(buffer),"text/html","utf-8");
      return true;
    } catch (Exception e) {
      return(false);
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState) {

    MLog.v("onCreateView called for HelpViewer ");

    webview=new MyWebView(getActivity());
    fontSize=webview.getSettings().getDefaultFontSize();
    webview.setWebViewClient(new WebViewClient() {

      @Override
      public boolean shouldOverrideUrlLoading(WebView view,String url) {
        MLog.v("url="+url);
        boolean handled=loadPage(url);
        if (handled) {
          return(true);
        } else {
          return super.shouldOverrideUrlLoading(view,url);
        }
      }

      @Override
      public WebResourceResponse shouldInterceptRequest(WebView view,String url) {
        MLog.v("shouldIntercept "+url);
        String name=url;
        if (name.startsWith("blt://")) {
          name=name.substring(6);
          if (name.endsWith(".png")) {
            try {
              int pos=name.lastIndexOf(".");
              if (pos>-1)
                name=name.substring(0,pos);
              int id=getResources().getIdentifier(name,"drawable","com.sra.brieflet");
              if (id!=0) {
                InputStream in=getResources().openRawResource(id);
                return(new WebResourceResponse("image/png","utf-8",in));
              } else {
                id=getResources().getIdentifier(name,"raw","com.sra.brieflet");
                if (id!=0) {
                  InputStream in=getResources().openRawResource(id);
                  return(new WebResourceResponse("image/png","utf-8",in));
                }
              }
            } catch (NotFoundException e) {
              MLog.e("Error fetching png web resource",e);
            }
          }
        }
        return super.shouldInterceptRequest(view,url);
      }

    });
    webview.getSettings().setBuiltInZoomControls(true);
    if (bundle==null||webview.restoreState(bundle)==null) {
      // MLog.v("webview restore is null!");
      loadPage("blt://help");
    }
    increaseFontSize(2);
    /*
     * if(!webview.canGoBack()){ MLog.v("webview can't go back"); if(!webview.canGoForward())
     * MLog.v("webview can't go forward either"); else MLog.v("webview can go forward"); }else MLog.v("webview can go back");
     */
    return(webview);
  }

  public void saveState(Bundle b) {
    if (webview.saveState(b)==null) {
      // MLog.v("Got null when saving state");
    }
  }

  public void loadBundle(Bundle b) {
    bundle=b;

  }

  @Override
  public void onPause() {
    super.onPause();
    if (webview!=null) {
      // ViewGroup parent=(ViewGroup)webview.getParent();
      saveState(bundle);
      MLog.v("Saved web view state in on pause.");
    }
  }

  public boolean onKeyDown(int keyCode,KeyEvent event) {
    if (webview==null)
      return false;
    else
      return webview.onKeyDown(keyCode,event);
  }

  public void increaseFontSize(int x) {
    fontSize+=x;
    if (fontSize>24)
      fontSize=24;
    changeFontSize(fontSize);
  }

  public void decreaseFontSize(int x) {
    fontSize-=x;
    if (fontSize<1)
      fontSize=1;
    changeFontSize(fontSize);

  }

  private void changeFontSize(int val) {
    webview.getSettings().setDefaultFontSize(val);
  }
}
