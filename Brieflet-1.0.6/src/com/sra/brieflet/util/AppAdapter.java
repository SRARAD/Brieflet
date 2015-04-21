/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
package com.sra.brieflet.util;

import java.util.List;

import com.sra.brieflet.R;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

public class AppAdapter extends BaseAdapter {
	private Activity act;
	private List<App> apps;
	private static LayoutInflater inflater=null;
	
	public AppAdapter(Activity a, List<App> apps){
		this.act=a;
		this.apps=apps;
		AppAdapter.inflater=(LayoutInflater)act.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
	}
	@Override
	public int getCount() {
		return apps.size();
	}
	@Override
	public Object getItem(int arg0) {
		return arg0;
	}
	@Override
	public long getItemId(int position) {
		// TODO Auto-generated method stub
		return position;
	}
	
	public static class AppViewHolder{
		public TextView app_title;
		public ImageView app_image;
	}	
	
	public View getView(int position, View convertView, ViewGroup parent) {
		View vi=convertView;
	    AppViewHolder holder;
	    if (convertView==null) {

	      vi=inflater.inflate(R.layout.app_layout,null);

	      holder=new AppViewHolder();
	      holder.app_title=(TextView)vi.findViewById(R.id.app_text);
	      holder.app_image=(ImageView)vi.findViewById(R.id.app_image);

	      vi.setTag(holder);
	    } 
	    else
	      holder=(AppViewHolder)vi.getTag();
	    
	    holder.app_title.setText(apps.get(position).title);
	    holder.app_image.setImageDrawable(apps.get(position).icon);

	    return vi;
	}
	
}
