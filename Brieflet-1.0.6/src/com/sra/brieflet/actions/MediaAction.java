/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
package com.sra.brieflet.actions;


public interface MediaAction {

  public boolean isLoop();
  public void setLoop(boolean flag);
  public boolean isAutoStart();
  public void setAutoStart(boolean flag);
}
