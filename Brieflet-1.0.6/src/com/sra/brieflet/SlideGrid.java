/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemSelectedListener;

import com.sra.brieflet.util.ClipboardUtil;
import com.sra.brieflet.util.DialogUtil;
import com.sra.brieflet.util.MLog;
import com.sra.brieflet.util.MTracker;
import com.sra.brieflet.util.StringResultListener;

public class SlideGrid extends Activity implements MultiChoiceModeListener {

  //private Bitmap[] bitmaps;
  private Context mContext;
  private GridView gv;
  private SlideLoader sl;
  private Spinner spinner;
  final String[] items = new String[] {"One Item Selected", "Select All", "Select None"};
  ArrayAdapter<String> adapter;
  AdapterContextMenuInfo lastInfo=null;
  List<Integer> selected= new ArrayList<Integer>();
  //boolean removed=false;
  boolean maskSelection=false;
  boolean first=false;
  boolean showTransitions=false;
  String[] transitionNames;
  List<String> transitionValues;
  //GoogleAnalyticsTracker tracker;
  
  public SlideGrid() {
    sl=SlideLoader.getDefaultInstance(this);  
  }

  @Override
  protected void onDestroy() {
    super.onDestroy();
    mContext=null;
    MTracker.stop();
    /*
    if (tracker!=null)
       tracker.stop();*/
  }

  @Override
  protected void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    //bitmaps were too big for using Parcels
    // Parcelable[] p=intent.getExtras().getParcelableArray("com.sra.cgm.thumbs");
    // bitmaps=new Bitmap[p.length];
    // System.arraycopy(p,0,bitmaps,0,p.length);
    //bitmaps=SlideLoader.getLastThumbs();
  }
  
  @Override
  public void onCreateContextMenu(ContextMenu menu,View v,ContextMenuInfo menuInfo) {
    
    AdapterContextMenuInfo info=(AdapterContextMenuInfo)menuInfo;
    lastInfo=info;
    SlideSet ss=sl.getCurrentSlideSet();
    boolean hidden=ss.isHidden(info.position);
    menu.setHeaderTitle("Options");
    //SubMenu actions=menu.addSubMenu("Actions");

    if (hidden) {
       menu.add(0,8,0,"Unhide Slide");
     } else {
        menu.add(0,8,0,"Hide Slide");
     }
     if (ss.getNumSlides()>1) {
          menu.add(0,2,0,"Cut Slide");
     }
     menu.add(0,3,0,"Copy Slide");
     if (ClipboardUtil.isSlideOnClipboard(this)) {
        menu.add(0,5,0,"Paste Before"); //only enabled if image to paste
        menu.add(0,6,0,"Paste After"); //only enabled if image to paste
     }
     if (ss.getNumSlides()>1) {
        menu.add(0,1,0,"Delete Slide");
     }
     //SubMenu slideLink=menu.addSubMenu("Slide Links");
     menu.add(1,4,0,"Copy As Slide Link Action");
     /*
     if (ClipboardUtil.isSlideActionOnClipboard(this)) {
        menu.add(1,7,0,"Paste Action"); //only enabled if intent to paste
     }
     */
     menu.add(1,9,0,"Set Slide As Template");
     if (info.position>0) {
       menu.add(1,10,0,"Set Slide Transition");
     }
  }
  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
	  
    MenuInflater inflater=getMenuInflater();
    inflater.inflate(R.menu.grid_menu,menu);
    if(showTransitions){
    	
    	menu.getItem(0).setTitle("Hide Transitions");
    }
    else{
    	
    	menu.getItem(0).setTitle("Show Transitions");
    }
    	
    	
    return(true);
  }
  /*
  @Override
  public boolean onPrepareOptionsMenu(Menu menu){
	  MLog.v("onPreparecalled");
	  
	  if(showTransitions){
		  	MLog.v("transitions are displayed");
	    	menu.getItem(0).setTitle("Hide Transitions");
	  }
	    else{
	    	MLog.v("transitions are hidden");
	    	menu.getItem(0).setTitle("Show Transitions");
	    }
	    	
	  
	  return super.onPrepareOptionsMenu(menu);
  }*/

  
  @Override
  protected void onPause() {
    super.onPause();
    if (sl!=null) {
      sl.activeGrid=null;
    }
  }

  @Override
  protected void onResume() {
    super.onResume();
    if (sl!=null) {
      sl.activeGrid=this;
    }
    MTracker.trackPageView("/"+this.getLocalClassName());
    /*
    if (tracker!=null) {
      tracker.trackPageView("/"+this.getLocalClassName());
    } else {
      MLog.v("tracker was null in onResume"); // thought it was better to flag this since it happens often apparently -- SWB
    }*/
  }
  
  /*
  @Override
  public boolean onContextItemSelected(final MenuItem item) {
    try{
     AdapterContextMenuInfo info=(AdapterContextMenuInfo)item.getMenuInfo();
    final SlideLoader sl=SlideLoader.getDefaultInstance(this);
    final SlideSet ss=sl.getCurrentSlideSet();
    if(info==null) // info is always null on submenu therefore we need to persist it's value
       info=lastInfo;
    final String slideName=ss.getSlideName(info.position);
    final SlideGrid sg=this;
    boolean hidden=ss.isHidden(slideName);
    MLog.v("item="+item.getItemId()+" info.position="+info.position);
    if (item.getItemId()==8) {
      if (hidden) {
        ss.setHidden(gv,slideName,false);
        MTracker.trackEvent("Slide Options", "Unhide","SlideGrid",  2); // Value
      } else {
        ss.setHidden(gv,slideName,true);
        MTracker.trackEvent("Slide Options", "Hide","SlideGrid",  2); // Value
      }
      gv.invalidateViews();
      //showBusyCursor("Working..","Please wait...",null );
    } else if (item.getItemId()==1) {
      int pos1=info.position+1;
      DialogUtil.guardAction("Are you sure you want to delete slide "+pos1+"?  This operation cannot be undone.",new Runnable() { public void run() {
        ss.deleteSlide(gv,slideName,new Runnable() { public void run() { sg.postRefreshView(); }});
      }},this);
      MTracker.trackEvent("Slide Options", "Delete","SlideGrid",  2); // Value
    } else if (item.getItemId()==2) { //cut slide
      try {
        ClipboardUtil.copySlideToClipboard(ss,info.position,this);
        ss.deleteSlide(gv,slideName,new Runnable() { public void run() { sg.postRefreshView(); }});
      } catch (Exception e) {
        MLog.e("Error during copy of slide",e);
        Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide to clipboard.",Toast.LENGTH_SHORT).show();
      }
      MTracker.trackEvent("Slide Options", "Cut","SlideGrid",  2); // Value
    } else if (item.getItemId()==3) { //copy slide
      try {
        ClipboardUtil.copySlideToClipboard(ss,info.position,this);
      } catch (Exception e) {
        MLog.e("Error during copy of slide",e);
        Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide to clipboard.",Toast.LENGTH_SHORT).show();
      }
      MTracker.trackEvent("Slide Options", "Copy","SlideGrid",  2); // Value
    } else if (item.getItemId()==4) { //copy slide link action
      try {
        ClipboardUtil.copySlideLinkToClipboard(ss,info.position,this);
      } catch (Exception e) {
        MLog.e("Error during copy of slide link action",e);
        Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide link action to clipboard.",Toast.LENGTH_SHORT).show();
      }
      MTracker.trackEvent("Slide Options", "CopySlideLinkAction","SlideGrid",  2); // Value
    } else if (item.getItemId()==5) { //paste before
      try {
        SlideInfo si=ClipboardUtil.getSlideFromClipboard(this);
        ss.insert(gv,info.position,si,true,new Runnable() { public void run() { sg.postRefreshView(); }});
      } catch (Exception e) {
        MLog.e("Error in paste operation",e);
        Toast.makeText(getApplicationContext(),"Sorry, an error was encountered during the paste operation.",Toast.LENGTH_SHORT).show();
      }
      MTracker.trackEvent("Slide Options", "Paste_before","SlideGrid",  2); // Value
    } else if (item.getItemId()==6) { //paste after
      try {
        SlideInfo si=ClipboardUtil.getSlideFromClipboard(this);
        ss.insert(gv,info.position,si,false,new Runnable() { public void run() { sg.postRefreshView(); }});
      } catch (Exception e) {
        MLog.e("Error in paste operation",e);
        Toast.makeText(getApplicationContext(),"Sorry, an error was encountered during the paste operation.",Toast.LENGTH_SHORT).show();
      }
      MTracker.trackEvent("Slide Options", "Paste_after","SlideGrid",  2); // Value
      /*
    } else if (item.getItemId()==7) { //paste action
      try {
        Intent intent=ClipboardUtil.getSlideActionFromClipboard(this);
        ss.insertAction(gv,intent,info.position,new Runnable() { public void run() { sg.postRefreshView(); }});
      } catch (Exception e) {
        MLog.e("Error in paste action operation",e);
        Toast.makeText(getApplicationContext(),"Sorry, an error was encountered during the paste action operation.",Toast.LENGTH_SHORT).show();
      }
      tracker.trackEvent("Actions", "Paste","SlideGrid",  1); // Value
      /
    } else if (item.getItemId()==9) { //save as template
      sl.copyOutSlide(info.position,"/sdcard/Slides/template.png");
      MTracker.trackEvent("Slide Options", "Save_template","SlideGrid",  2); // Value
    } else if (item.getItemId()==10) { //set slide transition
    	MTracker.trackEvent("Slide Options", "Set Slide Transition","SlideGrid",  2);
      DialogUtil.chooseArrayItem(this,"Choose Transition",R.array.TransitionNames,R.array.TransitionValues,new StringResultListener() {
        public void setResult(String str) {
          ss.setTransition(gv,slideName,str);
          MTracker.trackEvent("Choose Transition", str,"SlideGrid",  2);
        }
      });
    }
    }catch(Exception e){MLog.e("ContextItemSelected "+e.toString(), e);}
    //return super.onCreateOptionsMenu(item);
    return(true);
    
  }
  */
  
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch(item.getItemId()) {
      case android.R.id.home:
        Intent intent=new Intent(this,Brieflet.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        try {
          startActivity(intent);
        } catch (Exception e) {
          MLog.e("Error returning to Brieflet home",e);
        }
        return true;
        
      case R.id.show_transitions:
    	  if(showTransitions){
    		  showTransitions=false;
    		  item.setTitle("Show Transitions");
    	  }else{  
    		  showTransitions=true;
    		  item.setTitle("Hide Transitions");
    	  }
    	  postRefreshView();
    	  return true;
    	  
      default:
    	   return super.onOptionsItemSelected(item);
       
    }
    
  }

  public void postRefreshView() {
    gv.post(new Runnable() { public void run() { gv.invalidateViews(); }});
  }
  
  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    MTracker.initTracker(this);
    sl=SlideLoader.getDefaultInstance(this);
    setContentView(R.layout.grid);
    GridView g=(GridView)findViewById(R.id.myGrid);
    gv=g;
    int down=sl.getDownSampleSize();
    int w=(int)(1280/down);
    g.setColumnWidth(w+5);
    //bitmaps were too big for using Parcels
    // Parcelable[] p=getIntent().getExtras().getParcelableArray("com.sra.cgm.thumbs");
    // bitmaps=new Bitmap[p.length];
    // System.arraycopy(p,0,bitmaps,0,p.length);
    //bitmaps=SlideLoader.getLastThumbs();
    g.setAdapter(new ImageAdapter(this));
    setResult(-1);
    //registerForContextMenu(g);
    g.setChoiceMode(GridView.CHOICE_MODE_MULTIPLE_MODAL);
    g.setMultiChoiceModeListener(this);
    
    g.setOnItemClickListener(new OnItemClickListener() {

      public void onItemClick(AdapterView<?> parent,View v,int position,long id) {
        setResult(position);
        finish();
        
      }
    });
    
    /*
    tracker=GoogleAnalyticsTracker.getInstance();
    // Start the tracker in manual dispatch mode...
    tracker.start("UA-23457289-1",20,this);*/
    // tracker.setDebug(true); // put some debug messages in the log
    // tracker.setDryRun(true); // won't send the data to google analytics
  }

  public class ImageAdapter extends BaseAdapter {

    public ImageAdapter(Context c) {
      mContext=c;
      //setContentView(android.R.layout.simple_list_item_activated_1);
    }
    @Override
    public int getCount() {
      return(sl.getCurrentSlideSet().getNumSlides());
    }
    @Override
    public Object getItem(int position) {
      return position;
    }
    @Override
    public long getItemId(int position) {
      return position;
    }
    @Override
    public int getViewTypeCount(){
        return 1;
    }
    @Override
    public int getItemViewType(int pos){
        return 0;
    }
    @Override
    public View getView(int position,View convertView,ViewGroup parent) {
      View toReturn=null;
      ImageView imageView=null;
      TextView transition=null;

      if (convertView==null) {
        //imageView=new ImageView(mContext);
    	LayoutInflater inflater= getLayoutInflater();
    	toReturn= inflater.inflate(R.layout.slide_image,null);
    	imageView=(ImageView)toReturn.findViewById(R.id.myImage);
        transition=(TextView) toReturn.findViewById(R.id.transition_text);
        imageView.setAdjustViewBounds(false);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        //imageView.setPadding(5,5,5,5);

      } else {
        //imageView=(ImageView)convertView;
    	 toReturn=convertView;
    	 imageView=(ImageView)toReturn.findViewById(R.id.myImage);
         transition=(TextView) toReturn.findViewById(R.id.transition_text); 
      }
      /**do every time you're returning a view"**/
      SlideLoader sl=SlideLoader.getDefaultInstance(parent.getContext());
      SlideSet ss=sl.getCurrentSlideSet();
      int down=sl.getDownSampleSize();
      int w=(int)(1280/down)+5;
      int h=SlideLoader.theight/down+5;
      imageView.setLayoutParams(new FrameLayout.LayoutParams(w,h)); //was 160,100
      imageView.setImageBitmap(sl.getCurrentSlideSet().getBitmap(position));
      
      if(position==0){
          if(maskSelection)
              imageView.setBackgroundColor(android.R.color.transparent);
          else
              imageView.setBackgroundResource(R.drawable.selector);
      }else{
          imageView.setBackgroundResource(R.drawable.selector);
      }
      

      if(transition!=null){
    	  if(showTransitions){
    	      String prettyName= transitionNameToPrettyName(ss.getTransitionName(SlideGrid.this, position));
    		  transition.setText(prettyName);
    		  transition.setBackgroundResource(R.color.brieflet_blue);
    		  transition.setTextColor(Color.WHITE);
    	  }else{
    		  transition.setText("");
    	  }
    	  //transition.setLayoutParams(new FrameLayout.LayoutParams(w,h));
      }
    	  
      if (ss.isHidden(position)) {
        imageView.setColorFilter(Color.argb(200,0,0,0));
      } else {
        imageView.setColorFilter(null);
      }

      return toReturn;
    }

  }

private String transitionNameToPrettyName(String value){
    if(transitionValues== null)
        transitionValues= Arrays.asList(getResources().getStringArray(R.array.TransitionValues));
    if(transitionNames== null)
        transitionNames= getResources().getStringArray(R.array.TransitionNames);
    
    int index= transitionValues.indexOf(value);
    
    if(index>-1)
        return transitionNames[index];
    else
        return"";
    
    
}
private void clearMenu(Menu menu){
    for(int x=0; x< menu.size(); x++){
    	if(menu.getItem(x)!=menu.findItem(R.id.grid_menu_show_transition))
        menu.getItem(x).setVisible(false);
    }
}
@Override
public void onItemCheckedStateChanged(ActionMode mode, int position, long id, boolean checked) {
    SlideLoader sl=SlideLoader.getDefaultInstance(this);
    SlideSet ss=sl.getCurrentSlideSet();
    int checkedCount =gv.getCheckedItemCount();

    if(maskSelection){
    	if(position==0){
    		if(first){
    		    MLog.v("clearing the menu and returning");
    		    clearMenu(mode.getMenu());
    		    //mode.getMenu().clear();
    			//remove slide at position zero from selected
        		int index = selected.indexOf(position);
    			if(index>=0){
    				selected.remove(index);
    			}
        		first=false;
        		setTitle(mode);
        		return;
    		}else{
    			buildMenu(mode.getMenu());
    			maskSelection=false;

    			if(!checked){
    			    /**manual select**/
    	    		gv.setItemChecked(0, true);
    	    		return;
    			}
    		}
    	}else{
    		if(checked){
    			buildMenu(mode.getMenu());
    			maskSelection=false;
    			gv.setItemChecked(0, false);
    		}else{
    		    return;
    		}
    	}
    }
    		
    boolean all_hidden=true;
    
        if(checked){
            MLog.v("item checked!");
    			if(!selected.contains(position)){
    				selected.add(position);
    			}
    		}else{
    			int index = selected.indexOf(position);
    			if(index>=0){
    				selected.remove(index);
    			}
    		}
    		for(Integer i : selected){
    		    String slideName=ss.getSlideName(i);
    		    if(!ss.isHidden(slideName)){
    		        all_hidden=false;
    		        break;
    		    }
    		}
    		if(all_hidden){
    		    MLog.v("Change menu to unhide");
    		    mode.getMenu().getItem(0).setTitle("Unhide");
    		}else{
    		    mode.getMenu().getItem(0).setTitle("Hide");
    		}
    		
    		int last= mode.getMenu().size()-1;
    		if(checkedCount>1){
    		    
    			mode.getMenu().getItem(last).setVisible(false);
    			//removed=true;
    			//set copy slide link to "" links
    			mode.getMenu().getItem(last-1).setTitle("Copy Slide Links");
    			
    		}else{
    		    
    		    mode.getMenu().getItem(last).setVisible(true);
    			/*
    			if(removed){
    				int last=mode.getMenu().size()-1;
    				mode.getMenu().add(R.menu.grid_selection_menu, R.id.grid_menu_template, last, "Save Template");
    				removed=false;
    			
    			}*/
    			//int pos=mode.getMenu().size()-2;
    				mode.getMenu().getItem(last-1).setTitle("Copy Slide Link");
    		}
    		
    		
    		
    		//gv.invalidateViews();
   
	setTitle(mode);
	
    
    
    if(checkedCount==0){
        maskSelection=true;
        first=true;
        MLog.v("Turning on flags and setting 0 to true");
        gv.setItemChecked(0, true);  
    }
	
	
}
private String[] getSelectedSlideNames(SlideSet ss){

    String[] slideNames= new String[selected.size()];
    int x=0;
    for(Integer i : selected){
        slideNames[x]=(ss.getSlideName(i));
        if(slideNames[x]==null){
        	MLog.e("Got a null slide name in SldeSet: " + ss.getName()+ " at position: " +i, null);
        }
        x++;
    }
    return slideNames;
}

private List<Integer> getSortedCopy(){
    List<Integer> cpy= new ArrayList<Integer>();
    cpy.addAll(selected);
    Collections.sort(cpy);
    Collections.reverse(cpy);
    return cpy;
}

@Override
public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
    SlideLoader sl=SlideLoader.getDefaultInstance(this);
    boolean finish=false;
    final SlideSet ss=sl.getCurrentSlideSet();
    final String[] slideNames= getSelectedSlideNames(ss);
    List<Integer> sorted_copy=getSortedCopy();

    switch (item.getItemId()) {
    case R.id.grid_menu_hide:
    	boolean hide=true;
        for(String slideName: slideNames){
            if(item.getTitle().equals("Hide")){
            	hide=true;
                if(!ss.isHidden(slideName)){
                    //hide it
                    ss.setHiddenNoSave(gv,slideName, true);
                    MTracker.trackEvent("Slide Options", "Hide","SlideGrid",  2); // Value
                }
            }else{
            	hide=false;
                if(ss.isHidden(slideName)){
                    //show it
                    ss.setHiddenNoSave(gv,slideName, false);
                    MTracker.trackEvent("Slide Options", "Unhide","SlideGrid",  2); // Value
                    
                }
            }
        }
        deselectAll();
        ss.save(gv);
        String newTitle=hide?"Unhide":"Hide";
        item.setTitle(newTitle);
        postRefreshView();
        //ss.save(gv,ss.difference(selected),null);
       
        break;
    
    case R.id.grid_menu_delete:
        final String s = (selected.size()>1)?"s ":" ";
        String pos="";

        for(int x=0; x< selected.size(); x++){
            if(x==selected.size()-1)
                pos+=selected.get(x)+"";
            else
                pos+=selected.get(x)+", ";
        }
        
        DialogUtil.guardAction("Are you sure you want to delete slide"+s+"at position"+s+pos+"? This operation cannot be undone.",new Runnable() { public void run() {
            Set<String> skiplist= new HashSet<String>();
            boolean deleteAll=false;
            if(selected.size()==ss.getNumSlides()){
            	deleteAll=true;
            }
            deselectAll();
            for(int x=0; x<slideNames.length;x++){
            	String slideName= slideNames[x];
            	if(deleteAll&&(x==slideNames.length-1)){
            		
            		AlertDialog.Builder builder=new AlertDialog.Builder(SlideGrid.this);
                    builder.setMessage("Presentations must contain at least one Slide." +
                    		" To remove an entire presentation use the Delete option in Slide List.");
                    builder.setNeutralButton("Ok",new DialogInterface.OnClickListener() {

                      public void onClick(DialogInterface dialog,int id) {
                        // ok was clicked
                      }
                    });
                    builder.show();
                    break;
            		
            	}else{
            		skiplist.add(slideName);
                	ss.deleteSlideNoSave(slideName);
            	}
            }
            
            ss.saveRemove(gv,skiplist,new Runnable() { public void run() { SlideGrid.this.postRefreshView(); }});
            MTracker.trackEvent("Slide Options", "Delete","SlideGrid",  2); // Value
        }},this);
        break;
        
    case R.id.grid_menu_cut:
        try {
            if(selected.size()==ss.getNumSlides()){
                AlertDialog.Builder builder=new AlertDialog.Builder(SlideGrid.this);
                builder.setMessage("Presentations must contain at least one Slide.");
                builder.setNeutralButton("Ok",new DialogInterface.OnClickListener() {

                  public void onClick(DialogInterface dialog,int id) {
                    // ok was clicked
                  }
                });
                builder.show();
                break;
            }else{
              //MLog.v("PASSING IN: "+sorted_copy);
                //Toast.makeText(getApplicationContext(),"Passing in: "+sorted_copy,Toast.LENGTH_SHORT).show();
                ClipboardUtil.copySlidesToClipboard(ss,sorted_copy,this);
                deselectAll();
                //ss.deleteSlide(gv,slideName,new Runnable() { public void run() { sg.postRefreshView(); }});
                Set<String> skiplist= new HashSet<String>();
                for(String slideName: slideNames){
                    skiplist.add(slideName);
                    ss.deleteSlideNoSave(slideName);   
                }
                
                ss.saveRemove(gv,skiplist,new Runnable() { public void run() { SlideGrid.this.postRefreshView(); }});
                MTracker.trackEvent("Slide Options", "Cut","SlideGrid",  2); // Value
                //if(mode.getMenu().findItem(R.id.grid_menu_paste_after)==null){
                //  finish=true;
                    
                    //MLog.v("Cant find paste, adding back in!!");
                    /*
                  mode.getMenu().add(R.menu.grid_selection_menu, R.id.grid_menu_paste_before, 3, "Paste Before");
                  mode.getMenu().add(R.menu.grid_selection_menu, R.id.grid_menu_paste_after, 4, "Paste After");*/
                //} 
            }
          } catch (Exception e) {
            MLog.e("Error during copy of slide",e);
            Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide to clipboard.",Toast.LENGTH_SHORT).show();
          }
          
          break;
    case R.id.grid_menu_copy:
        try{
            //MLog.v("PASSING IN: "+sorted_copy);
            //Toast.makeText(getApplicationContext(),"Passing in: "+sorted_copy,Toast.LENGTH_SHORT).show();
            ClipboardUtil.copySlidesToClipboard(ss,sorted_copy,this);
            MTracker.trackEvent("Slide Options", "Copy","SlideGrid",  2); // Value
            deselectAll();
            //if(mode.getMenu().findItem(R.id.grid_menu_paste_after)==null){
            //finish=true;
            	/*
          	  mode.getMenu().add(R.menu.grid_selection_menu, R.id.grid_menu_paste_before, 3, "Paste Before");
          	  mode.getMenu().add(R.menu.grid_selection_menu, R.id.grid_menu_paste_after, 4, "Paste After");*/
            //}
        }catch(Exception e){
            MLog.e("Error during copy of slide",e);
            Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide to clipboard.",Toast.LENGTH_SHORT).show();
        }
        
        break;
        
    case R.id.grid_menu_link:
        try {
            ClipboardUtil.copySlideLinksToClipboard(ss,sorted_copy,this);
            deselectAll();
            MTracker.trackEvent("Slide Options", "CopySlideLinkAction","SlideGrid",  2); // Value
        }catch (Exception e) {
            MLog.e("Error during copy of slide link action",e);
            Toast.makeText(getApplicationContext(),"Sorry, problem encountered while copying slide link action to clipboard.",Toast.LENGTH_SHORT).show();
         }
        break;
        
    case R.id.grid_menu_paste_before:
        
        try {
            List<SlideInfo> slides=ClipboardUtil.getSlidesFromClipboard(this);
            MLog.v("Got slides from clipboard");
            int position= sorted_copy.get(sorted_copy.size()-1);
            String[] names = new String[slides.size()];

            int x=0;
            for(SlideInfo slide: slides){
              MLog.v("x="+x);
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
            MTracker.trackEvent("Slide Options", "Paste_before","SlideGrid",  2); // Value
            MLog.v("saveall");
            ss.saveAll(gv, null, names, slides, new Runnable() { public void run() { SlideGrid.this.postRefreshView(); }});
            MLog.v("deselectall");
            deselectAll();
            for(String name: names){
                gv.setItemChecked(ss.getSlideNumber(name), true);
            }
            
            //ss.insert(gv,info.position,si,true,new Runnable() { public void run() { sg.postRefreshView(); }});
          } catch (Exception e) {
            MLog.e("Error in paste operation",e);
            Toast.makeText(getApplicationContext(),"Sorry, an error was encountered during the paste operation.",Toast.LENGTH_SHORT).show();
          }
          
          break;
          
    case R.id.grid_menu_paste_after:
        
        try {
            List<SlideInfo> slides=ClipboardUtil.getSlidesFromClipboard(this);
            int position= sorted_copy.get(0);
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
            MTracker.trackEvent("Slide Options", "Paste_after","SlideGrid",  2); // Value
            ss.saveAll(gv, null, names, slides, new Runnable() { public void run() { SlideGrid.this.postRefreshView(); }});
            
            deselectAll();
            for(String name: names){
                gv.setItemChecked(ss.getSlideNumber(name), true);
            }
            
            //ss.insert(gv,info.position,si,true,new Runnable() { public void run() { sg.postRefreshView(); }});
          } catch (Exception e) {
            MLog.e("Error in paste operation",e);
            Toast.makeText(getApplicationContext(),"Sorry, an error was encountered during the paste operation.",Toast.LENGTH_SHORT).show();
          }
          
        break;
    case R.id.grid_menu_transition:
    	MTracker.trackEvent("Slide Options", "Set Slide Transition","SlideGrid",  2);
        DialogUtil.chooseArrayItem(this,"Choose Transition",R.array.TransitionNames,R.array.TransitionValues,new StringResultListener() {
          public void setResult(String str) {
        	for(String slideName: slideNames){
        		ss.setTransitionNoSave(gv,slideName,str);	
        	}
        	MTracker.trackEvent("Choose Transition", str,"SlideGrid",  2);
  	      	ss.updatePropsAndSave(gv,null);
  	      deselectAll();
  	      postRefreshView();
          
          }
        });
    	
    	break;
    case R.id.grid_menu_template:
    	sl.copyOutSlide(sorted_copy.get(0),"/sdcard/Slides/template.png");
        MTracker.trackEvent("Slide Options", "Save_template","SlideGrid",  2); // Value
    	deselectAll();
    	break;
    case R.id.grid_menu_show_transition:
        if(showTransitions){
            showTransitions=false;
            item.setTitle("Show Transitions");
        }else{  
            showTransitions=true;
            item.setTitle("Hide Transitions");
        }
        postRefreshView();
        return true;
        
    default:
    	
        break;
    }
    //if(finish)
    //mode.finish();
    //deselectAll();
    return false;
}

private void buildMenu(Menu menu){
	for(int x=0; x<menu.size(); x++){
	    menu.getItem(x).setVisible(true);
	}
    
	if(!ClipboardUtil.isSlideOnClipboard(this)){
		//menu.removeItem(R.id.grid_menu_paste);
		//menu.removeItem(R.id.grid_menu_paste_before);
	    menu.getItem(3).setVisible(false);
	}
}

@Override
public boolean onCreateActionMode(ActionMode mode, Menu menu) {
    
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.grid_selection_menu, menu);
        /*
        if(!ss.isHidden(slideName)){
            hidden=false;
        }
        if(hidden){
            menu.getItem(0).setTitle("Unhide");
        }*/
    
    if(!ClipboardUtil.isSlideOnClipboard(this)){
        //menu.removeItem(R.id.grid_menu_paste);
        //menu.removeItem(R.id.grid_menu_paste_before);
        menu.getItem(3).setVisible(false);
    }
    
    if(showTransitions)
    	menu.getItem(5).setTitle("Hide Transitions");
    else
    	menu.getItem(5).setTitle("Show Transitions");
    
    LayoutInflater li = getLayoutInflater();
    View v=li.inflate(R.layout.action_mode_select_all, null);

    spinner = (Spinner) v.findViewById(R.id.spinner);
    //ArrayAdapter<String> str_adapter= new ArrayAdapter<String>(this,)
    
   

    adapter = new ArrayAdapter<String>(this,
    		android.R.layout.simple_spinner_item, items);
    /*ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(
            this, R.array.select_array, R.layout.custom_spinner_item);*/
    adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

    spinner.setAdapter(adapter);
    spinner.setOnItemSelectedListener(new OnItemSelectedListener(){

		@Override
		public void onItemSelected(AdapterView<?> arg0, View arg1, int arg2,
				long arg3) {
			
			
			if(arg2==1){
					selectAll();
					
			}
			else if(arg2==2){
				deselectAll();
			}
			spinner.setSelection(0);
			//setSubtitle(mode2);
			
		}

		@Override
		public void onNothingSelected(AdapterView<?> arg0) {
			
		}
    	
    });
    mode.setCustomView(v);
    
    
    //mode.setTitle("Select Items");
    setTitle(mode);
    return true;
}

@Override
public void onDestroyActionMode(ActionMode mode) {
	selected.clear();
	invalidateOptionsMenu();
}
private void selectAll(){
	int size= sl.getCurrentSlideSet().getNumSlides();
	for(int x=0; x<size; x++){
		gv.setItemChecked(x, true);
	}
}
private void deselectAll(){
	if(!maskSelection){
	
	int size= sl.getCurrentSlideSet().getNumSlides();
	for(int x=0; x<size; x++){
	    /*
		if(x==size-1){
			maskSelection=true;
			first=true;
			gv.setItemChecked(x, true);
		}else*/
			gv.setItemChecked(x, false);
			if(gv.getCheckedItemCount()==0)
			    break;
	}
	
	}
	
}
@Override
public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
	// TODO Auto-generated method stub
	return false;
}
private void setTitle(ActionMode mode) {
    final int checkedCount = gv.getCheckedItemCount();
    //TextView tv=(TextView)mode.getCustomView().findViewById(R.id.action_mode_text_view);
    
    switch (checkedCount) {
        case 0:
            mode.setSubtitle(null);
            break;
        case 1:
            if(maskSelection){
            	items[0]="0 items selected";
            }else
            	items[0]="1 item selected";
        	
            //tv.setText("One item selected");
            break;
        default:
        	items[0]="" + checkedCount + " items selected";
        	//adapter.notifyDataSetChanged();
            //tv.setText("" + checkedCount + " items selected");
            break;
    }
  adapter.notifyDataSetChanged();
}

}
