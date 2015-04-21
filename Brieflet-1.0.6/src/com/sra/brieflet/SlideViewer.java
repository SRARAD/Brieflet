/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet;

import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Paint.Style;
import android.graphics.drawable.AnimationDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.view.animation.TranslateAnimation;
import android.view.animation.Animation.AnimationListener;
import android.widget.EditText;
import android.widget.ImageSwitcher;
import android.widget.ImageView;
import android.widget.Toast;
import android.widget.ViewSwitcher;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Gallery.LayoutParams;
import android.widget.ImageView.ScaleType;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.WriterException;
import com.sra.brieflet.actions.LaunchAction;
import com.sra.brieflet.actions.MediaAction;
import com.sra.brieflet.actions.SlideAction;
import com.sra.brieflet.actions.SlideAudioAction;
import com.sra.brieflet.actions.SlideIntentAction;
import com.sra.brieflet.actions.SlideVideoAction;
import com.sra.brieflet.animations.Rotate3dAnimation;
import com.sra.brieflet.util.App;
import com.sra.brieflet.util.AppResultListener;
import com.sra.brieflet.util.BriefletMediaPlayer;
import com.sra.brieflet.util.ClipboardUtil;
import com.sra.brieflet.util.ColorPickerDialog;
import com.sra.brieflet.util.DialogUtil;
import com.sra.brieflet.util.IntentUtil;
import com.sra.brieflet.util.MLog;
import com.sra.brieflet.util.MTracker;
import com.sra.brieflet.util.MusicUtil;
import com.sra.brieflet.util.QRCodeEncoder;
import com.sra.brieflet.util.StringResultListener;

public class SlideViewer extends Activity implements ViewSwitcher.ViewFactory,ColorPickerDialog.OnColorChangedListener,
    AnimationListener {

  class SpriteView extends ImageView {

    private boolean first=true;
    private AnimationDrawable ad;

    public SpriteView(Context context) {
      super(context);
      hide();
    }

    public void walkOut(boolean prev,final Runnable runnable) {
      Animation a;
      if (prev) {
        a=new TranslateAnimation(-200f,-100f,550f,550f);
      } else {
        a=new TranslateAnimation(1200f,1050f,550f,550f);
      }
      a.setDuration(2000);
      a.setAnimationListener(new AnimationListener() {

        @Override
        public void onAnimationEnd(Animation animation) {
          runnable.run();
        }

        @Override
        public void onAnimationRepeat(Animation animation) {}

        @Override
        public void onAnimationStart(Animation animation) {}
      });
      this.startAnimation(a);
      show();
    }

    public void fastMove(boolean prev) {
      Animation a;
      if (prev) {
        a=new TranslateAnimation(-100f,1200f,550f,550f);
      } else {
        a=new TranslateAnimation(1050f,-250f,550f,550f);
      }
      a.setDuration(300);
      this.startAnimation(a);
      hide();
    }

    public void fastThrow(boolean prev) {
      AnimationSet a=new AnimationSet(true);
      if (prev) {
        a.addAnimation(new RotateAnimation(0f,-90f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f));
        a.addAnimation(new TranslateAnimation(1050f,-250f,550f,150f));
      } else {
        a.addAnimation(new RotateAnimation(0f,90f,Animation.RELATIVE_TO_SELF,0.5f,Animation.RELATIVE_TO_SELF,0.5f));
        a.addAnimation(new TranslateAnimation(-100f,1200f,550f,150f));
      }
      a.setStartOffset((int)(0.25*flipSpeed));
      a.setDuration(flipSpeed);
      this.startAnimation(a);
      hide();
    }

    public void hide() {
      this.setVisibility(INVISIBLE);
      if (ad!=null) {
        // ad.stop();
      }
    }

    public void show() {
      this.setVisibility(VISIBLE);
      if (ad!=null) {
        // ad.start();
      }
    }

    @Override
    protected void onDraw(Canvas canvas) {
      if (first) {
        setBackgroundResource(R.drawable.droid);
        ad=(AnimationDrawable)getBackground();
        ad.start();
        first=false;
      }
    }

    public void shutdown() {
      if (ad!=null) {
        ad.stop();
        ad.setCallback(null);
      }
    }

  }

  class CanvasView extends View {

    private List<Rect> aRects=new ArrayList<Rect>();
    private List<Rect> rects=new ArrayList<Rect>();
    private ArrayList<SlideAction> acts=new ArrayList<SlideAction>();
    private float mX,mY;
    private static final float TOUCH_TOLERANCE=4;
    private Path mPath;
    private Bitmap mBitmap;
    private Canvas mCanvas;
    private Paint mBitmapPaint;
    private Context context;
    private int lastWidth=0;
    private int lastHeight=0;
    private boolean blanking=false;

    public void clearAll() {
      aRects.clear();
      clear();
    }

    public void shutdown() {
      context=null;
    }

    public void clear() {
      rects.clear();
      acts.clear();
      mPath.reset();
      if (mCanvas!=null)
        mCanvas.drawColor(0x00000000,PorterDuff.Mode.DST_IN);
    }

    public void addRect(Rect rect,SlideAction action) {
      rects.add(rect);
      acts.add(action);
    }

    public void addARect(Rect rect) {
      aRects.add(rect);
    }

    public void delete(Rect rect) {
      aRects.remove(rect);
    }

    public CanvasView(Context context) {
      super(context);
      this.context=context;
      mPath=new Path();
      mBitmapPaint=new Paint(Paint.DITHER_FLAG);
    }

    private boolean makeBackingBitmap() {
      mBitmap=Bitmap.createBitmap(lastWidth,lastHeight,Bitmap.Config.ARGB_8888);
      mCanvas=new Canvas(mBitmap);
      return(true);
    }

    @Override
    protected void onSizeChanged(int w,int h,int oldw,int oldh) {
      super.onSizeChanged(w,h,oldw,oldh);
      lastWidth=w;
      lastHeight=h;
      heightScale=lastHeight;
      // MLog.v("cv sees height="+lastHeight);
      // mBitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
      // mCanvas=new Canvas(mBitmap);
    }

    private void drawControl(Canvas canvas,int x,int y,Paint p) {
      int rad=3;
      canvas.drawOval(new RectF((float)(x-rad),(float)(y-rad),(float)(x+rad),(float)(y+rad)),p);
    }

    public void toggleBlanking() {
      blanking=!blanking;
      invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
      if (blanking) {
        canvas.drawColor(Color.BLACK);
      } else {
        Paint p=new Paint();
        /* just to preview indexed text
        p.setColor(Color.YELLOW);
        p.setStrokeWidth(2);
        p.setStyle(Style.STROKE);
        for(IndexEntry ie:ss.getIndex()) {
          int pos=curSlideName.indexOf(".");
          if (curSlideName.substring(0,pos).equals(ie.slideName)) {
            canvas.drawRect(ie.rect,p);
          }
        }
        */
        p.setColor(Color.BLUE);
        p.setStrokeWidth(2);
        p.setStyle(Style.STROKE);
        for(int i=0;i<aRects.size();i++) {
          Rect rect=aRects.get(i);
          rect=scaleRect(rect);
          canvas.drawRect(rect,p);
        }
        for(int i=0;i<rects.size();i++) {
          Rect rect=rects.get(i);
          SlideAction action=acts.get(i);
          if (action.supportsThumbnail()&&action.isThumbVisible()) {
            Rect r1=SlideLoader.fixRect(action.getBounds());
            r1=scaleRect(r1);
            SlideLoader.getDefaultInstance(context).drawActionThumbnail(action,canvas,r1,true);
            // SlideLoader.getDefaultInstance(context).drawActionThumbnail(action,canvas);
          }
          p.setColor(Color.RED);
          p.setStyle(Style.STROKE);
          rect=scaleRect(rect);
          canvas.drawRect(rect,p);
          // draw handle-like stuff
          p.setStyle(Style.FILL);
          p.setColor(Color.GREEN);
          drawControl(canvas,rect.centerX(),rect.centerY(),p);
          drawControl(canvas,rect.left,rect.top,p);
          drawControl(canvas,rect.left,rect.bottom,p);
          drawControl(canvas,rect.right,rect.top,p);
          drawControl(canvas,rect.right,rect.bottom,p);
          drawControl(canvas,rect.left+rect.width()/2,rect.top,p);
          drawControl(canvas,rect.left,rect.top+rect.height()/2,p);
          drawControl(canvas,rect.right,rect.top+rect.height()/2,p);
          drawControl(canvas,rect.left+rect.width()/2,rect.bottom,p);
        }
        canvas.drawColor(0x00000000);
        if (mBitmap!=null) {
          canvas.drawBitmap(mBitmap,0,0,mBitmapPaint);
        }
        canvas.drawPath(mPath,mPaint);
      }
    }

    private void touch_start(float x,float y) {
      mPath.reset();
      mPath.moveTo(x,y);
      mX=x;
      mY=y;
    }

    private void touch_move(float x,float y) {
      float dx=Math.abs(x-mX);
      float dy=Math.abs(y-mY);
      if (dx>=TOUCH_TOLERANCE||dy>=TOUCH_TOLERANCE) {
        mPath.quadTo(mX,mY,(x+mX)/2,(y+mY)/2);
        mX=x;
        mY=y;
      }
    }

    private void touch_up() {
      mPath.lineTo(mX,mY);
      // commit the path to our offscreen
      if (mCanvas==null) {
        makeBackingBitmap();
      } else {
        // this really shouldn't happen since the component would go away if a
        // resize happened, right?
        if (lastWidth!=mBitmap.getWidth()||lastHeight!=mBitmap.getHeight()) {
          MLog.v("Reallocating CV Bitmap");
          makeBackingBitmap(); // reallocate to new size
        }
      }
      mCanvas.drawPath(mPath,mPaint);
      // kill this so we don't double draw
      mPath.reset();
    }

    public boolean onMyTouchEvent(MotionEvent event) {
      float x=event.getX();
      float y=event.getY();
      if (!createMode&&!editActMode) {
        switch (event.getAction()) {
          case MotionEvent.ACTION_DOWN:
            touch_start(x,y);
            invalidate();
            break;
          case MotionEvent.ACTION_MOVE:
            touch_move(x,y);
            invalidate();
            break;
          case MotionEvent.ACTION_UP:
            touch_up();
            invalidate();
            break;
        }
        return true;
      } else if (event.getAction()==MotionEvent.ACTION_UP) {
        return(false);
      } else {
        return false;
      }
    }
  }

  class SlideGestureListener extends GestureDetector.SimpleOnGestureListener {

    private SlideViewer sv;

    public SlideGestureListener(SlideViewer sv) {
      this.sv=sv;
    }

    public void shutdown() {
      sv=null;
    }

    @Override
    public boolean onDown(MotionEvent e) {
      MLog.v("onDown");
      if (createMode||editActMode) {
        prevRect=actRect;
        onMove=false;
        onResize=false;
        onRight=false;
        onLeft=false;
        onTop=false;
        onBottom=false;
        mSwitcher.postDelayed(new Runnable() {

          public void run() {
            refreshSwitcher(actAction); // this throws off the timing, but
                                        // important so we don't get double
                                        // image
          }
        },100); // the 100ms delay seems to be enough to not throw off timing
                // now
        return true;
      } else {
        return false;
      }
    }

    @Override
    public boolean onScroll(MotionEvent e1,MotionEvent e2,float distX,float distY) {
      MLog.v("onScroll");
      if (createMode||editActMode) {
        isCancelPermitted=false; // now you can't double tab to cancel edit or
        // create action operation
        try {
          // createMode=true;
          int bt=actRect.bottom;
          int lf=actRect.left;
          int rt=actRect.right;
          int tp=actRect.top;
          int diffX=0;
          int diffY=0;
          diffX=Math.abs((prevRect.right-prevRect.left)/4);
          diffY=Math.abs((prevRect.bottom-prevRect.top)/4);
          // moving operation
          if (!onResize
              &&(onMove||((int)e1.getX()<=(prevRect.centerX()+diffX)&&(int)e1.getX()>=(prevRect.centerX()-diffX)
                  &&(int)e1.getY()<=(prevRect.centerY()+diffY)&&(int)e1.getY()>=(prevRect.centerY()-diffY)))) {
            onMove=true;
            int w=(prevRect.right-prevRect.left)/2;
            int h=(prevRect.bottom-prevRect.top)/2;
            rt=(int)(e2.getX())+w;
            lf=(int)(e2.getX())-w;
            tp=(int)(e2.getY())-h;
            bt=(int)(e2.getY())+h;
          } else { // resizing operation
            onResize=true;
            onMove=false;
            int prevRt=rt;
            int prevLf=lf;
            int prevTp=tp;
            int prevBt=bt;

            if (onRight||((int)e1.getX()<=(rt+60)&&(int)e1.getX()>=(rt-diffX))) {
              rt=(int)(e1.getX()+(e2.getX()-e1.getX()));
              onRight=true;
              if (Math.abs(lf-rt)<=MIN_HW) {
                rt=prevRt;
              }
            }
            if (onLeft||((int)e1.getX()<=(lf+diffX)&&(int)e1.getX()>=(lf-60))) {
              lf=(int)(e1.getX()+(e2.getX()-e1.getX()));
              onLeft=true;
              if (Math.abs(lf-rt)<=MIN_HW) {
                lf=prevLf;
              }
            }
            if (onBottom||((int)e1.getY()<=(bt+60)&&(int)e1.getY()>=(bt-diffY))) {
              bt=(int)(e1.getY()+(e2.getY()-e1.getY()));
              onBottom=true;
              if (Math.abs(bt-tp)<=MIN_HW) {
                bt=prevBt;
              }
            }

            if (onTop||((int)e1.getY()<=(tp+diffY)&&(int)e1.getY()>=(tp-60))) {
              tp=(int)(e1.getY()+(e2.getY()-e1.getY()));
              onTop=true;
              if (Math.abs(bt-tp)<=MIN_HW) {
                tp=prevTp;
              }
            }
          }
          actRect.set(lf,tp,rt,bt);
          cv.invalidate();
        } catch (Exception e) {
          MLog.e("onscrol e="+e.toString(),e);
        }
        return true;
      } else {
        return false;
      }
      // return super.onScroll(e1,e2,distX,distY);
    }

    @Override
    public boolean onDoubleTap(MotionEvent e) {
      MLog.v("double tap");
      if (createMode) {
        createMode=false;
        if (actAction!=null)
          fixRect(actAction.getBounds());
        // checkRectCoordsOrder();
        MLog.v("on double tap create mode");
        ss.updatePropsAndSave(mSwitcher,null);
        // ss.addActionAndSave(mSwitcher,curSlideName,actAction,null);
        Toast.makeText(getApplicationContext(),"Your action has been created.",Toast.LENGTH_SHORT).show();
        MTracker.trackEvent("DoubleTap", "Create", actAction.getName(), 1);
        if (showActions) {
          cv.addARect(actAction.getBounds());
          cv.invalidate();
        }
      } else if (editActMode&&!isCancelPermitted) {
        editActMode=false;
        isCancelPermitted=true;
        actionMode=0;
        ss.updatePropsAndSave(mSwitcher,null);
        if (actAction!=null)
          fixRect(actAction.getBounds());
        // checkRectCoordsOrder();
        Toast.makeText(getApplicationContext(),"Your action has been updated.",Toast.LENGTH_SHORT).show();
        MTracker.trackEvent("DoubleTap", "Edit", actAction.getName(), 1);
      } else if (actionMode!=0) { // this allows us to all Edit operations
        // without going switching into jumping mode
          MLog.v("On double tap action Mode !=0");
        actionMode=0;
      } else {
        drawMode=!drawMode; // dont' toggle draw if in createMode
        if (drawMode){
          Toast.makeText(getApplicationContext(),"Drawing mode is ON.",Toast.LENGTH_SHORT).show();
          MTracker.trackEvent("DrawMode", "On", "SlideViewer", 1);
        }
        else{
          Toast.makeText(getApplicationContext(),"Drawing mode is OFF.",Toast.LENGTH_SHORT).show();
          MTracker.trackEvent("DrawMode", "Off", "SlideViewer", 1);
        }  
      }
      MLog.v("createmode="+createMode+" drawMode="+drawMode+" editActMode="+editActMode);
      // need hook to add action here
      createMode=false;
      editActMode=false;
      cv.clear();
      cv.invalidate();
      refreshSwitcher();
      return super.onDoubleTap(e);
    }

    @Override
    public boolean onFling(MotionEvent e1,MotionEvent e2,float velocityX,float velocityY) {
      MLog.v("onFling");
      if (editActMode||createMode||drawMode) { //I'm going to rule out draw mode fling recognition for now -- SWB
        return(false);
      } else {
        if (drawMode) {
          drawMode=false;
          MTracker.trackEvent("DrawMode", "Off", "SlideViewer", 1);
          cv.clear();
          cv.invalidate();
        }
        //MLog.v("single touch called editActMode="+editActMode);
        Point p1= new Point((int)e1.getX(), (int)e1.getY());
        Point p2= new Point((int)e2.getX(), (int)e2.getY());
        if(Math.abs(p1.y-p2.y)<100){
            if(p1.x>p2.x){
                //slide next
                if ((curSlide+1)<ss.getNumSlides()) {
                    int nxt=ss.nextNonHiddenSlide(curSlide,false);
                    if (nxt!=curSlide) {
                      curSlide=nxt;
                      switchToSlide(false,curSlide,false,false);
                    }
                  }
            }else{
                //slide previous
                if (curSlide>0) {
                    int nxt=ss.nextNonHiddenSlide(curSlide,true);
                    if (nxt!=curSlide) {
                      curSlide=nxt;
                      switchToSlide(false,curSlide,false,true);
                    }
                  }
            }
        }
        
        
      }
      return(true);
      //return super.onFling(e1,e2,velocityX,velocityY);
    }

    @Override
    public void onLongPress(MotionEvent x) {
      MLog.v("onLong");
      try {
        mapXYClickCoord(x);

        if (editActMode) { // allows user to open a dialoge window to ecit
          // action URI
          MLog.v("actionClass="+actAction.getClass());
          MLog.v("actionType="+actAction.toString());
          if (actAction!=null) {
            String uriStr="http://www.server.com";
            String intentClass="android.intent.action.VIEW";
            String intentType="intent";
            if (actAction instanceof SlideIntentAction) {
              uriStr=((SlideIntentAction)actAction).getUriString();
            } else if (actAction instanceof SlideVideoAction) {
              uriStr=((SlideVideoAction)actAction).getFilename(); // I think
              // this is
              // what you
              // meant
              // Ahmad...
              intentType="video";
              intentClass="";
            } else {
              Toast.makeText(getApplicationContext(),"Sorry, no editable properties on this action.",Toast.LENGTH_SHORT).show();
              return;
            }
            getActionURI(intentType,intentClass,'|',uriStr,false);
          }

        } else if (!createMode) {
          if (drawMode) {
            drawOptionDialog();
          } else {
            MLog.v("item onLongPress ");
            // sv.openContextMenu(this.sv.cv); //this doesn't work, right? --
            // SWB
            SlideAction hitAction=ss.getActionAtXY(curSlide,(int)x.getX(),(int)x.getY());
            if (hitAction!=null&&!onSide) {
              optionActionDialog(hitAction);
            } else {
              optionDialog();
            }
          }
        }
      } catch (Exception e) {
        MLog.e("on long press e="+e.toString(),e);
      }
      super.onLongPress(x);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      MLog.v("onSingleTapConfirmed");
      if (editActMode||createMode) {
        return(false);
      } else {
        if (drawMode) {
          drawMode=false;
          MTracker.trackEvent("DrawMode", "Off", "SlideViewer", 1);
          cv.clear();
          cv.invalidate();
        }
        MLog.v("single touch called editActMode="+editActMode);
        handleSingleTouch(e);
      }
      return(true);
    }

  }

  /***************************/
  static final int MIN_HW=50; // minimum bounding rectangle width/hight
  //GoogleAnalyticsTracker tracker;
  AdapterContextMenuInfo lastInfo=null;
  private boolean editActMode=false; // activate edit action operation
  private boolean createMode=false; // activate create action operation
  private boolean isCancelPermitted=true; // control what stage when a user can
  // cancel an edit/create action
  // operation
  private Rect actRect=null;
  private SlideAction actAction=null;
  private Rect prevRect=null;
  private boolean onMove=false;
  private boolean onResize=false;
  private boolean onRight=false;
  private boolean onLeft=false;
  private boolean onTop=false;
  private boolean onBottom=false;

  private ImageSwitcher mSwitcher;
  private SlideLoader sl;
  private SlideSet ss;
  private int curSlide=0;
  private String curSlideName=null;
  // private static SlideViewer instance;
  private GestureDetector gd;
  private SlideGestureListener sgl;
  private CanvasView cv;
  private SpriteView sprite;
  private boolean drawMode=false;
  private boolean showActions=false; // showActions says whether we always want
  // to show action bound rectangles

  private Animation[] animationSet=new Animation[4];
  private Paint mPaint;
  private boolean droidEffects=false;
  private int flipSpeed=1000;
  private boolean onSide=false;
  private int heightScale=752;
  int gx=0;
  int gy=0;
  private final static int TRANSITION_FADE=0;
  private final static int TRANSITION_ZOOM=1;
  private final static int TRANSITION_HSCROLL=2;
  private final static int TRANSITION_HYPER=3;
  private final static int TRANSITION_FLIP=4;
  private final static int TRANSITION_NONE=5;
  private final static int TRANSITION_SPIN=6;
  public final static int ACTION_MODE_NONE=0;
  public final static int ACTION_MODE_EDIT=1;
  public final static int ACTION_MODE_CUT=2;
  public final static int ACTION_MODE_COPY=3;
  public final static int ACTION_MODE_DELETE=4;
  public final static int ACTION_MODE_INFO=5;
  private int actionMode=ACTION_MODE_NONE;
  private Bundle bundle;
  private boolean onActivityReturn=false;
  private String currentTransitionName=null;

  private int TRANSITION_DEFAULT=TRANSITION_FLIP;

  private int currentTransition=TRANSITION_DEFAULT;

  public SlideViewer() {}

  @Override
  public boolean onKeyDown(int keyCode,KeyEvent event) {
    MLog.v("keyCode="+keyCode);
    switch (keyCode) {
      case KeyEvent.KEYCODE_PAGE_UP:
      case KeyEvent.KEYCODE_DPAD_LEFT:
        // prev
        if (curSlide>0) {
          int nxt=ss.nextNonHiddenSlide(curSlide,true);
          if (nxt!=curSlide) {
            curSlide=nxt;
            switchToSlide(false,curSlide,false,true);
          }
        }
        return(true);
      case KeyEvent.KEYCODE_DPAD_UP:
        Intent intent=new Intent(this,SlideGrid.class);
        try {
          startActivityForResult(intent,0);
        } catch (Exception e) {
          MLog.e("Error launching intent",e);
        }
        return(true);
      case KeyEvent.KEYCODE_PAGE_DOWN:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        if ((curSlide+1)<ss.getNumSlides()) {
          int nxt=ss.nextNonHiddenSlide(curSlide,false);
          // curSlide++;
          if (nxt!=curSlide) {
            curSlide=nxt;
            switchToSlide(false,curSlide,false,false);
          }
        }
        // next
        return(true);
      case KeyEvent.KEYCODE_B:
        // blank
        cv.toggleBlanking();
        return(true);
      case KeyEvent.KEYCODE_F5:
        // begin
        curSlide=0;
        switchToSlide(true,0,true,false);
        return(true);
        // the next two are also send by the targus and will escape the view
        // mode if we don't disable them
      case KeyEvent.KEYCODE_MEDIA_REWIND:
      case KeyEvent.KEYCODE_ESCAPE:
        return(true);
    }
    return super.onKeyDown(keyCode,event);
  }

  @Override
  protected void onResume() {
    MLog.v("SlideViewer onResume");
    if (ss==null) MLog.v("ss=null");
    super.onResume();
    if (sl==null)
      sl=SlideLoader.getDefaultInstance(this);
    MLog.v("SlideViewer OnResume ");
    BriefletMediaPlayer bmp=sl.getMP();
    if (bmp.isPlaying()) {
      String lz=bmp.getLastZipName();
      if (lz==null || lz.equals(ss.getZipName())) { //only abort if still in same presentation as music played for
        bmp.abortStop();
      }
    }
    MLog.v("onResume ss zipname="+ss.getZipName());
    if (bundle!=null&&!onActivityReturn) {
      curSlide=bundle.getInt("curSlide");
      curSlideName=bundle.getString("curSlideName");
      MLog.v("onResume using bundle");
    }
    onActivityReturn=false;
    MLog.v("onResume curSlide="+curSlide+" curSlideName="+curSlideName);
    if (!sl.getCurrentSlideSet().getZipName().equals(ss.getZipName())) {
      MLog.v("switching back to presentation "+ss.getZipName());
      // switch back to correct presentation if necessary
      //sl.loadSlides(ss.getZipName());
      sl.preloadSlides(ss.getZipName()); //important, slide loader needs to know the current slide set!  -- SWB
      final SlideSet fss=ss;
      (new Thread() { public void run() { //need to background this too
        //sl.loadSlides(slidePath);
        sl.loadSlidesUsingChanged(fss);
      }}).start(); //background build thumbnails
      switchToSlide(false,curSlide,true,false,true); //in case it's not displayed, display it -- SWB
    }
    resync();
    MTracker.trackPageView("/"+this.getLocalClassName());
    /*
    if (tracker!=null) {
      tracker.trackPageView("/"+this.getLocalClassName());
    } else {
      MLog.v("tracker was null in onResume"); // thought it was better to flag
      // this since it happens often
      // apparently -- SWB
    }*/
  }

  private void resync() {
    MLog.v("resync");
    if (ss.getSlideName(curSlide)==null||!ss.getSlideName(curSlide).equals(curSlideName)) {
      MLog.v("Viewer detected mismatch of slide name and position");
      curSlide=ss.getCorrectSlide(curSlide,curSlideName);
      curSlideName=ss.getSlideName(curSlide);
      switchToSlide(false,curSlide,true,false,true);
    }
  }

  private void postResync() {
    mSwitcher.post(new Runnable() {

      public void run() {
        resync();
      }
    });
  }

  private void postDelete(final SlideAction act) {
    cv.post(new Runnable() {

      public void run() {
        if (act.supportsThumbnail())
          refreshSwitcher();
        cv.delete(act.getBounds());
        cv.invalidate();
      }
    });
  }

  public void refreshSwitcher() {
    refreshSwitcher(null);
  }

  /**
   * If we've made a change which involves graphical bitmap overlays on the
   * slides this will force a full rebuild of the slide.
   */
  public void refreshSwitcher(SlideAction exclude) {
    mSwitcher.setOutAnimation(null);
    mSwitcher.setInAnimation(null);
    mSwitcher.setImageDrawable(sl.getSlide(curSlide,exclude));
  }

  public void setAnimationSet(int num) {
    switch (num) {
      case TRANSITION_NONE:
        animationSet[0]=null;
        animationSet[1]=null;
        animationSet[2]=null;
        animationSet[3]=null;
        break;
      case TRANSITION_FADE: // fade
        animationSet[0]=AnimationUtils.loadAnimation(this,android.R.anim.fade_out);
        animationSet[1]=AnimationUtils.loadAnimation(this,android.R.anim.fade_in);
        animationSet[2]=animationSet[0];
        animationSet[3]=animationSet[1];
        break;
      case TRANSITION_ZOOM: // zoom
        animationSet[0]=AnimationUtils.loadAnimation(this,R.anim.zoom_prev_out);
        animationSet[1]=AnimationUtils.loadAnimation(this,R.anim.zoom_prev_in);
        animationSet[2]=AnimationUtils.loadAnimation(this,R.anim.zoom_next_out);
        animationSet[3]=AnimationUtils.loadAnimation(this,R.anim.zoom_next_in);
        break;
      case TRANSITION_SPIN: // spin
        animationSet[0]=AnimationUtils.loadAnimation(this,R.anim.spin_prev_out);
        animationSet[1]=AnimationUtils.loadAnimation(this,R.anim.spin_prev_in);
        animationSet[2]=AnimationUtils.loadAnimation(this,R.anim.spin_next_out);
        animationSet[3]=AnimationUtils.loadAnimation(this,R.anim.spin_next_in);
        break;
      case TRANSITION_HSCROLL: // hslide
        animationSet[0]=AnimationUtils.loadAnimation(this,R.anim.left_prev_out);
        animationSet[1]=AnimationUtils.loadAnimation(this,R.anim.left_prev_in);
        animationSet[2]=AnimationUtils.loadAnimation(this,R.anim.left_next_out);
        animationSet[3]=AnimationUtils.loadAnimation(this,R.anim.left_next_in);
        break;
      case TRANSITION_HYPER:
        animationSet[0]=AnimationUtils.loadAnimation(this,R.anim.hyperspace_out);
        animationSet[1]=AnimationUtils.loadAnimation(this,R.anim.hyperspace_in);
        animationSet[2]=animationSet[0];
        animationSet[3]=animationSet[1];
        break;
      case TRANSITION_FLIP:
        Rotate3dAnimation rotation=new Rotate3dAnimation(0,90,1280/2,800/2,310.0f,true);
        rotation.setDuration(flipSpeed);
        rotation.setInterpolator(new AccelerateInterpolator());
        Rotate3dAnimation rotation2=new Rotate3dAnimation(-90,0,1280/2,800/2,310.0f,false);
        rotation2.setDuration(flipSpeed);
        rotation2.setStartOffset(flipSpeed);
        rotation2.setInterpolator(new DecelerateInterpolator());
        Rotate3dAnimation rotation3=new Rotate3dAnimation(0,-90,1280/2,800/2,310.0f,true);
        rotation3.setDuration(flipSpeed);
        rotation3.setInterpolator(new AccelerateInterpolator());
        Rotate3dAnimation rotation4=new Rotate3dAnimation(90,0,1280/2,800/2,310.0f,false);
        rotation4.setDuration(flipSpeed);
        rotation4.setStartOffset(flipSpeed);
        rotation2.setInterpolator(new DecelerateInterpolator());
        animationSet[0]=rotation3;
        animationSet[1]=rotation4;
        animationSet[2]=rotation;
        animationSet[3]=rotation2;
        break;
    }
    if (animationSet[1]!=null) animationSet[1].setAnimationListener(this);
    if (animationSet[3]!=null) animationSet[3].setAnimationListener(this);
  }

  public void drawOptionDialog() {
    try {
      AlertDialog.Builder builder=new AlertDialog.Builder(this);
      builder.setTitle("Drawing Options");
      final CharSequence[] items=new CharSequence[1];
      items[0]="Choose Color";
      final SlideViewer slideViewer=this;
      builder.setItems(items,new DialogInterface.OnClickListener() {
      
        public void onClick(DialogInterface dialog,int item) {
          if (item==0) {
            mPaint.setXfermode(null);
            mPaint.setAlpha(0xFF);
            new ColorPickerDialog(slideViewer,slideViewer,mPaint.getColor()).show();
          }
        }
      });
      AlertDialog alert=builder.create();
      MTracker.trackEvent("DrawMode", "Choose Color", "SlideViewer", 1);
      alert.show();
    } catch (Exception e) {
      MLog.e("exception openOptionDialog e="+e.toString(),e);
    }
  }

  public void showHideActions(boolean isVisible) {
    showActions=isVisible;
    if (!showActions) {
      cv.clearAll();
      cv.invalidate();
    }
    switchToSlide(false,curSlide,true,false,true);
  }

  public void optionDialog() {
    try {
      AlertDialog.Builder builder=new AlertDialog.Builder(this);
      builder.setTitle("Options");
      List<String> items=new ArrayList<String>();
      final List<Integer> selected= new ArrayList<Integer>();
      selected.add(curSlide);
      boolean clip=ClipboardUtil.isSlideOnClipboard(this);
      boolean hidden=ss.isHidden(curSlide);
      if (hidden) {
        items.add("Unhide Slide");
      } else {
        items.add("Hide Slide");
      }
      if (showActions) {
        items.add("Hide Actions");
      } else {
        items.add("Show Actions");
      }
      if (ss.getNumSlides()>1) {
        items.add("Cut Slide");
      }
      items.add("Copy Slide");
      if (clip) {
        items.add("Paste Before"); // only enabled if image to paste
        items.add("Paste After"); // only enabled if image to paste
      }
      if (ss.getNumSlides()>1) {
        items.add("Delete Slide");
      }
      items.add("Set Slide As Template");
      if (curSlide>0) {
        items.add("Set Slide Transition");
      }
      if (!onSide) {
        items.add("Copy As Slide Link Action");
        items.add("Create Action");
        if (ClipboardUtil.isSlideActionOnClipboard(this)) {
          items.add("Paste Action"); // only enabled if intent to paste
        }
      }
      final CharSequence[] items2=new CharSequence[items.size()];
      for(int i=0;i<items.size();i++)
        items2[i]=items.get(i);
      final SlideViewer sv=this;
      builder.setItems(items2,new DialogInterface.OnClickListener() {

        public void onClick(DialogInterface dialog,int item) {
          CharSequence name=items2[item];
          if (name.equals("Create Action")) {
            createAction();
          } else if (name.equals("Unhide Slide")) {
            ss.setHidden(mSwitcher,curSlide,false);
            MTracker.trackEvent("Slide Options","Unhide","SlideViewer",2); // Value
          } else if (name.equals("Hide Slide")) {
            ss.setHidden(mSwitcher,curSlide,true);
            MTracker.trackEvent("Slide Options","Hide","SlideViewer",2); // Value
          } else if (name.equals("Set Slide Transition")) {
        	 MTracker.trackEvent("Slide Options", "Set Slide Transition","SlideViewer",  2);
            DialogUtil.chooseArrayItem(sv,"Choose Transition",R.array.TransitionNames,R.array.TransitionValues,
                new StringResultListener() {

                  public void setResult(String str) {
                    ss.setTransition(mSwitcher,curSlideName,str);
                    MTracker.trackEvent("Choose Transition", str, "SlideViewer", 1);
                  }
                });
            MTracker.trackEvent("Slide Options","SetSlideTransition","SlideViewer",2); // Value
          } else if (name.equals("Show Actions")) {
            showHideActions(true);
            MTracker.trackEvent("Slide Options","Show Actions","SlideViewer",2); // Value
          } else if (name.equals("Hide Actions")) {
            showHideActions(false);
            MTracker.trackEvent("Slide Options","Hide Actions","SlideViewer",2); // Value
          } else if (name.equals("Cut Slide")) {
            try {
              
              if(ss.getNumSlides()==1){
                  
                  AlertDialog.Builder builder=new AlertDialog.Builder(sv);
                  builder.setMessage("Presentations must contain at least one Slide.");
                  builder.setNeutralButton("Ok",new DialogInterface.OnClickListener() {

                    public void onClick(DialogInterface dialog,int id) {
                      // ok was clicked
                    }
                  });
                  builder.show();
                  
              }else{
                  ClipboardUtil.copySlidesToClipboard(ss,selected,sv);
                  ss.deleteSlide(mSwitcher,curSlide,new Runnable() {

                      public void run() {
                        sv.postResync();
                      }
                    });
              }
              
              
              
              MTracker.trackEvent("Slide Options","Cut","SlideViewer",2); // Value
            } catch (Exception e) {
              MLog.e("Error during copy of slide",e);
              Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide to clipboard.",
                  Toast.LENGTH_SHORT).show();
            }
          } else if (name.equals("Copy Slide")) {
            try {
              ClipboardUtil.copySlidesToClipboard(ss,selected,sv);
              MTracker.trackEvent("Slide Options","Copy","SlideViewer",2); // Value
            } catch (Exception e) {
              MLog.e("Error during copy of slide",e);
              Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide to clipboard.",
                  Toast.LENGTH_SHORT).show();
            }
          } else if (name.equals("Paste Before")) {
        	  try {
                  List<SlideInfo> slides=ClipboardUtil.getSlidesFromClipboard(getApplicationContext());
                  int position= curSlide;
                  String[] names = new String[slides.size()];

                  int x=0;
                  for(SlideInfo slide: slides){
                      names[x]=ss.insertNoSave(position, slide, true);
                      ss.updateThumbnail(slide, names[x], false);
                      x++;
                  }
                  ss.updateProps();
                  /*
                  x=0;
                  for(SlideInfo slide: slides){
                      ss.updateThumbnail(slide, names[x], false);
                      x++;
                  }*/
                  MTracker.trackEvent("Slide Options", "Paste_before","SlideViewer",  2); // Value
                  ss.saveAll(cv, null, names, slides, null);
                  
                  //ss.insert(gv,info.position,si,true,new Runnable() { public void run() { sg.postRefreshView(); }});
                } catch (Exception e) {
                  MLog.e("Error in paste operation",e);
                  Toast.makeText(getApplicationContext(),"Sorry, an error was encountered during the paste operation.",Toast.LENGTH_SHORT).show();
                }
          } else if (name.equals("Paste After")) {
        	  try {
                  List<SlideInfo> slides=ClipboardUtil.getSlidesFromClipboard(getApplicationContext());
                  int position= curSlide;
                  String[] names = new String[slides.size()];

                  int x=0;
                  for(SlideInfo slide: slides){
                      names[x]=ss.insertNoSave(position, slide, false);
                      ss.updateThumbnail(slide, names[x], false);
                      x++;
                  }
                  ss.updateProps();
                  /*
                  x=0;
                  for(SlideInfo slide: slides){
                      ss.updateThumbnail(slide, names[x], false);
                      x++;
                  }*/
                  MTracker.trackEvent("Slide Options", "Paste_after","SlideViewer",  2); // Value
                  ss.saveAll(cv, null, names, slides, null);
                  
                  //ss.insert(gv,info.position,si,true,new Runnable() { public void run() { sg.postRefreshView(); }});
                } catch (Exception e) {
                  MLog.e("Error in paste operation",e);
                  Toast.makeText(getApplicationContext(),"Sorry, an error was encountered during the paste operation.",Toast.LENGTH_SHORT).show();
                }
          } else if (name.equals("Delete Slide")) {
            final String slideName=ss.getSlideName(curSlide);
            int pos1=curSlide+1;
            DialogUtil.guardAction("Are you sure you want to delete slide "+pos1+"?  This operation cannot be undone.",
                new Runnable() {

                  public void run() {
                      if(ss.getNumSlides()==1){
                          
                          AlertDialog.Builder builder=new AlertDialog.Builder(sv);
                          builder.setMessage("Presentations must contain at least one Slide.");
                          builder.setNeutralButton("Ok",new DialogInterface.OnClickListener() {

                            public void onClick(DialogInterface dialog,int id) {
                              // ok was clicked
                            }
                          });
                          builder.show();
                          
                      }else{
                          ss.deleteSlide(mSwitcher,slideName,new Runnable() {

                              public void run() {
                                sv.postResync();
                              }
                            });
                      }   
                  }
                },sv);
            MTracker.trackEvent("Slide Options","Delete","SlideViewer",2); // Value
          } else if (name.equals("Set Slide As Template")) {
            sl.copyOutSlide(curSlide,"/sdcard/Slides/template.png");
            MTracker.trackEvent("Slide Options","Save_template","SlideViewer",2); // Value
          } else if (name.equals("Copy As Slide Link Action")) {
            try {
              ClipboardUtil.copySlideLinksToClipboard(ss,selected,sv);
            } catch (Exception e) {
              MLog.e("Error during copy of slide link",e);
              Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide link to clipboard.",
                  Toast.LENGTH_SHORT).show();
            }
            MTracker.trackEvent("Slide Options","CopySlideLinkAction","SlideViewer",1); // Value
          } else if (name.equals("Paste Action")) {
            try {
              Intent intent=ClipboardUtil.getSlideActionFromClipboard(sv);
              List<SlideAction> actions=ss.getActionsForIntent(intent);
             //SlideAction action=ss.getActionForIntent(intent);
              if (actions==null) {
                Toast.makeText(getApplicationContext(),"Sorry, an error was encountered during the paste action operation.",
                    Toast.LENGTH_SHORT).show();
              } else {
                addActions(actions);
                if(actions.size()==1&&showActions){
                    SlideAction sa = actions.get(0);
                    cv.addARect(sa.getBounds());
                    cv.invalidate();
                }else if(actions.size()>1){
                    createMode=false;
                    //addAction2(action);
                    ss.updatePropsAndSave(mSwitcher,null);
                    if (showActions) { // only add in if in show rects mode
                      for(SlideAction sa : actions)
                          cv.addARect(sa.getBounds());
                      
                      cv.invalidate();
                    }
                    
                    //createMode=false;
                    cv.clear();
                    cv.invalidate();
                    refreshSwitcher();
                }
                
                MTracker.trackEvent("Slide Options","Paste Action",actions.get(0).getName().substring(0,5),1); // Value
              }
            } catch (Exception e) {
              MLog.e("Error in paste action operation",e);
              Toast.makeText(getApplicationContext(),"Sorry, an error was encountered during the paste action operation.",
                  Toast.LENGTH_SHORT).show();
            }

          }
        }
      });
      AlertDialog alert=builder.create();
      alert.show();
    } catch (Exception e) {
      MLog.e("exception openDialog e="+e.toString(),e);
    }
  }

  public void optionActionDialog(final SlideAction hitAction) {
    // should not be called with hitAction==null anymore, clean up code later --
    // SWB
    try {
      AlertDialog.Builder builder=new AlertDialog.Builder(this);
      builder.setTitle("Actions");
      MLog.v("Actions curslide="+curSlide);
      List<String> items=new ArrayList<String>();
      if (hitAction.supportsThumbnail()) {
        if (hitAction.isThumbVisible()) {
          items.add("Hide Thumbnail");
        } else {
          items.add("Show Thumbnail");
        }
      }
      if (hitAction instanceof MediaAction) {
        MediaAction ma=(MediaAction)hitAction;
        if (ma.isLoop()) {
          items.add("Play Once");
        } else {
          items.add("Loop Media");
        }
        if (ma.isAutoStart()) {
          items.add("Start on Click");
        } else {
          items.add("Auto Start");
        }
      }
      // Todo: add special options for audio here (possibly merge with above
      // (e.g. play once, instead of play video once)
      items.add("Edit Action");
      items.add("Cut Action");
      items.add("Copy Action");
      items.add("Delete Action");
      items.add("View Action");
      final SlideViewer sv=this;
      final CharSequence[] items2=new CharSequence[items.size()];
      for(int i=0;i<items.size();i++)
        items2[i]=items.get(i);
      builder.setItems(items2,new DialogInterface.OnClickListener() {

        public void onClick(DialogInterface dialog,int item) {
          // in the future, we either have to get Option Dialogs working, or
          // borrow the menu structure,
          // using display strings to compare is bad for many reasons -- SWB
          CharSequence name=items2[item];
          if (name.equals("Edit Action")) {
            editAction3(ACTION_MODE_EDIT,hitAction); // editAction3 if
          } else if (name.equals("Delete Action")) {
            editAction3(ACTION_MODE_DELETE,hitAction); // editAction3 if
          } else if (name.equals("Cut Action")) {
            editAction3(ACTION_MODE_CUT,hitAction); // editAction3 if non-null
          } else if (name.equals("Copy Action")) {
            editAction3(ACTION_MODE_COPY,hitAction); // editAction3 if
            cv.clear();
            cv.invalidate();
          } else if (name.equals("View Action")) {
            editAction3(ACTION_MODE_INFO,hitAction); // editAction3 if
          } else if (name.equals("Copy As Slide Link Action")) {
            try {
              ClipboardUtil.copySlideLinkToClipboard(ss,curSlide,sv);
              MTracker.trackEvent("Actions", "Copy As Slide Link Action", "SlideViewer", 1);
            } catch (Exception e) {
              MLog.e("Error during copy of slide link",e);
              Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide link to clipboard.",
                  Toast.LENGTH_SHORT).show();
            }
          } else if (name.equals("Hide Thumbnail")) {
            hitAction.setThumbnailVisible(false);
            refreshSwitcher(); // do repaint first?
            MTracker.trackEvent("Actions", "Hide Thumbnail", "SlideViewer", 1);
            ss.updatePropsAndSave(mSwitcher,new Runnable() {

              public void run() {}
            });
          } else if (name.equals("Show Thumbnail")) {
            hitAction.setThumbnailVisible(true);
            refreshSwitcher(); // do repaint first?
            MTracker.trackEvent("Actions", "Show Thumbnail", "SlideViewer", 1);
            ss.updatePropsAndSave(mSwitcher,new Runnable() {

              public void run() {}
            });
          } else if (name.equals("Play Once")) {
            MediaAction ma=(MediaAction)hitAction;
            ma.setLoop(false);
            MTracker.trackEvent("Actions", "PlayOnce", "SlideViewer", 1);
            ss.updatePropsAndSave(mSwitcher,new Runnable() {

              public void run() {}
            });
          } else if (name.equals("Loop Media")) {
            MediaAction ma=(MediaAction)hitAction;
            ma.setLoop(true);
            MTracker.trackEvent("Actions", "Loop Media", "SlideViewer", 1);
            ss.updatePropsAndSave(mSwitcher,new Runnable() {

              public void run() {}
            });
          } else if (name.equals("Auto Start")) {
            MediaAction ma=(MediaAction)hitAction;
            ma.setAutoStart(true);
            MTracker.trackEvent("Actions", "Auto Start", "SlideViewer", 1);
            ss.updatePropsAndSave(mSwitcher,new Runnable() {

              public void run() {}
            });
          } else if (name.equals("Start on Click")) {
            MediaAction ma=(MediaAction)hitAction;
            ma.setAutoStart(false);
            MTracker.trackEvent("Actions", "Start on Click", "SlideViewer", 1);
            ss.updatePropsAndSave(mSwitcher,new Runnable() {

              public void run() {}
            });
          }
        }
      });
      AlertDialog alert=builder.create();
      alert.show();
    } catch (Exception e) {
      MLog.e("openActionDialog e="+e.toString(),e);
    }
  }

  private void createAction() {
    try {
      AlertDialog.Builder builder=new AlertDialog.Builder(this);
      builder.setTitle("Actions");
      int len=7;
      final CharSequence[] items=new CharSequence[len];
      int c=0;
      items[c++]="Browser";
      items[c++]="Google Maps";
      // items[c++]="Google Earth";
      items[c++]="Streets";
      items[c++]="Navigation";
      items[c++]="Launch Any Application";
      items[c++]="Play Song";
      items[c++]="Create QR Code";
      // Todo: add "Play Audio" (play a file)
      // Todo: add "Audio Control" (play,pause,stop)
      // you can set other things off the action themselves (loop, continuous,
      // auto-play, volume)

      final SlideViewer sv=this;
      builder.setItems(items,new DialogInterface.OnClickListener() {

        public void onClick(DialogInterface dialog,int item) {
          CharSequence name=items[item];
          if (name.equals("Google Maps")) {
            getActionURI("intent","android.intent.action.VIEW",'|',"geo:39.05034,-77.12065",true);
            MTracker.trackEvent("Actions", "Google Maps", "SlideViewer", 1);
          } else if (name.equals("Create QR Code")) {
        	  
        	addQRCodeAction("intent","android.intent.action.VIEW",',',"http://www.google.com");  
            
          } else if (name.equals("Streets")) {
            getActionURI("intent","android.intent.action.VIEW",'|',"google.streetview:cbll=39.05034,-77.12065",true);
            MTracker.trackEvent("Actions", "Streets", "SlideViewer", 1);
          } else if (name.equals("Browser")) {
            getActionURI("intent","android.intent.action.VIEW",',',"http://www.google.com",true);
            MTracker.trackEvent("Actions", "Browser", "SlideViewer", 1);
          } else if (name.equals("Navigation")) {
            getActionURI("intent","android.intent.action.VIEW",',',"google.navigation:q=39.05034,-77.12065",true);
            MTracker.trackEvent("Actions", "Navigation", "SlideViewer", 1);
          } else if (name.equals("Launch Any Application")) {
            IntentUtil.chooseApp(sv,new AppResultListener() {

              @Override
              public void setResult(App app) {
                String pkg=app.intent.getComponent().getPackageName();
                String cls=app.intent.getComponent().getClassName();
                // Drawable ico=app.icon;
                SlideAction action=SlideAction.createInstance("launch|"+pkg+"|"+cls+"|"+gx+"|"+gy+"|"+(gx+72)+"|"+(gy+72)+"|1");
                addAction2(action);
                // MLog.v("chose "+app.title); //make a SlideAction next then
                // call addAction2
              }
            });
            MTracker.trackEvent("Actions", "Launch Any App", "SlideViewer", 1);
          } else if (name.equals("Play Song")) {
            MusicUtil.chooseSong(sv,new StringResultListener() {

              @Override
              public void setResult(String result) {
                if (result==null) {
                  Toast.makeText(getApplicationContext(),"Sorry, no song found on your device.",Toast.LENGTH_SHORT).show();
                } else {
                  //int pos=result.indexOf("|");
                  //String albumId=result.substring(0,pos);
                  //result=result.substring(pos+1);
                  // audio|uri|left|top|right|bottom|start|duration|thumb|loop|autoStart|cont|volume|control|bitmapname
                  MLog.v("Chosen Song="+result);
                  SlideAction action=SlideAction.createInstance("audio|"+result+"|"+gx+"|"+gy+"|"+(gx+72)+"|"+(gy+72)
                      +"|0|-1|1|0|0|0|-1|0");
                  Bitmap thumb=MusicUtil.getThumb(sv, result);
                  if(thumb!=null){
                      action.setBitmap(thumb);
                      action.setThumbnailVisible(true);
                  }
                  /*
                  Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
                  Uri uri0 = ContentUris.withAppendedId(sArtworkUri, Long.parseLong(albumId));
                  ContentResolver res = sv.getContentResolver();
                  try {
                    InputStream in = res.openInputStream(uri0);
                    Bitmap artwork = BitmapFactory.decodeStream(in);
                    action.setBitmap(artwork);
                    action.setThumbnailVisible(true);
                  } catch (FileNotFoundException e) {
                    MLog.e("Error getting album art",e);
                  }*/                  
                  addAction2(action);
                }
              }
            });
          }
        }
      });
      MTracker.trackEvent("Actions", "Play Song", "SlideViewer", 1);
      AlertDialog alert=builder.create();
      alert.show();

    } catch (Exception e) {
      MLog.e(" exception "+e.toString(),e);
    }
  }

  private void getActionURI(final String type,final String cl,final char delim,final String v,final boolean isAdd) {
    AlertDialog.Builder alert=new AlertDialog.Builder(this);
    alert.setTitle("Action URI");
    alert.setMessage("Please enter action URI ");
    // Set an EditText view to get user input
    final EditText input=new EditText(this);
    input.setText(v);
    alert.setView(input);
    alert.setPositiveButton("Ok",new DialogInterface.OnClickListener() {

      public void onClick(DialogInterface dialog,int whichButton) {
        Editable value=input.getText();
        isCancelPermitted=false;
        // Do something with value!
        if (isAdd)
          addAction(type,cl,delim,v);
        else
            Toast.makeText(getApplicationContext(),"Move or Resize the Action Area and Double-Tap To Finish",Toast.LENGTH_SHORT).show();
        
        if(actAction instanceof SlideIntentAction){
        	if(v.contains("Slides")){
        		String val= value.toString();
        		SlideSet linked_set= sl.getSlideSetByPath(val.substring(0, val.indexOf("#")));
        		if(linked_set!=null){
        			String entry_name=val.substring(val.indexOf("#")+1);
        			int slide_num= linked_set.getSlideNumber(entry_name);
        			if(slide_num>-1){
        				sl.loadSlidesInBackground(linked_set, slide_num);
        				Bitmap b= linked_set.getBitmap(slide_num);
        				actAction.setBitmap(b);
        				sl.clearCache();
        				File file=SlideLoader.getChangedFile(ss.getZipName(),actAction.getBitmapName(),false);
        			      if (file.exists()) {
        			        file.delete();
        			      }
        			}
        			
        		}  
        	}
        }
        
        MLog.v("v value is,="+value.toString());
        actAction.setURIString(value.toString());
        MLog.v("action is,="+actAction.getPropertyString());
      }
    });
    alert.setNegativeButton("Cancel",new DialogInterface.OnClickListener() {

      public void onClick(DialogInterface dialog,int whichButton) {
        // Canceled.
      }
    });
    alert.show();
  }
  
  private void addQRCodeAction(final String type,final String cl,final char delim,String uri){
	  AlertDialog.Builder alert=new AlertDialog.Builder(this);
	    alert.setTitle("Action URI");
	    alert.setMessage("Please enter action URI ");
	    // Set an EditText view to get user input
	    final EditText input=new EditText(this);
	    input.setText(uri);
	    alert.setView(input);
	    alert.setPositiveButton("Ok",new DialogInterface.OnClickListener() {

	      public void onClick(DialogInterface dialog,int whichButton) {
	        Editable value=input.getText();
	        String url = value.toString();
	        
		  	int r=gx+300;
		    int b=gy+300;
		    String str=type+delim+cl+delim+url+delim+gx+delim+gy+delim+r+delim+b+delim+"1";
		    SlideAction act=SlideAction.createInstance(str);
		    Bitmap bitmap=null;
			try {
				bitmap = QRCodeEncoder.encodeAsBitmap(url, BarcodeFormat.QR_CODE, 100);
			} catch (WriterException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		    act.setBitmap(bitmap);

		    addAction2(act);

	        actAction.setURIString(url);
	      }
	    });
	    alert.show();

  }
  private void addAction(String type,String cl,char delim,String uri) {
    MLog.v("x="+gx+" y="+gy);
    int r=gx+300;
    int b=gy+100;
    String str=type+delim+cl+delim+uri+delim+gx+delim+gy+delim+r+delim+b+delim+"1";
    MLog.v("str="+str);
    SlideAction act=SlideAction.createInstance(str);
    MTracker.trackEvent("Actions", // Category
        "Add", // Action
        type, // Label
        1); // Value

    addAction2(act);
  }

  private void addAction2(SlideAction act) {
    Toast.makeText(getApplicationContext(),"Move or Resize the Action Area and Double-Tap To Finish",Toast.LENGTH_SHORT).show();
    actRect=act.getBounds();
    actAction=act;
    prevRect=actRect;
    createMode=true;
    ss.addAction(curSlide,act);
    cv.addRect(actRect,act); // got lucky in that Rect is modifiable and now
    // added to CV?
    cv.invalidate();
  }
  private void addActions(List<SlideAction> actions){
      if(actions.size()==1)
          Toast.makeText(getApplicationContext(),"Move or Resize the Action Area and Double-Tap To Finish",Toast.LENGTH_SHORT).show();
      else
          Toast.makeText(getApplicationContext(),"Actions created. Selected each action individually to resize.",Toast.LENGTH_SHORT).show();
      Iterator<SlideAction> it= actions.iterator();
      positionActionsInGrid(actions);
      while(it.hasNext()){
          SlideAction action =it.next();
          MLog.v("Action bounds. Left: "+action.getLeft()+ " Top: " +action.getTop()+
                  " Right: " +action.getRight()+ " Bottom: "+action.getBottom());
          actRect=action.getBounds();
          actAction=action;
          prevRect=actRect;
          createMode=true;
          action.setThumbnailVisible(true);
          ss.addAction(curSlide,action);
          
          /*if(it.hasNext())
              cv.addARect(actRect);
          else*/
              cv.addRect(actRect,action); // got lucky in that Rect is modifiable and now
          // added to CV?
      }
      //showActions=true; //this is a user option, you don't invoke it automatically -- SWB
      
      
      cv.invalidate();
      
  }
  private void positionActionsInGrid(List<SlideAction> actions){
      int num_rows= (int)Math.ceil(Math.sqrt(actions.size()));
      //MLog.v("num rows: "+ num_rows);
      //SlideLoader sl= SlideLoader.getDefaultInstance();
      int thumb_size= 100;
      //MLog.v("Thumb Size: "+ thumb_size);
      double scale=.25;
      int padding =(int)(thumb_size*scale);
      //MLog.v("padding: "+ padding);
      int total_size= num_rows*thumb_size+((num_rows+1)*padding);
      //MLog.v("Total Size: "+ total_size);
      int max_size= 700;
      //int max_height=(int)(SlideLoader.theight*(.75));
      //MLog.v("Max Size: "+ max_size);
      int start_left=(1280/2)-(max_size/2);
      int start_top=(SlideLoader.theight/2)-(max_size/2);
      
      //Rect grid= new Rect(start_left, start_top,start_left+max_size, start_top+max_size);
      
      if(total_size>max_size){
          MLog.v("COnstraining size");
          int diff= total_size-max_size;
          MLog.v("diff: "+diff);
          double thumb_diff=diff*(1-scale);
          MLog.v("thumb_diff: "+thumb_diff);
          thumb_size=thumb_size-(int)(thumb_diff/num_rows);
          MLog.v("thumb_size: "+thumb_size);
         //resizeAll(actions, px_per_thumb);
          double pad_diff=diff-thumb_diff;
          MLog.v("pad_diff: "+pad_diff);
          padding= padding-(int)(pad_diff/(num_rows+1));
          MLog.v("padding: "+padding);
          total_size=max_size;
          MLog.v("total_size: "+total_size);
          
      }/*else if(total_size>max_height){
          MLog.v("COnstraining Height");
          int diff= total_size-max_height;
          double thumb_diff=diff*(1-scale);
          thumb_size= (int)(thumb_diff/num_rows);
          //resizeAll(actions, px_per_thumb);
          double pad_diff=diff-thumb_diff;
          padding= (int)(pad_diff/(num_rows+1));
          total_size=max_height;
      }*/

      //start laying out the thumbnails
      int z=0;
      for(int x=0; x<num_rows; x++){
          for(int y=0; y<num_rows; y++){
              if(z>=actions.size())
                  return;
              SlideAction action= actions.get(z);
              int left=((y+1)*padding)+(y*thumb_size)+start_left;
              int top=((x+1)*padding)+(x*thumb_size)+start_top;
              int right= left+thumb_size;
              int bottom= top+thumb_size;
              action.setBounds(new Rect(left,top,right,bottom));
              
              z++;
          }
      }
      
      
  }
  /*
  private void resizeAll(List<SlideAction>actions, int newSize){
      for(SlideAction action : actions){
          int left= action.getLeft();
          int newRight=left+newSize;
          int top=action.getTop();
          int newBottom= top+newSize;
          action.setBounds(new Rect(left,top,newRight,newBottom));
      }
  }*/

  private void selectActionToEdit(SlideAction action) {
    Toast.makeText(getApplicationContext(),"Move or Resize the Action Area and Double-Tap To Finish",Toast.LENGTH_SHORT).show();
    actAction=action;
    actRect=action.getBounds();
    prevRect=actRect;
    cv.addRect(actRect,action);
    cv.invalidate();
  }

  private void editAction2(MotionEvent e) {
    SlideAction action=ss.getActionAtXY(curSlide,(int)e.getX(),(int)e.getY());
    isCancelPermitted=false;
    if (action!=null) {
      MLog.v("action is in editAction2 one para");
      cv.clear();
      cv.addRect(action.getBounds(),action);
      editAction3(action);
    } else {
      Toast.makeText(getApplicationContext(),"Double-Tap If you want to cancel.",Toast.LENGTH_SHORT).show();
    }
  }

  private void editAction3(int mode,SlideAction action) {
    actionMode=mode;
    editAction3(action);
  }

  private void editAction3(SlideAction action) {
    final SlideAction act=action;
    final SlideViewer sv=this;
    editActMode=false;
    try {
      switch (actionMode) {
        case ACTION_MODE_EDIT:
          editActMode=true;
          selectActionToEdit(act);
          MTracker.trackEvent("Actions", // Category
              "Edit", // Action
              action.getName().substring(0,5), // Label
              1); // Value
          break;
        case ACTION_MODE_DELETE:
          isCancelPermitted=true;
          guardAction("Are you sure would like to delete "+action.getName()+"?",new Runnable() {

            public void run() {
              ss.deleteAction(mSwitcher,curSlide,act,new Runnable() {

                public void run() {
                  sv.postDelete(act);
                }
              });
              cv.delete(act.getBounds());
            }
          });
          MTracker.trackEvent("Actions", // Category
              "Delete", // Action
              action.getName().substring(0,5), // Label
              1); // Value
          break;
        case ACTION_MODE_CUT:
          guardAction("Are you sure would like to cut "+action.getName()+"?",new Runnable() {

            public void run() {
              try {
                ClipboardUtil.copyActionToClipboard(act,sv);
              } catch (Exception e) {
                MLog.e("Error while copying action to clipboard.",e);
                Toast.makeText(getApplicationContext(),"Internal error while copying action to clipboard.",Toast.LENGTH_SHORT)
                    .show();
              }
              ss.deleteAction(mSwitcher,curSlide,act,new Runnable() {

                public void run() {
                  sv.postDelete(act);
                }
              });
              cv.delete(act.getBounds());
            }
          });
          MTracker.trackEvent("Actions", // Category
              "Cut", // Action
              action.getName().substring(0,5), // Label
              1); // Value
          break;
        case ACTION_MODE_COPY:
          try {
            ClipboardUtil.copyActionToClipboard(act,sv);
          } catch (Exception e2) {
            MLog.e("Error while copying action to clipboard.",e2);
            Toast.makeText(getApplicationContext(),"Internal error while copying action to clipboard.",Toast.LENGTH_SHORT).show();
          }
          MTracker.trackEvent("Actions", // Category
              "Copy", // Action
              action.getName().substring(0,5), // Label
              1); // Value
          break;
        case ACTION_MODE_INFO:
          StringBuilder sb=new StringBuilder();
          sb.append("Action: "+action.getName());
          sb.append("\n["+action.getPropertyString()+"]");
          DialogUtil.infoDialog("Action Details",sb.toString(),null,sv);
          MTracker.trackEvent("Actions", // Category
              "Info", // Action
              action.getName().substring(0,5), // Label
              1); // Value
          break;
      }
    } catch (Exception ex) {
      MLog.e("editAction2 e="+ex.toString(),ex);
    }
    if (!editActMode) {
      actionMode=0;
      cv.clear();
      cv.invalidate();
    }
  }

  public void guardAction(String message,final Runnable runnable) {
    AlertDialog.Builder builder=new AlertDialog.Builder(this);
    builder.setMessage(message).setCancelable(false).setPositiveButton("Yes",new DialogInterface.OnClickListener() {

      public void onClick(DialogInterface dialog,int id) {
        runnable.run();
      }
    }).setNegativeButton("No",new DialogInterface.OnClickListener() {

      public void onClick(DialogInterface dialog,int id) {
        dialog.cancel();
      }
    });
    AlertDialog alert=builder.create();
    alert.show();
  }

  private void mapXYClickCoord(MotionEvent e) {
    gx=(int)e.getX();
    gy=(int)e.getY();
  }

  /*
   * public static synchronized SlideViewer getDefaultInstance() { if
   * (instance!=null) return(instance); instance=new SlideViewer();
   * return(instance); }
   */

  /**
   * Switch to a slide
   * 
   * @param first
   *          true if this is the first slide displayed in a new presentation
   *          (e.g. no transition)
   * @param num
   *          slide number to display
   * @param fast
   *          true if should skip transition
   * @param prev
   *          true if we're moving to a previous slide
   */
  
  protected void switchToSlide(boolean first,int num,boolean fast,boolean prev) {
    switchToSlide(first,num,fast,prev,false);
  }
  

  protected void switchToSlide(final boolean first,final int num,boolean fast,final boolean prev,final boolean noauto) {
    curSlideName=ss.getSlideName(num);
    // Brieflet.LogV("ss="+ss.getName()+" slide name="+ss.getSlideName(num));
    setAnimation(fast,prev,num,first);
    // checkDroidEffects();
    if (animationSet[0]==null) fast=true; //if we're using a "none" transition then just use fast mode
    final boolean ffast=fast;
    if (droidEffects&&sprite!=null&&!first&!fast) {
      switch (currentTransition) {
        case TRANSITION_HSCROLL:
          sprite.walkOut(prev,new Runnable() {

            public void run() {
              switchToSlideContinue(first,prev,num,ffast,noauto);
            }
          });
          break;
        case TRANSITION_FLIP:
          sprite.walkOut(!prev,new Runnable() {

            public void run() {
              switchToSlideContinue(first,prev,num,ffast,noauto);
            }
          });
          break;
      }
    } else {
      switchToSlideContinue(first,prev,num,fast,noauto);
    }
  }

  private void switchToSlideContinue(boolean first,boolean prev,int num,boolean fast,boolean noauto) {
    if (showActions) {
      cv.clearAll();
      cv.invalidate();
    }
    mSwitcher.setImageDrawable(sl.getSlide(num));
    // this is where we'd like to know where the image ended up onscreen
    ImageView v=(ImageView)mSwitcher.getCurrentView();
    MLog.v("Area For Image To Fit In is "+v.getLeft()+","+v.getTop()+" "+v.getMeasuredWidth()+"x"+v.getMeasuredHeight());
    adjustView(v);
    if (droidEffects&&sprite!=null&&!first&&!fast) {
      switch (currentTransition) {
        case TRANSITION_HSCROLL:
          sprite.fastMove(prev);
          break;
        case TRANSITION_FLIP:
          sprite.fastThrow(prev);
          break;
      }
    }
    if (fast&&!first) {
      onAnimationEnd(null,noauto); // in case there is no transition at all (e.g. fast)
    }
  }

  public void setSlidePath(String slidePath) {
    setSlidePath(slidePath,null);
  }

  public void setSlidePath(final String slidePath,String slideName) {

    if (sl==null)
      sl=SlideLoader.getDefaultInstance(this);
    final SlideSet fss=sl.preloadSlides(slidePath);
    (new Thread() { public void run() {
      //sl.loadSlides(slidePath);
      sl.loadSlidesUsingChanged(fss);
    }}).start(); //background build thumbnails
    ss=sl.getCurrentSlideSet();
    if (slideName!=null) {
      curSlide=ss.getSlideNumber(slideName);
      if (curSlide==-1) {
        curSlide=0; // if slide name not found
      }
    } else {
      if (curSlide!=0) {} else
        curSlide=0;
    }
    if (ss.isHidden(curSlide)) { // find a non-hidden slide
      curSlide=ss.nextNonHiddenSlide(curSlide,false);
      if (ss.isHidden(curSlide)) {
        curSlide=ss.nextNonHiddenSlide(curSlide,true);
        if (ss.isHidden(curSlide)) {
          curSlide=0;
        }
      }
    }

    switchToSlide(true,curSlide,true,false);
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    String slidePath=intent.getExtras().getString("slidePath");
    String slideName=intent.getExtras().getString("slideName");
    setSlidePath(slidePath,slideName); // on existing one
    setTransitionFromPreference();
  }

  private void setAnimation(boolean fast,boolean prev,int toslide,boolean first) {
    if (fast&&!first) {
      mSwitcher.setOutAnimation(null);
      mSwitcher.setInAnimation(null);
    } else {
      String transName=null;
      if (first) {
        transName="fade"; // fade into first slide
      } else {
        if (prev) {
          transName=ss.getTransitionName(getBaseContext(),toslide+1);
        } else {
          transName=ss.getTransitionName(getBaseContext(),toslide);
        }
      }
      checkDroidEffects(transName);
      MLog.v("ctn="+currentTransitionName+" tn="+transName);
      if (!currentTransitionName.equals(transName)) {
        setTransitionFromName(transName);
      }
      if (prev) {
        mSwitcher.setOutAnimation(animationSet[0]);
        mSwitcher.setInAnimation(animationSet[1]);
      } else {
        mSwitcher.setOutAnimation(animationSet[2]);
        mSwitcher.setInAnimation(animationSet[3]);
      }
    }
  }

  public static void fixRect(Rect rect) {
    int tmp=0;
    if (rect.left>rect.right) {
      tmp=rect.left;
      rect.left=rect.right;
      rect.right=tmp;
    }
    if (rect.top>rect.bottom) {
      tmp=rect.top;
      rect.top=rect.bottom;
      rect.bottom=tmp;
    }
  }

  private void setTransitionFromPreference() {
    try {
      SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      String defaultTransition=prefs.getString("defaultTransition","flip");
      setTransitionFromName(defaultTransition);
    } catch (Exception e) {
      MLog.e("sl error="+e.toString(),e);
    }
  }

  private void setTransitionFromName(String name) {
    if (name.equals("none")) {
      currentTransition=TRANSITION_NONE;
      setAnimationSet(TRANSITION_NONE);
    } else if (name.equals("fade")) {
      currentTransition=TRANSITION_FADE;
      setAnimationSet(TRANSITION_FADE);
    } else if (name.equals("zoom")) {
      currentTransition=TRANSITION_ZOOM;
      setAnimationSet(TRANSITION_ZOOM);
    } else if (name.equals("spin")) {
      currentTransition=TRANSITION_SPIN;
      setAnimationSet(TRANSITION_SPIN);
    } else if (name.equals("hscroll")) {
      currentTransition=TRANSITION_HSCROLL;
      setAnimationSet(TRANSITION_HSCROLL);
    } else if (name.equals("flip")) {
      currentTransition=TRANSITION_FLIP;
      setAnimationSet(TRANSITION_FLIP);
    } else {
      currentTransition=TRANSITION_DEFAULT;
      setAnimationSet(TRANSITION_DEFAULT);
    }
    currentTransitionName=name;
  }

  private void setDefaultColorFromPreference() {
    try {
      SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      String defaultColor=prefs.getString("defaultColor","Green");
      // here's an alternative that lets us use just a list of colors which are
      // defined as resources (ultimately a color picker is nicer though) -- SWB
      mPaint.setColor(getResources().getColor(getResources().getIdentifier(defaultColor.toLowerCase(),"color",getPackageName())));
    } catch (Exception e) {
      MLog.e("sl error="+e.toString(),e);
    }
  }

  private void setDefaultStrokeFromPreference() {
    try {
      SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      String defaultStrokeW=prefs.getString("defaultStrokeWidth","5");
      mPaint.setStrokeWidth(Float.parseFloat(defaultStrokeW));
      MLog.v("Slide viewercolor="+defaultStrokeW);
    } catch (Exception e) {
      MLog.e("sl error="+e.toString(),e);
    }
  }

  private void checkDroidEffects(String transName) {
    SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getBaseContext());
    droidEffects=prefs.getBoolean("droidEffects",false);
    if (transName!=null) {
      if (!transName.equals("hscroll")&&!transName.equals("flip")) {
        droidEffects=false;
      }
    }
    if (!droidEffects) {
      // sprite=null; //keep it in case used again
      flipSpeed=300;
    } else {
      flipSpeed=1000;
    }
  }

  private Point scalePoint(int x,int y) {
    int off=(int)(1232-(1.0*SlideLoader.theight*800/1280))/2;
    if (onSide) {
      return(new Point((int)(1.0*x/1280*800),(int)(off+y*800/1280)));
    } else {
      return(new Point(x,y));
    }
  }

  /**
   * Take touch on rotated screen and synthesize the coordinates that would be
   * the case on a non-rotated screen.
   * 
   * @param x
   *          touch x
   * @param y
   *          touch y
   * @return synthesized non-rotated touch point
   */
  private Point reversePoint(int x,int y) {
    int yht=(int)(1.0*SlideLoader.theight*800/1280);
    int off=(1232-yht)/2;
    if (onSide) {
      int nx=(int)(1.0*x/800*1280);
      int ny=0;
      if (y<off) {
        ny=0;
      } else if (y>(off+yht)) {
        ny=SlideLoader.theight;
      } else {
        ny=(y-off)*1280/800;
      }
      return(new Point(nx,ny));
    } else {
      return(new Point(x,y));
    }
  }

  private Rect scaleRect(Rect r) {
    if (onSide) {
      Point ul=scalePoint(r.left,r.top);
      Point lr=scalePoint(r.right,r.bottom);
      return(new Rect(ul.x,ul.y,lr.x,lr.y));
    } else if (heightScale==752) {
      return(r);
    } else {
      float scale=1f*720/752;
      return(new Rect(r.left,(int)(r.top*scale),r.right,(int)(r.bottom*scale)));
    }
  }

  private void reportDisplay() {
    Display display=((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
    
    int width=display.getWidth();
    int height =display.getHeight();
    MLog.v("Width: " + width);
    MLog.v("Height: " +height);
    switch (display.getRotation()) {
      case Surface.ROTATION_0:
    	if(width<height)
    		onSide=true;
    	else
    		onSide=false;
        MLog.v("Display: ori=0");
        break;
      case Surface.ROTATION_90:
    	if(width<height)
    		onSide=true;
    	else
    		onSide=false;
        MLog.v("Display: ori=90");
        break;
      case Surface.ROTATION_180:
    	if(width<height)
      		onSide=true;
      	else
      		onSide=false;
        MLog.v("Display: ori=180");
        break;
      case Surface.ROTATION_270:
    	if(width<height)
    		onSide=true;
      	else
      		onSide=false;
        MLog.v("Display: ori=270");
        break;
    }
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    MLog.v("SlideViewer onCreate");
    if (ss==null) MLog.v("ss=null");
    try {
      super.onCreate(savedInstanceState);
      MTracker.initTracker(this);
      
      if (savedInstanceState!=null) {
        curSlide=savedInstanceState.getInt("curSlide");
        curSlideName=savedInstanceState.getString("curSlideName");
        MLog.v("**********restoring state curSlide="+curSlide+" curSlideName="+curSlideName);
      }
      MLog.v("onCreate bundle gets instanceState");
      bundle=savedInstanceState;
      mPaint=new Paint();
      mPaint.setAntiAlias(true);
      mPaint.setDither(true);
      mPaint.setColor(Color.GREEN);
      mPaint.setStyle(Paint.Style.STROKE);
      mPaint.setStrokeJoin(Paint.Join.ROUND);
      mPaint.setStrokeCap(Paint.Cap.ROUND);
      mPaint.setStrokeWidth(5);
      checkDroidEffects(null);
      SharedPreferences prefs=PreferenceManager.getDefaultSharedPreferences(getBaseContext());
      showActions=prefs.getBoolean("showActions",false);

      requestWindowFeature(Window.FEATURE_NO_TITLE);

      setContentView(R.layout.image_switcher);

      mSwitcher=(ImageSwitcher)findViewById(R.id.switcher);
      mSwitcher.setFactory(this);

      setTransitionFromPreference();
      setDefaultColorFromPreference();
      setDefaultStrokeFromPreference();

      cv=new CanvasView(this);
      addContentView(cv,new LayoutParams(LayoutParams.FILL_PARENT,LayoutParams.FILL_PARENT));

      if (droidEffects&&(currentTransition==TRANSITION_HSCROLL||currentTransition==TRANSITION_FLIP)) {
        sprite=new SpriteView(this);
        addContentView(sprite,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
      }
      String slidePath=getIntent().getExtras().getString("slidePath");
      String slideName=getIntent().getExtras().getString("slideName");
      setSlidePath(slidePath,slideName); // on a new create

      reportDisplay();
      
      
      /*
      tracker=GoogleAnalyticsTracker.getInstance();
      // Start the tracker in manual dispatch mode...
      tracker.start("UA-23457289-1",20,this);*/
      // tracker.setDebug(true); // put some debug messages in the log
      // tracker.setDryRun(true); // won't send the data to google analytics

      mSwitcher.setFocusable(true);
      mSwitcher.setFocusableInTouchMode(true);

    } catch (Exception e) {
      MLog.e("********slideloader "+e.toString(),e);
    }
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    MLog.v("SlideViewer: onSaveInstanceState");
    // Save UI state changes to the savedInstanceState.
    super.onSaveInstanceState(savedInstanceState);
    savedInstanceState.putInt("curSlide",curSlide);
    savedInstanceState.putString("curSlideName",curSlideName);
    bundle=savedInstanceState;
    // MLog.v("sra","sra: *SlideViewer Saving Instances State curSlide to "+curSlide);
  }

  public void adjustView(ImageView view) {
    Display display=((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
    int width = display.getWidth();
    int height= display.getHeight();
    if (display.getRotation()==Surface.ROTATION_0) {
    	if(width<height){
    		view.setScaleType(ImageView.ScaleType.FIT_CENTER);
    	}else{
    		view.setScaleType(ScaleType.FIT_XY);
    	}
      
    } else {
      if(width<height){
    	  view.setScaleType(ImageView.ScaleType.FIT_CENTER);
      }else{
    	  view.setScaleType(ScaleType.FIT_XY);
      }
    }
    view.setAdjustViewBounds(false);
  }

  public View makeView() {
    // MLog.v("sra","sra:-larg");
    ImageView i=new ImageView(this);
    sgl=new SlideGestureListener(this);
    gd=new GestureDetector(this,sgl);
    i.setBackgroundColor(0xFF000000);
    adjustView(i);
    i.setLayoutParams(new ImageSwitcher.LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT));
    return i;
  }

  @Override
  protected void onActivityResult(int requestCode,int resultCode,Intent data) {
    super.onActivityResult(requestCode,resultCode,data);
    onActivityReturn=true;
    if (requestCode==0) { // back from grid view
      if (resultCode!=-1) {
        curSlide=resultCode;
        MLog.v("onActivityResult curSlide="+curSlide);
        switchToSlide(false,curSlide,true,false);
      }
    } else if (requestCode==1) { // back from video view
      int x=-1;
      int y=-1;
      if (data!=null) {
        x=data.getIntExtra("x",-1);
        y=data.getIntExtra("y",-1);
      }
      if (x>-1) {
        MotionEvent me=(MotionEvent)data.getExtras().get("com.sra.brieflet.motionEvent");
        if (me!=null) {
          switch (resultCode) {
            case 10: // longPress
              sgl.onLongPress(me);
              break;
            case 11: // doubleTap
              sgl.onDoubleTap(me);
              break;
          }
        } else {
          handleSingleTouch(MotionEvent.ACTION_DOWN,x,y);
        }
      } else {
        switch (resultCode) {
          case 1: // previous
            if (curSlide>0) {
              int nxt=ss.nextNonHiddenSlide(curSlide,true);
              // curSlide--;
              if (nxt!=curSlide) {
                curSlide=nxt;
                switchToSlide(false,curSlide,false,true);
              }
            }
            break;
          case 2: // next
            if ((curSlide+1)<ss.getNumSlides()) {
              int nxt=ss.nextNonHiddenSlide(curSlide,false);
              // curSlide++;
              if (nxt!=curSlide) {
                curSlide=nxt;
                switchToSlide(false,curSlide,false,false);
              }
            }
            break;
          case 3: // goto grid
            Intent intent=new Intent(this,SlideGrid.class);
            try {
              startActivityForResult(intent,0);
            } catch (Exception e) {
              MLog.e("Error launching intent",e);
            }
            break;
          case 4: // first slide
            curSlide=0;
            this.switchToSlide(true,0,true,false);
            break;
        }
      }
    }
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    // super.onTouchEvent(event);
    if (gd.onTouchEvent(event)) {
      return(true);
    } else {
      if (drawMode||createMode||editActMode) {
        if (cv!=null) {
          return(cv.onMyTouchEvent(event));
        }
      }
      return(false);
    }
  }

  public void handleSingleTouch(MotionEvent event) {
    handleSingleTouch(event.getAction(),(int)event.getX(),(int)event.getY());
  }

  public void handleSingleTouch(int action,int x,int y) {
    // consider reading metadata to map regions of the slide to intent
    // activations -- SWB
    // syntax would be: intent,<intent string>,<uri>,x,y,width,height
    if (onSide) {
      Point p=reversePoint(x,y);
      MLog.v(x+","+y+" goes to "+p.x+","+p.y);
      x=p.x;
      y=p.y;
    }
    int half=1280/2;
    int side=50;
    if (action==MotionEvent.ACTION_DOWN) {
      SlideAction act=ss.getActionAtXY(curSlide,x,y);
      if (x<side||(act==null&&x<=half&y>200)) {
        // previous
        if (curSlide>0) {
          int nxt=ss.nextNonHiddenSlide(curSlide,true);
          if (nxt!=curSlide) {
            curSlide=nxt;
            switchToSlide(false,curSlide,false,true);
          }
        }
      } else if (x>(1280-side)||(act==null&&x>half&y>200)) {
        // next
        if ((curSlide+1)<ss.getNumSlides()) {
          int nxt=ss.nextNonHiddenSlide(curSlide,false);
          if (nxt!=curSlide) {
            curSlide=nxt;
            switchToSlide(false,curSlide,false,false);
          }
        }
      } else if (y<side||(act==null&&y<200)) {
        // top
        
        Intent intent=new Intent(this,SlideGrid.class);
        try {
          startActivityForResult(intent,0);
        } catch (Exception e) {
          MLog.e("Error launching intent",e);
        }
      } else if (act instanceof SlideIntentAction) {
        SlideIntentAction sia=(SlideIntentAction)act;
        String intentStr=sia.getIntentString();
        Intent myIntent=null;
        if (intentStr.startsWith("com.sra.brieflet.SlideViewer")) {
          MLog.v("following slide link");
          myIntent=new Intent(this,SlideViewer.class);
          String uri=sia.getUriString();
          // form: slide://path#slide
          int pos=uri.lastIndexOf("#");
          String slideName=uri.substring(pos+1);
          String slidePath=uri.substring(8,pos);
          if (slidePath.equals(ss.getZipName())) {
            MLog.v("slide link is in this same presentation - taking shortcut");
            //use an intra presentation shortcut
            int num=ss.getSlideNumber(slideName);
            curSlide=num;
            switchToSlide(false,num,false,false);
            return;
          }
          File sFile=new File(slidePath);
          if (!sFile.exists()) {
            DialogUtil.infoDialog("Action Target Not Found","The presentation "+slidePath+" was not found.",null,this);
            return;
          }
          myIntent.putExtra("slidePath",slidePath);
          myIntent.putExtra("slideName",slideName);
        } else {
          myIntent=new Intent(intentStr,Uri.parse(sia.getUriString()));
        }
        try {
          startActivity(myIntent);
        } catch (Throwable e) {
          DialogUtil.infoDialog("Action Target Not Found","Unable to launch activity.",null,this);
          return;
        }
      } else if (act instanceof LaunchAction) {
        LaunchAction la=(LaunchAction)act;
        final String pkgstr=la.getPackageString();
        String clsstr=la.getClassString();
        Intent intent=new Intent(Intent.ACTION_MAIN);
        intent.addCategory(Intent.CATEGORY_LAUNCHER);
        intent.setComponent(new ComponentName(pkgstr,clsstr));
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
          startActivity(intent);
        } catch (Throwable e) {
          DialogUtil.guardAction("Unable to launch application "+clsstr+".  Would you like to find it in the market?",
              new Runnable() {

                public void run() {
                  Intent marketIntent=new Intent(Intent.ACTION_VIEW,Uri.parse("http://market.android.com/details?id="+pkgstr));
                  startActivity(marketIntent);
                }
              },this);
          return;
        }
      } else if (act instanceof SlideVideoAction) {
        startVideoAction((SlideVideoAction)act);
        /*
         * String filename=((SlideVideoAction)act).getFilename(); File vFile=new
         * File(filename); if (!vFile.exists()) { // later look into handling
         * streaming video
         * DialogUtil.infoDialog("Action Target Not Found","The video "
         * +filename+" was not found.",null,this); return; } Intent intent=new
         * Intent(this,VideoViewer.class);
         * intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
         * intent.putExtra("slidenum",curSlide);
         * intent.putExtra("action",act.getPropertyString()); // Put in Guard //
         * against video not // found try { startActivityForResult(intent,1); }
         * catch (Exception e) { MLog.e("Error launching video viewer",e); }
         */
      } else if (act instanceof SlideAudioAction) {
        startAudioAction((SlideAudioAction)act,true);
      }
    }
  }

  private void startVideoAction(SlideVideoAction sva) {
    String filename=((SlideVideoAction)sva).getFilename();
    File vFile=new File(filename);
    if (!vFile.exists()) { // later look into handling streaming video
      DialogUtil.infoDialog("Action Target Not Found","The video "+filename+" was not found.",null,this);
      return;
    }
    Intent intent=new Intent(this,VideoViewer.class);
    intent.addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
    intent.putExtra("slidenum",curSlide);
    intent.putExtra("showActions",showActions);
    intent.putExtra("action",sva.getPropertyString()); // Put in Guard
                                                       // against video not
                                                       // found
    try {
      startActivityForResult(intent,1);
    } catch (Exception e) {
      MLog.e("Error launching video viewer",e);
    }
  }

  private void handleAudioTransition() {
    BriefletMediaPlayer bmp=sl.getMP();
    if (bmp.isPlaying()) {
      bmp.stop();
    }
  }

  private void startAudioAction(SlideAudioAction saa,boolean manual) {
    int control=saa.getControl();
    if (control==SlideAudioAction.CONTROL_PLAY) { //happens to be 0 so works if not specified
      BriefletMediaPlayer bmp=sl.getMP();
      if (bmp.isPlaying()) {
        if (bmp.getLastPath()!=null && bmp.getLastPath().equals(saa.getUri())) {
          //we're already playing it, so toggle pause/play
          bmp.pause();
          return;
        }
        bmp.stop();
      } else if (bmp.getLastPath()!=null && bmp.getLastPath().equals(saa.getUri())) {
        if (bmp.isPaused()) {
          bmp.start();
          return;
        }
      }
      bmp.reset();
      try {
        bmp.setDataSource(ss.getZipName(),saa.getUri());
        bmp.setLooping(saa.isLoop());
        bmp.prepare();
        bmp.seekTo((int)saa.getStartMillis());
        MLog.v("vol="+saa.getVolume());
        if (saa.getVolume()>0) {
          bmp.setVolume(saa.getVolume(),saa.getVolume());
        }
        bmp.start();
        MLog.v("starting media player");
      } catch (Exception e) {
        MLog.e("Error with media player",e);
      }
    }
  }

  @Override
  public void colorChanged(int color) {
    mPaint.setColor(color);
  }

  @Override
  protected void onPause() {
    MLog.v("SlideViewer onPause");
    super.onPause();
    BriefletMediaPlayer bmp=sl.getMP();
    if (bmp.isPlaying()) {
      bmp.delayedStop();
    }
  }
  
  @Override
  public void onAnimationEnd(Animation animation) {
    onAnimationEnd(animation,false);
  }

  public void onAnimationEnd(Animation animation,boolean noauto) {
    // look for autoplay at this point
    List<SlideAction> actions=ss.getActions(curSlide);
    if (!noauto) {
    handleAudioTransition();
    boolean usedAudio=false;
    if (actions!=null) {
      for(SlideAction action:actions) {
        if (action instanceof SlideVideoAction) {
          final SlideVideoAction sva=(SlideVideoAction)action;
          // final SlideViewer sv=this;
          if (sva.isAutoStart()) {
            startVideoAction(sva); // this is now fixed by forcing first slide
                                   // to fade -- SWB
            /*
             * this gives time to cache first image prior to first slide video
             * playback but glitches screen (new Thread() { public void run() {
             * sv.runOnUiThread(new Runnable() { public void run() {
             * startVideoAction(sva); }}); }}).start();
             */
          }
        }
        if (action instanceof SlideAudioAction&&!usedAudio) {
          SlideAudioAction saa=(SlideAudioAction)action;
          if (saa.isAutoStart()) {
            startAudioAction(saa,false);
            usedAudio=true;
          }
        }
        // Todo: add clause to initiate audio action (either starting, or
        // control actions)
      }
    }
    }
    if (showActions) {
      MLog.v("animation ended "+animation);
      if (actions!=null) {
        for(SlideAction action:actions) {
          cv.addARect(action.getBounds());
        }
        cv.invalidate();
      }
    }
    cv.invalidate();
  }

  @Override
  public void onAnimationRepeat(Animation animation) {}

  @Override
  public void onAnimationStart(Animation animation) {}
  
  

  @Override
  protected void onStop() {
    super.onStop();
    MLog.v("SlideViewer onStop");
  }


  @Override
  protected void onRestart() {
    super.onRestart();
    MLog.v("SlideViewer onRestart");
    if (ss==null) MLog.v("ss=null");
  }

  @Override
  protected void onStart() {
    super.onStart();
    MLog.v("SlideViewer onStart");
    if (ss==null) MLog.v("ss=null");
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    MLog.v("SlideViewer onDestroy");
    if (ss==null) MLog.v("ss=null");
    if (sl!=null) {
      sl.clearCache();
    }
    if (cv!=null) {
      cv.shutdown();
    }
    if (sprite!=null)
      sprite.shutdown();
    /*
    if (tracker!=null)
      tracker.stop();*/
    MTracker.stop();
    if (sgl!=null) {
      sgl.shutdown();
    }
  }

}
