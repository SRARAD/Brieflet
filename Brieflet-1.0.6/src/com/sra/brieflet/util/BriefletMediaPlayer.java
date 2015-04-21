/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
package com.sra.brieflet.util;

import java.io.IOException;

import android.media.MediaPlayer;

public class BriefletMediaPlayer extends MediaPlayer {
  
  private String path;
  private String zipname;
  public boolean stoppingSoon=false;
  private boolean paused=false;

  @Override
  public void setDataSource(String path) throws IOException,IllegalArgumentException,IllegalStateException {
    super.setDataSource(path);
    this.path=path;
  }
  
  public void setDataSource(String zipname,String path) throws IOException,IllegalArgumentException,IllegalStateException {
    this.zipname=zipname;
    setDataSource(path);
  }
  
  public String getLastZipName() {
    return(zipname);
  }
  
  public String getLastPath() {
    return(path);
  }
  
  public boolean isPaused() {
    return(paused);
  }
  
  @Override
  public void pause() throws IllegalStateException {
    super.pause();
    MTracker.trackEvent("Music", "Pause", "Media PLayer", 2);
    paused=true;
  }

  @Override
  public void stop() throws IllegalStateException {
    super.stop();
    MTracker.trackEvent("Music", "Stop", "Media PLayer", 2);
    paused=false;
  }

  @Override
  public void start() throws IllegalStateException {
    super.start();
    MTracker.trackEvent("Music", "Play", "Media PLayer", 2);
    paused=false;
  }

  public void abortStop() {
    stoppingSoon=false;
  }
  
  public void delayedStop() {
    final BriefletMediaPlayer bmp=this;
    stoppingSoon=true;
    (new Thread() { public void run() {
      try { Thread.sleep(200); } catch (Exception e) {};
      if (bmp.stoppingSoon) {
        bmp.stop();
        bmp.stoppingSoon=false;
      }
    }}).start();
  }

}
