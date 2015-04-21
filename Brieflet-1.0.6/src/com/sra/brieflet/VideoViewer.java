/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet;

import java.util.List;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.Paint.Style;
import android.graphics.drawable.Drawable;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnBufferingUpdateListener;
import android.media.MediaPlayer.OnCompletionListener;
import android.media.MediaPlayer.OnPreparedListener;
import android.media.MediaPlayer.OnVideoSizeChangedListener;
import android.net.Uri;
import android.os.Bundle;
import android.view.Display;
import android.view.GestureDetector;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.ViewGroup.LayoutParams;

import com.sra.brieflet.actions.SlideAction;
import com.sra.brieflet.actions.SlideVideoAction;
import com.sra.brieflet.util.BriefletMediaPlayer;
import com.sra.brieflet.util.MLog;
import com.sra.brieflet.util.MTracker;

public class VideoViewer extends Activity /*implements View.OnTouchListener*/ {

  class CanvasView extends View {

    private Context context;
    private VideoSurfaceView vsv;

    public CanvasView(Context context,VideoSurfaceView vsv) {
      super(context);
      this.context=context;
      this.vsv=vsv;
    }
    
    public void toggleBlanking() {
      blanking=!blanking;
      invalidate();
    }
    
    public void shutdown() {
      context=null;
      vsv=null;
    }

    @Override
    protected void onSizeChanged(int w,int h,int oldw,int oldh) {
      super.onSizeChanged(w,h,oldw,oldh);
      heightScale=h;
      MLog.v("heightscale="+h);
      if (mediaPlayer!=null) {
        vsv.repositionVideo();
        invalidate();
      }
    }

    @Override
    protected void onDraw(Canvas canvas) {
      if (blanking) {
        canvas.drawColor(Color.BLACK);        
      } else {
        Rect r=scaleRect(new Rect(0,0,1280,SlideLoader.theight));
      MLog.v("VideoViewer canvas draw");
      //SlideLoader sl=SlideLoader.getDefaultInstance(context);
      // Log.v("SRA","SRA:drawing slide");
      Drawable slide=sl.getSlide(slidenum);
      slide.setBounds(r.left,r.top,r.right,r.bottom); // should be 720 or 752 depending
      Rect vr=scaleRect(vid.getBounds());
      MLog.v("slide dims="+slide.getIntrinsicWidth()+"x"+slide.getIntrinsicHeight());
      // Log.v("SRA","SRA:bounds="+slide.getBounds());
      // Log.v("SRA","SRA: x="+x+" y="+y+" w="+w+" h="+h);
      canvas.save();
      canvas.clipRect(r.left,r.top,r.right,vr.top); // top
      slide.draw(canvas);
      canvas.restore();
      canvas.save();
      canvas.clipRect(r.left,vr.top,vr.left,r.bottom); // left
      slide.draw(canvas);
      canvas.restore();
      canvas.save();
      canvas.clipRect(vr.right,vr.top,r.right,r.bottom); // right
      slide.draw(canvas);
      canvas.restore();
      canvas.save();
      canvas.clipRect(vr.left,vr.bottom,vr.right,r.bottom); // bottom
      slide.draw(canvas);
      canvas.restore();
      if (showActions) {
      Paint p=new Paint();
      p.setColor(Color.BLUE);
      p.setStrokeWidth(2);
      p.setStyle(Style.STROKE);
      for(SlideAction sa:ss.getActions(slidenum)) {
        canvas.drawRect(scaleRect(sa.getBounds()),p);
      }
      }
      // Log.v("SRA","SRA:slide="+slide);
      super.onDraw(canvas);
      }
    }
  }

  class VideoSurfaceView extends SurfaceView implements OnBufferingUpdateListener,OnCompletionListener,OnPreparedListener,
      OnVideoSizeChangedListener,SurfaceHolder.Callback {

    private SurfaceHolder mHolder;
    private int mVideoWidth;
    private int mVideoHeight;
    private boolean mIsVideoSizeKnown=false;
    private boolean mIsVideoReadyToBePlayed=false;

    VideoSurfaceView(Context context) {
      super(context);
      mHolder=getHolder();
      mHolder.addCallback(this);
      mHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
    }

    public void releaseMediaPlayer() {
      if (mediaPlayer!=null) {
        mediaPlayer.release();
        mediaPlayer=null;
      }
    }

    public void doCleanUp() {
      mVideoWidth=0;
      mVideoHeight=0;
      mIsVideoReadyToBePlayed=false;
      mIsVideoSizeKnown=false;
    }

    private void playVideo(String filename) {
      doCleanUp();
      try {
        // Create a new media player and set the listeners
        mediaPlayer=new MediaPlayer();
        mediaPlayer.setDataSource(filename);
        mediaPlayer.setDisplay(mHolder);
        mediaPlayer.prepare();
        mediaPlayer.seekTo((int)millis);
        if (vid.isLoop()) {
          mediaPlayer.setLooping(true);
        } else {
          mediaPlayer.setLooping(false);
        }
        mediaPlayer.setOnBufferingUpdateListener(this);
        mediaPlayer.setOnCompletionListener(this);
        mediaPlayer.setOnPreparedListener(this);
        mediaPlayer.setOnVideoSizeChangedListener(this);
        mediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
      } catch (Exception e) {
        // Log.e("sra","sra:Error setting up media player",e);
      }
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder,int format,int width,int height) {}

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
      playVideo(vid.getFilename());
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {}

    @Override
    public void onBufferingUpdate(MediaPlayer mp,int percent) {}

    @Override
    public void onCompletion(MediaPlayer mp) {}

    @Override
    public void onPrepared(MediaPlayer mp) {
      mIsVideoReadyToBePlayed=true;
      if (mIsVideoReadyToBePlayed&&mIsVideoSizeKnown) {
        startVideoPlayback();
      }
    }

    @Override
    public void onVideoSizeChanged(MediaPlayer mp,int width,int height) {
      mIsVideoSizeKnown=true;
      mVideoWidth=width;
      mVideoHeight=height;
      if (mIsVideoReadyToBePlayed&&mIsVideoSizeKnown) {
        startVideoPlayback();
      }
    }

    private void startVideoPlayback() {
      // some size that actually worked ;-)
      // holder.setFixedSize(640,512);
      // holder.setFixedSize(100,80);
      // mHolder.setFixedSize(160,128);
      // holder.setFixedSize(155,124);
      repositionVideo();
      MTracker.trackEvent("Video", "Play", "VideoViewer", 2);
      mediaPlayer.start();
    }
    
    public void repositionVideo() {
      Rect vr=scaleRect(vid.getBounds());
      //MLog.v("x="+vr.left+" y="+vr.top);
      //MLog.v("w="+vr.width()+" h="+vr.height());
      mHolder.setFixedSize(vr.width(),vr.height());
      setX(vr.left);
      setY(vr.top);
    }
  }
  
  
  class VideoGestureListener extends GestureDetector.SimpleOnGestureListener {
    
    private VideoViewer vv;

    public VideoGestureListener(VideoViewer vv) {
      this.vv=vv;
    }
    
    public void shutdown() {
      vv=null;
    }

    @Override
    public void onLongPress(MotionEvent event) {
      Intent intent=new Intent();
      intent.putExtra("x",(int)event.getX());
      intent.putExtra("y",(int)event.getY());
      intent.putExtra("com.sra.brieflet.motionEvent",event);
      setResult(10,intent);
      finish();
      overridePendingTransition(0,0); // pretty secret call here ;-)
      //super.onLongPress(e);
    }

    @Override
    public boolean onDoubleTap(MotionEvent event) {
      Intent intent=new Intent();
      intent.putExtra("x",(int)event.getX());
      intent.putExtra("y",(int)event.getY());
      intent.putExtra("com.sra.brieflet.motionEvent",event);
      setResult(11,intent);
      finish();
      overridePendingTransition(0,0); // pretty secret call here ;-)
      //return super.onDoubleTap(e);
      return(true);
    }

    @Override
    public boolean onSingleTapConfirmed(MotionEvent e) {
      MLog.v("video tap");
      return(handleSingleTouch(e));
      //return super.onSingleTapConfirmed(e);
    }

    @Override
    public boolean onFling(MotionEvent e1,MotionEvent e2,float velocityX,float velocityY) {
      //MLog.v("single touch called editActMode="+editActMode);
      Point p1= new Point((int)e1.getX(), (int)e1.getY());
      Point p2= new Point((int)e2.getX(), (int)e2.getY());
      if(Math.abs(p1.y-p2.y)<100){
          if(p1.x>p2.x){
            setResult(2);
            finish();
          }else{
            setResult(1);
            finish();
          }
          return(true);
      } else {
        return super.onFling(e1,e2,velocityX,velocityY);
      }
    }
    
  }

  private int curSlide=0;
  private MediaPlayer mediaPlayer;
  private VideoSurfaceView vsv;
  private CanvasView cv;
  private int slidenum;
  private long millis;
  private SlideVideoAction vid;
  private GestureDetector gd;
  private VideoGestureListener vgl;
  private boolean blanking=false;
  private int heightScale=752;
  private boolean onSide=false;
  private SlideLoader sl;
  private SlideSet ss;
  private boolean showActions=false;

  public VideoViewer() {}

  private void setVideoAction(int num,SlideAction sva) {
    if (sva!=null&&sva instanceof SlideVideoAction) {
      vid=(SlideVideoAction)sva;
    } else {
      List<SlideAction> actions=SlideLoader.getDefaultInstance(this).getCurrentSlideSet().getSlideActions(slidenum);
      // assume one video action for now
      for(SlideAction action:actions) {
        if (action instanceof SlideVideoAction) {
          vid=(SlideVideoAction)action;
          break;
        }
      }
    }
  }
  
  private void reportDisplay() {
    Display display=((WindowManager)getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
    switch (display.getRotation()) {
      case Surface.ROTATION_0:
        onSide=false;
        MLog.v("Display: ori=0");
        break;
      case Surface.ROTATION_90:
        onSide=true;
        MLog.v("Display: ori=90");
        break;
      case Surface.ROTATION_180:
        onSide=false;
        MLog.v("Display: ori=180");
        break;
      case Surface.ROTATION_270:
        onSide=true;
        MLog.v("Display: ori=270");
        break;
    }
  }
  
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

  private Point scalePoint(int x,int y) {
    int off=(int)(1232-(1.0*SlideLoader.theight*800/1280))/2;
    if (onSide) {
      return(new Point((int)(1.0*x/1280*800),(int)(off+y*800/1280)));
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

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    slidenum=intent.getExtras().getInt("slidenum");
    SlideAction sa=null;
    String actionstr=getIntent().getStringExtra("action");
    if (actionstr!=null)
      sa=SlideAction.createInstance(actionstr);
    setVideoAction(slidenum,sa);
  }

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    MTracker.initTracker(this);
    requestWindowFeature(Window.FEATURE_NO_TITLE);
    vsv=new VideoSurfaceView(this);
    slidenum=getIntent().getExtras().getInt("slidenum");
    showActions=getIntent().getExtras().getBoolean("showActions");
    sl=SlideLoader.getDefaultInstance(this);
    ss=SlideLoader.currentSlideSet;
    cv=new CanvasView(this,vsv);
    //cv.setOnTouchListener(this);
    cv.setFocusable(true);
    cv.setFocusableInTouchMode(true);
    vgl=new VideoGestureListener(this);
    gd=new GestureDetector(this,vgl);
    setContentView(vsv,new LayoutParams(LayoutParams.WRAP_CONTENT,LayoutParams.WRAP_CONTENT));
    addContentView(cv,new LayoutParams(LayoutParams.FILL_PARENT,LayoutParams.FILL_PARENT));
    
    SlideAction sa=null;
    String actionstr=getIntent().getStringExtra("action");
    if (actionstr!=null)
      sa=SlideAction.createInstance(actionstr);
    reportDisplay();
    setVideoAction(slidenum,sa);
    /*
    Rect vr=scaleRect(vid.getBounds());
    vsv.setX(vr.left);
    vsv.setY(vr.top);
    */
  }

  @Override
  public void onSaveInstanceState(Bundle savedInstanceState) {
    // Save UI state changes to the savedInstanceState.
    // This bundle will be passed to onCreate if the process is
    // killed and restarted.
    // super.onSaveInstanceState(savedInstanceState);
    savedInstanceState.putInt("slidenum",slidenum);
    super.onSaveInstanceState(savedInstanceState);
  }

  @Override
  protected void onPause() {
    super.onPause();
    vsv.releaseMediaPlayer();
    vsv.doCleanUp();
    //SlideLoader sl=SlideLoader.getDefaultInstance(this);
    BriefletMediaPlayer bmp=sl.getMP();
    if (bmp.isPlaying()) {
      bmp.delayedStop();
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    //SlideLoader sl=SlideLoader.getDefaultInstance(this);
    BriefletMediaPlayer bmp=sl.getMP();
    if (bmp.isPlaying()) {
      bmp.abortStop();
    }
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    vsv.releaseMediaPlayer();
    vsv.doCleanUp();
    MTracker.stop();
    if (cv!=null) cv.shutdown();
    if (vgl!=null) vgl.shutdown();
  }

  @Override
  public boolean onKeyDown(int keyCode,KeyEvent event) {
    MLog.v("keyCode="+keyCode);
    switch(keyCode) {
      case KeyEvent.KEYCODE_PAGE_UP:
      case KeyEvent.KEYCODE_DPAD_LEFT:
        //prev
        setResult(1);
        finish();
        return(true);
      case KeyEvent.KEYCODE_DPAD_UP:
        setResult(3);
        finish();
        return(true);
      case KeyEvent.KEYCODE_PAGE_DOWN:
      case KeyEvent.KEYCODE_DPAD_RIGHT:
        setResult(2);
        finish();
        //next
        return(true);
      case KeyEvent.KEYCODE_B:
        //blank
        if (mediaPlayer!=null) {
        if (blanking) {
          if (!mediaPlayer.isPlaying()) {
            MTracker.trackEvent("Video", "Play", "VideoViewer", 2);
            mediaPlayer.start();
          }
        } else {
          if (mediaPlayer.isPlaying()) {
            MTracker.trackEvent("Video", "Pause", "VideoViewer", 2);
            mediaPlayer.pause();
          }
        }
        }
        cv.toggleBlanking();
        return(true);
      case KeyEvent.KEYCODE_F5:
        //begin
        setResult(4);
        finish();
        return(true);
      //the next two are also send by the targus and will escape the view mode if we don't disable them
      case KeyEvent.KEYCODE_MEDIA_REWIND:
      case KeyEvent.KEYCODE_ESCAPE:
        return(true);
    }    
    return super.onKeyDown(keyCode,event);
  }

  @Override
  public boolean onTouchEvent(MotionEvent event) {
    // TODO Auto-generated method stub
    return(gd.onTouchEvent(event));
    //return super.onTouchEvent(event);
  }

  /*
  @Override
  public boolean onTouch(View v,MotionEvent event) {
    MLog.v("onTouch "+event.getAction());
    return(gd.onTouchEvent(event));
  }
  */
  
  private boolean handleSingleTouch(MotionEvent event) {
    int action=event.getAction();
    if (action==MotionEvent.ACTION_DOWN) {
      Rect bounds=vid.getBounds();
      int x=(int)event.getX();
      int y=(int)event.getY();
      int tx=x;
      int ty=y;
      if (onSide) {
        Point p=reversePoint(x,y);
        tx=p.x;
        ty=p.y;
      }
      if (bounds.contains(tx,ty) && (tx>100) && (tx<1180) && (ty>100)) {
        // Log.v("sra "+this.getClass().getName(), "sra:--large");
        Intent play=new Intent(Intent.ACTION_VIEW);
        Uri u=Uri.parse("file://"+vid.getFilename());
        play.setDataAndType(u,"video/*");
        try {
          startActivity(play);
        } catch (Exception e) {
          // Log.e("cg",e.toString());
        }
      } else {
        Intent intent=new Intent();
        intent.putExtra("x",(int)event.getX());
        intent.putExtra("y",(int)event.getY());
        setResult(9,intent);
        finish();
        overridePendingTransition(0,0); // pretty secret call here ;-)
      }
    }
    return true;
  }

}
