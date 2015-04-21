/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet.util;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.provider.MediaStore;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

import com.sra.brieflet.R;

public class MusicUtil {
    static Bitmap thumb;

  public static int[] getAllSongs(Context context) {
    Cursor c=query(context,MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[] {MediaStore.Audio.Media._ID,
        MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM},MediaStore.Audio.Media.IS_MUSIC
        +"=1",null,null);
    try {
      if (c==null||c.getCount()==0) {
        return null;
      }
      int len=c.getCount();
      int[] list=new int[len];
      for(int i=0;i<len;i++) {
        c.moveToNext();
        MLog.v("song="+c.getString(1)+" artist="+c.getString(2)+" album="+c.getString(3));
        list[i]=c.getInt(0);
      }

      return list;
    } finally {
      if (c!=null) {
        c.close();
      }
    }
  }

  public static Cursor query(Context context,Uri uri,String[] projection,String selection,String[] selectionArgs,String sortOrder) {
    try {
      ContentResolver resolver=context.getContentResolver();
      if (resolver==null) {
        return null;
      }
      return resolver.query(uri,projection,selection,selectionArgs,sortOrder);
    } catch (UnsupportedOperationException ex) {
      return null;
    }
  }
  
  public static Bitmap getThumb(Context context, String uri){
      Cursor c= query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Audio.Media.ALBUM_ID},MediaStore.Audio.Media.DATA+"=\""+uri +"\"",null,null);
      if(c==null||c.getCount()==0){
          return null;
      }
      try{
      c.moveToFirst();
      String albumId=c.getString(0);
      Uri sArtworkUri = Uri.parse("content://media/external/audio/albumart");
      Uri uri0 = ContentUris.withAppendedId(sArtworkUri, Long.parseLong(albumId));
      ContentResolver res = context.getContentResolver();
          try {
            InputStream in = res.openInputStream(uri0);
            Bitmap artwork = BitmapFactory.decodeStream(in);
            return artwork;
          } catch (FileNotFoundException e) {
            MLog.v("Could not find album art");
            //if(useDefault)
            //    return getDefaultArtwork(context,uri);
            return null;
          } 
      }finally {
          if (c!=null) {
              c.close();
            }
      }
      
  }
  
  private static void adjustTextSize(String text,TextPaint tp,float height) {
  tp.setTextSize(100);
  tp.setTextScaleX(1.0f);
  Rect bounds = new Rect();
  tp.getTextBounds(text,0,text.length(),bounds);
  int h = bounds.bottom - bounds.top;
  float size=((height/h)*100f);
  tp.setTextSize(size);
  }
  
  private static void adjustTextScale(String text,TextPaint tp,float width,float height) {
    tp.setTextScaleX(1.0f);
    Rect bounds = new Rect();
    tp.getTextBounds(text,0,text.length(),bounds);
     int w = bounds.right - bounds.left;
    int text_h = bounds.bottom-bounds.top;
    //mTextBaseline=bounds.bottom+((height-text_h)/2);
    float xscale = (0.9f*width/w);
    tp.setTextScaleX(xscale);
  }
  
  
  public static void drawDefaultArtwork(Context context, Canvas canvas, Rect rect, String uri){
      BitmapFactory.Options opts = new BitmapFactory.Options();
      opts.inPreferredConfig = Bitmap.Config.ARGB_8888;
      if(thumb==null){
          thumb= BitmapFactory.decodeStream(context.getResources().openRawResource(R.drawable.albumart_mp_unknown), null, opts);
          thumb=thumb.copy(opts.inPreferredConfig, true);
      }
        
      Cursor c= query(context, MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[]{MediaStore.Audio.Media.ARTIST, MediaStore.Audio.Media.ALBUM},
              MediaStore.Audio.Media.DATA+"=\""+uri +"\"",null,null);
      try{
      c.moveToFirst();  
      TextPaint p = new TextPaint();
      p.setColor(Color.WHITE);
      String artist= c.getString(0);
      String album=c.getString(1);
      /*
      int font_size=0;
      if(artist.length()>album.length()){
          font_size=rect.width()/artist.length();
      }else{
          font_size=rect.width()/album.length();
      }
      p.setTextSize(font_size);
      */
      p.setTextAlign(Align.CENTER);
      p.setTypeface(Typeface.DEFAULT_BOLD);
      p.setAntiAlias(true);
      canvas.drawBitmap(thumb,null,rect,new Paint());
      float th=0.2f*rect.height();
      adjustTextSize(artist,p,th);
      adjustTextScale(artist,p,rect.width(),th);
      canvas.drawText(artist, rect.centerX(), rect.top-p.ascent(), p);
      adjustTextSize(album,p,th);
      adjustTextScale(album,p,rect.width(),th);
      canvas.drawText(album, rect.centerX(), rect.bottom-p.descent(), p);
      /* this does nice wrapping but alas this is not what we want here, we do want scaling
      TextPaint tp=new TextPaint();
      tp.setColor(Color.WHITE);
      tp.setTextSize(16);
      //tp.setTextAlign(Align.CENTER);
      tp.setTypeface(Typeface.DEFAULT_BOLD);
      tp.setAntiAlias(true);
      canvas.save();
      canvas.translate(rect.left,rect.top);
      StaticLayout sltop = new StaticLayout(artist,tp,rect.width(),Layout.Alignment.ALIGN_CENTER,1f,0f,true);
      sltop.draw(canvas);
      canvas.restore();
      canvas.save();
      canvas.translate(rect.left,rect.bottom-tp.descent());
      StaticLayout slbot = new StaticLayout(album,tp,rect.width(),Layout.Alignment.ALIGN_CENTER,1f,0f,true);
      slbot.draw(canvas);
      canvas.restore();
      */
      }finally {
          if (c!=null) {
              c.close();
            }
      }
  }

  public static void chooseSong(Activity act,final StringResultListener listener) {
    List<String> titlesL=new ArrayList<String>();
    List<String> albumsL=new ArrayList<String>();
    List<String> artistsL=new ArrayList<String>();
    List<String> pathsL=new ArrayList<String>();
    //List<String> albumIdsL=new ArrayList<String>();
    boolean hit=false;
    Cursor c=query(act,MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,new String[] {
        MediaStore.Audio.Media.TITLE,MediaStore.Audio.Media.ARTIST,MediaStore.Audio.Media.ALBUM,MediaStore.Audio.Media.DATA},
        MediaStore.Audio.Media.IS_MUSIC+"=1",null,MediaStore.Audio.Media.TITLE);
    try {
      if (c==null||c.getCount()==0) {
        listener.setResult(null);
      }
      int len=c.getCount();
      for(int i=0;i<len;i++) {
        c.moveToNext();
        String title=c.getString(0);
        String artist=c.getString(1);
        int pos=titlesL.indexOf(title);
        if (pos==-1 || !artistsL.get(pos).equals(artist)) {
          titlesL.add(title);
          artistsL.add(artist);
          albumsL.add(c.getString(2));
          pathsL.add(c.getString(3));
          //albumIdsL.add(c.getString(5));
        }
      }
    } finally {
      if (c!=null) {
        c.close();
      }
    }
    int len=titlesL.size();
    String[] titles=new String[len];
    String[] artists=new String[len];
    String[] albums=new String[len];
    final String[] paths=new String[len];
    for(int i=0;i<len;i++) {
      titles[i]=titlesL.get(i);
      artists[i]=artistsL.get(i);
      albums[i]=albumsL.get(i);
      paths[i]=pathsL.get(i);
    }
    LayoutInflater inflater = (LayoutInflater)act.getSystemService
    (Context.LAYOUT_INFLATER_SERVICE);
    View view = (View) inflater.inflate(R.layout.listview,(ViewGroup) act.findViewById(R.id.songlist));
    MusicAdapter musicAdapter=new MusicAdapter(act,titles,albums,artists);
    ListView lv=(ListView)view.findViewById(R.id.songlist);
    lv.setAdapter(musicAdapter);    
    AlertDialog.Builder builder = new AlertDialog.Builder(act);
    builder.setTitle("Choose A Song");
    builder.setView(view);
    final AlertDialog alertDialog = builder.create();
    lv.setOnItemClickListener(new OnItemClickListener() {
      @Override
      public void onItemClick(AdapterView<?> arg0,View arg1,int arg2,long arg3) {
        listener.setResult(paths[arg2]);
        alertDialog.dismiss();
      }
    });
    alertDialog.show();
  }

}
