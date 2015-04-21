/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet.util;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.text.TextPaint;
import android.util.AttributeSet;
import android.widget.ProgressBar;

public class TextProgressBar extends ProgressBar {

  private String text;
  private Paint textPaint;

  public TextProgressBar(Context context) {
    super(context);
    textPaint=new TextPaint();
    textPaint.setColor(Color.BLACK);
  }

  public TextProgressBar(Context context,AttributeSet attrs) {
    super(context,attrs);
    textPaint=new TextPaint();
    textPaint.setColor(Color.BLACK);
  }

  public TextProgressBar(Context context,AttributeSet attrs,int defStyle) {
    super(context,attrs,defStyle);
    textPaint=new TextPaint();
    textPaint.setColor(Color.BLACK);
  }

  @Override
  protected synchronized void onDraw(Canvas canvas) {
    super.onDraw(canvas);
    if (text!=null) {
      Rect bounds=new Rect();
      textPaint.getTextBounds(text,0,text.length(),bounds);
      int x=getWidth()/2-bounds.centerX();
      int y=getHeight()/2-bounds.centerY();
      canvas.drawText(text,x,y,textPaint);
    }
  }

  public void setText(String text) {
    this.text=text;
    drawableStateChanged(); // hmm...didn't know about this
  }

}
