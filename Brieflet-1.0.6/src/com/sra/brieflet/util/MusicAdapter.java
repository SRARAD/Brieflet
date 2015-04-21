/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet.util;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.sra.brieflet.R;

public class MusicAdapter extends BaseAdapter {

  private Activity activity;
  private String[] titles;
  private String[] artists;
  private String[] albums;
  private static LayoutInflater inflater=null;

  public MusicAdapter(Activity a,String[] titles,String[] albums,String[] artists) {
    this.activity=a;
    this.titles=titles;
    this.albums=albums;
    this.artists=artists;
    MusicAdapter.inflater=(LayoutInflater)activity.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
  }

  public int getCount() {
    return titles.length;
  }

  public Object getItem(int position) {
    return position;
  }

  public long getItemId(int position) {
    return position;
  }

  public static class ViewHolder {

    public TextView title;
    public TextView album;
    public TextView artist;
  }

  public View getView(int position,View convertView,ViewGroup parent) {
    View vi=convertView;
    ViewHolder holder;
    if (convertView==null) {

      vi=inflater.inflate(R.layout.song_layout,null);

      holder=new ViewHolder();
      holder.title=(TextView)vi.findViewById(R.id.title);
      holder.album=(TextView)vi.findViewById(R.id.album);
      holder.artist=(TextView)vi.findViewById(R.id.artist);

      vi.setTag(holder);
    } else
      holder=(ViewHolder)vi.getTag();
    holder.title.setText(this.titles[position]);
    holder.album.setText(this.albums[position]);
    holder.artist.setText(this.artists[position]);
    return vi;
  }
}
