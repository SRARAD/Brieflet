/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import android.app.ActionBar;
import android.app.ListFragment;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseBooleanArray;
import android.view.ActionMode;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView.MultiChoiceModeListener;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import com.sra.brieflet.actions.SlideAction;
import com.sra.brieflet.util.DialogUtil;
import com.sra.brieflet.util.MLog;
import com.sra.brieflet.util.MTracker;
import com.sra.brieflet.util.StringResultListener;

public class SlideList extends ListFragment implements MultiChoiceModeListener {

  private String[] mStrings;
  private String[] fileNames;
  private SlideLoader sl;
  private Brieflet pres;
  private Bundle state=null;
  private static ArrayAdapter arrAdapter;

  public SlideList() {}

  public SlideList(Brieflet pres) {
    this.pres=pres;
  }
  
  public Brieflet getPresenter(){
	  if(pres==null){
		  pres= (Brieflet)getActivity();
	  }  
	  return pres;
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    try {
      MLog.v("SlideList Calling onActivityCreated ");
      super.onCreate(savedInstanceState);
      sl=SlideLoader.getDefaultInstance(getActivity());
      loadSlideIndex(sl);
      arrAdapter=new ArrayAdapter<String>(getActivity(),android.R.layout.simple_list_item_activated_1,mStrings);
      setListAdapter(arrAdapter);
      ListView lv=getListView();
      // lv.setTextFilterEnabled(true);
      lv.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE_MODAL);
      lv.setMultiChoiceModeListener(this);

      ActionBar bar=getActivity().getActionBar();
      // bar.setSubtitle("Long press for options");

      // registerForContextMenu(lv);
      this.state=savedInstanceState;
    } catch (Exception e) {
      MLog.e("slideList "+e.toString(),e);
    }
  }

  @Override
  public void onResume() {
    super.onResume();
    SlideLoader.abortThumbs=true;
    MLog.v("SlideList Calling resume");
  }

  @Override
  public View onCreateView(LayoutInflater inflater,ViewGroup container,Bundle savedInstanceState) {
    MLog.v("SlideList calling onCreateview...");
    super.onCreate(savedInstanceState);
    ListView lV=new ListView(getActivity());
    return lV;
  }

  /*
   * @Override public void onCreateContextMenu(ContextMenu menu,View v,ContextMenuInfo menuInfo) { AdapterContextMenuInfo
   * info=(AdapterContextMenuInfo)menuInfo; menu.setHeaderTitle("Options"); if (Brieflet.SHOW_ALPHA) {
   * menu.add(0,2,0,"Create Presentation"); } menu.add(0,3,0,"Rename Presentation");
   * menu.add(0,1,0,"Send Presentation").setIcon(R.drawable.ic_menu_send);
   * menu.add(0,0,0,"Delete Presentation").setIcon(R.drawable.ic_menu_delete); menu.add(0,5,0,"Set Default Transition");
   * menu.add(0,6,0,"Add Sidebars"); menu.add(0,4,0,"Show Presentation Details"); }
   */

  /*
   * @Override public boolean onContextItemSelected(final MenuItem item) { final AdapterContextMenuInfo
   * info=(AdapterContextMenuInfo)item.getMenuInfo(); if (item.getItemId()==0) {
   * pres.guardAction("Are you sure you want to delete \""
   * +mStrings[info.position]+"\" which corresponds to the file "+fileNames[info.position]+"?",new Runnable() { public void run() {
   * File file=new File(fileNames[info.position]); if (file.exists()) { file.delete(); pres.refreshView(); } //this is not on the
   * GUI thread and should do a refresh }}); } else if (item.getItemId()==1) { Intent intent=new Intent(Intent.ACTION_SEND);
   * intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); intent.setType("application/vnd.brieflet");
   * intent.putExtra(Intent.EXTRA_STREAM,Uri.parse("file://"+fileNames[info.position]));
   * intent.putExtra(Intent.EXTRA_SUBJECT,"\""+mStrings[info.position]+"\" Presentation");
   * intent.putExtra(Intent.EXTRA_TEXT,"I've attached my Android Brieflet presentation entitled \""+mStrings[info.position]+"\".");
   * startActivity(intent); } else if (item.getItemId()==2) { DialogUtil.getTextDialog("Create Presentation","Enter Title",null,new
   * StringResultListener() {
   * 
   * @Override public void setResult(String result) { sl.createSlideSet((Brieflet)getActivity(),result,new Runnable() { public void
   * run() {} { pres.postRefreshView(); }}); } },pres); } else if (item.getItemId()==3) { final View view=this.getView();
   * DialogUtil.getTextDialog("Rename Presentation","Enter New Title",mStrings[info.position],new StringResultListener() {
   * 
   * @Override public void setResult(String result) { File file=new File(fileNames[info.position]);
   * sl.renameSlideSet(view,file.getName(),result,new Runnable() { public void run() { pres.postRefreshView(); }}); } },pres); }
   * else if (item.getItemId()==4) { SlideSet ss0=sl.getSlideSetByPath(fileNames[info.position]); StringBuilder sb=new
   * StringBuilder(); sb.append("Title:"+mStrings[info.position]); if (ss0!=null) { if (ss0.getName().indexOf("|")>-1) {
   * sb.append("\nTitle With Hidden Prefix:"+ss0.getName()); } } sb.append("\nFile:"+fileNames[info.position]);
   * DialogUtil.infoDialog("Presentation Details",sb.toString(),null,pres); } else if (item.getItemId()==5) { final SlideSet
   * ss0=sl.getSlideSetByPath(fileNames[info.position]); final View view=this.getView();
   * DialogUtil.chooseArrayItem(getActivity(),"Choose Transition",R.array.TransitionNames,R.array.TransitionValues,new
   * StringResultListener() { public void setResult(String str) { ss0.setTransition(view,str); } }); }else if (item.getItemId()==6){
   * SlideSet ss0=sl.getSlideSetByPath(fileNames[info.position]); File zipfile= new File(ss0.getZipName());
   * SlideLoader.analyze(pres, zipfile, false); } return(true); }
   */
  private String cleanPrefix(String str) {
    int pos=str.indexOf("|");
    if (pos>-1) {
      if (pos==(str.length()-1)) {
        return("");
      } else {
        return(str.substring(pos+1));
      }
    } else {
      return(str);
    }
  }

  private void loadSlideIndex(SlideLoader sl) {
    Collection<SlideSet> sss=sl.getSlideSets(getPresenter());
    SlideSet[] ssets=new SlideSet[sss.size()];
    int p=0;
    for(SlideSet ss:sss) {
      ssets[p++]=ss;
    }
    Arrays.sort(ssets,new Comparator() {

      @Override
      public int compare(Object object1,Object object2) {
        SlideSet ss1=(SlideSet)object1;
        SlideSet ss2=(SlideSet)object2;
        return(ss1.getName().compareTo(ss2.getName()));
      }
    });
    mStrings=new String[ssets.length];
    fileNames=new String[ssets.length];
    p=0;
    for(SlideSet ss:ssets) {
      mStrings[p]=cleanPrefix(ss.getName());
      fileNames[p++]=ss.getZipName();
    }
  }

  @Override
  public void onListItemClick(ListView l,View v,int position,long id) {
    String filePath=fileNames[position];
    Intent intent=new Intent(getActivity(),SlideViewer.class);
    intent.putExtra("slidePath",filePath);
    SlideLoader sl=SlideLoader.getDefaultInstance(getPresenter());
    sl.isDone=false;
    DialogUtil.showBusyCursor(getPresenter(),"Launching Presentation...","Please wait...",null);
    try {
      startActivity(intent);
    } catch (Exception e) {
      MLog.e(" "+e.toString(),e);
    }
  }

  @Override
  public void onItemCheckedStateChanged(ActionMode mode,int position,long id,boolean checked) {
    int checkedCount=getListView().getCheckedItemCount();
    Menu menu=mode.getMenu();
    if (checkedCount>1) {
      menu.getItem(0).setVisible(false);  
      //menu.removeItem(R.id.menu_edit);
      menu.getItem(5).setVisible(false);  
      //menu.removeItem(R.id.menu_sidebars);
      menu.getItem(6).setVisible(false);  
      //menu.removeItem(R.id.menu_duplicate);
      menu.getItem(3).setVisible(false);  
      //menu.removeItem(R.id.menu_details);
      setSubtitle(mode);
    } else {
        for(int x=0; x<menu.size(); x++){
            menu.getItem(x).setVisible(true);
        }
        
        /*
      if (menu.size()==3) {
        menu.clear();
        onCreateActionMode(mode,menu);
      }*/
        /*
         * MenuInflater inflater = getActivity().getMenuInflater(); inflater.inflate(R.menu.selection_menu, menu);
         * mode.setTitle("Select Items");
         */
      

    }

  }

  @Override
  public boolean onActionItemClicked(ActionMode mode,MenuItem item) {

    // Toast.makeText(getActivity(), "Selected " + getListView().getCheckedItemCount() +
    // " items", Toast.LENGTH_SHORT).show();
    SparseBooleanArray positions=getListView().getCheckedItemPositions();
    if (positions==null) {
      Toast.makeText(getActivity(),"Cant get positions",Toast.LENGTH_SHORT).show();
      return false;
    }
    final List<Integer> selected=new ArrayList<Integer>();

    for(int x=0;x<mStrings.length;x++) {
      if (positions.get(x)) {
        selected.add(x);
      }
    }
    if (selected.size()==0) {
      Toast.makeText(getActivity(),"Nothing selected",Toast.LENGTH_SHORT).show();
      return false;
    }

    // final SlideSet ss0=sl.getSlideSetByPath(fileNames[position]);
    final View view=this.getView();
    switch (item.getItemId()) {
      case R.id.menu_edit:
        DialogUtil.getTextDialog("Rename Presentation","Enter New Title",mStrings[selected.get(0)],new StringResultListener() {

          @Override
          public void setResult(String result) {
            File file=new File(fileNames[selected.get(0)]);
            sl.renameSlideSet(view,file.getName(),result,new Runnable() {

              public void run() {
                getPresenter().postRefreshView();
              }
            });
          }
        },getPresenter());
        MTracker.trackEvent("SlideList Options","Rename","SlideList",2); // Value
        // mode.finish();
        break;
      case R.id.menu_delete:
        String titles="";
        String file_names="";

        for(int x=0;x<selected.size();x++) {
          if (x+1==selected.size()) {
            titles+="\""+mStrings[selected.get(x)]+"\"";
            file_names+="\""+fileNames[selected.get(x)]+"\"";
          } else {
            titles+="\""+mStrings[selected.get(x)]+"\", ";
            file_names+="\""+fileNames[selected.get(x)]+"\", ";
          }
        }
        String respectively=(selected.size()>1) ? " ,respectively" : "";
        String s=(selected.size()>1) ? "s " : " ";

        getPresenter().guardAction("Are you sure you want to delete "+titles+" which corresponds to the file"+s+file_names+respectively+" ?",
            new Runnable() {

              public void run() {
                for(Integer sel:selected) {

                  File file=new File(fileNames[sel]);
                  if (file.exists()) {
                    String chgdir=SlideLoader.getChangedDir(file);
                    file.delete();
                    File fchg=new File(chgdir); //make sure we delete changed directory too
                    if (fchg.exists()) {
                      Brieflet.deleteDir(chgdir);
                    }
                  }
                }
                getPresenter().refreshView();
                // this is not on the GUI thread and should do a refresh
              }
            });
        MTracker.trackEvent("SlideList Options","Delete","SlideList",2); // Value
        break;
      case R.id.menu_send:
        final Intent intent=new Intent(Intent.ACTION_SEND_MULTIPLE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setType("application/vnd.brieflet");

        final ArrayList<Uri> uris=new ArrayList<Uri>();
        String pres_titles="";
        final ArrayList<SlideSet> topack=new ArrayList<SlideSet>();
        for(Integer sel:selected) {
          topack.add(sl.getSlideSetByPath(fileNames[sel]));
          File fileIn= new File(fileNames[sel]);
          //MLog.v("Attachment exists? "+fileIn.exists());
          Uri u = Uri.fromFile(fileIn);
          uris.add(u);
          pres_titles+="\""+fileNames[sel]+"\" ";
        }

        /*
        intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
        String presentation=(selected.size()>0) ? "Presentations" : "Presentation";
        intent.putExtra(Intent.EXTRA_SUBJECT,presentation);
        intent.putExtra(Intent.EXTRA_TEXT,"I've attached my Android Brieflet "+presentation.toLowerCase()+" entitled "+pres_titles
            +".");
        startActivity(intent);
        */
        MTracker.trackEvent("SlideList Options","Send","SlideList",2); // Value

        final String ftitles=pres_titles;
        (new Thread() {

          public void run() {
            SlideLoader.isDone=false;
            DialogUtil.showBusyCursor(view,"Packing For Transmission...","Please wait...",null);
            for(SlideSet ss:topack) {
              sl.packPresentation(ss);
            }
            SlideLoader.isDone=true;
            intent.putParcelableArrayListExtra(Intent.EXTRA_STREAM,uris);
            String presentation=(selected.size()>0) ? "Presentations" : "Presentation";
            intent.putExtra(Intent.EXTRA_SUBJECT,presentation);
            intent.putExtra(Intent.EXTRA_TEXT,"I've attached my Android Brieflet "+presentation.toLowerCase()+" entitled "+ftitles
                +".");
            startActivity(intent);
          }
        }).start();

        break;
      case R.id.menu_transition:

        DialogUtil.chooseArrayItem(getActivity(),"Choose Transition",R.array.TransitionNames,R.array.TransitionValues,
            new StringResultListener() {

              public void setResult(String str) {

                for(Integer sel:selected) {
                  SlideSet s=sl.getSlideSetByPath(fileNames[sel]);
                  s.setTransition(view,str);
                }
              }

            });
        MTracker.trackEvent("SlideList Options","Transition","SlideList",2); // Value
        break;

      case R.id.menu_sidebars:
        final SlideSet ss=sl.getSlideSetByPath(fileNames[selected.get(0)]);
        final File zipfile=new File(ss.getZipName());
        // need a potential pack here
        (new Thread() {

          public void run() {
            SlideLoader.isDone=false;
            DialogUtil.showBusyCursor(view,"Packing For Analysis...","Please wait...",null);
            sl.packPresentation(ss);
            SlideLoader.isDone=true;
            getPresenter().runOnUiThread(new Runnable() {

              public void run() {
                SlideLoader.analyze(getPresenter(),zipfile,null,false,null);
                MTracker.trackEvent("SlideList Options","Sidebars","SlideList",2); // Value
              }
            });
          }
        }).start();
        break;
      case R.id.menu_duplicate:
        final SlideSet ss2=sl.getSlideSetByPath(fileNames[selected.get(0)]);
        final File zipfile2=new File(ss2.getZipName());
        // need a potential pack here
        (new Thread() {

          public void run() {
            SlideLoader.isDone=false;
            DialogUtil.showBusyCursor(view,"Packing For Duplication...","Please wait...",null);
            sl.packPresentation(ss2);
            SlideLoader.isDone=true;
            getPresenter().runOnUiThread(new Runnable() {

              public void run() {
                DialogUtil.getTextDialog("Name for Duplicated Presentation","Enter Title",mStrings[selected.get(0)],
                    new StringResultListener() {

                      @Override
                      public void setResult(String result) {
                        File file=new File(fileNames[selected.get(0)]);
                        sl.duplicateSlideSet(view,file,result,new Runnable() {

                          public void run() {
                            getPresenter().postRefreshView();
                          }
                        });
                      }
                    },getPresenter());
                MTracker.trackEvent("SlideList Options","Duplicate","SlideList",2); // Value
              }
            });
          }
        }).start();
        break;
      case R.id.menu_details:
        final SlideSet ss0=sl.getSlideSetByPath(fileNames[selected.get(0)]);
        StringBuilder sb=new StringBuilder();
        sb.append("Title: "+mStrings[selected.get(0)]);
        if (ss0!=null) {
          if (ss0.getName().indexOf("|")>-1) {
            sb.append("\nTitle With Hidden Prefix: "+ss0.getName());
          }
        }

        File f=new File(fileNames[selected.get(0)]);
        String changeDir=SlideLoader.getChangedDir(f);
        File f0=new File(changeDir);
        if (f0.exists()) {
          sb.append("\nUnpacked (Pending Changes):");
          File[] cfiles=f0.listFiles();
          int meta=0;
          int slide=0;
          int act=0;
          for(File cfile:cfiles) {
            if (cfile.getName().endsWith(".txt")) {
              meta++;
            } else if (cfile.getName().startsWith(".Image")) {
              act++;
            } else {
              slide++;
            }
          }
          int c=0;
          if (meta>1) {
            c++;
            sb.append(" "+meta+" meta files");
          } else if (meta>0) {
            c++;
            sb.append(" 1 meta file");
          }
          if (slide>1) {
            c++;
            if (c>0)
              sb.append(",");
            sb.append(" "+slide+" slide images");
          } else if (slide>0) {
            c++;
            if (c>0)
              sb.append(",");
            sb.append(" 1 slide image");
          }
          if (act>1) {
            c++;
            if (c>0)
              sb.append(",");
            sb.append(" "+act+" stored action bitmaps");
          } else if (act>0) {
            c++;
            if (c>0)
              sb.append(",");
            sb.append(" 1 stored action bitmap");
          }
        }
        sb.append("\nArchive File: "+fileNames[selected.get(0)]);
        sb.append("\nArchive File Last Modified: "+new Date(f.lastModified()));
        long sz=f.length();
        sb.append("\nArchive File Size: "+(sz/1024)+"K");
        sb.append("\nTotal Slides: "+ss0.getNumSlides());
        Rect r=SlideLoader.getSlideSizeRange(f);
        if (r!=null) {
          if (r.left==r.top&&r.right==r.bottom) {
            sb.append(" Size="+r.left+"x"+r.right);
          } else {
            sb.append(" Size Ranges=");
            if (r.left==r.top) {
              sb.append(r.left+"x");
            } else {
              sb.append("["+r.left+","+r.top+"]x");
            }
            if (r.right==r.bottom) {
              sb.append(r.right);
            } else {
              sb.append("["+r.right+","+r.bottom+"]");
            }
          }
        }
        int acts=0;
        for(int i=0;i<ss0.getNumSlides();i++) {
          List<SlideAction> sas=ss0.getActions(i);
          if (sas!=null)
            acts+=sas.size();
        }
        if (acts>0)
          sb.append("\nTotal Actions: "+acts);
        if (ss0.getActionIds().size()>0)
          sb.append("\nStored Action Bitmaps: "+ss0.getActionIds().size());
        DialogUtil.infoDialog("Presentation Details",sb.toString(),null,getPresenter());
        MTracker.trackEvent("SlideList Options","Details","SlideList",2); // Value
        break;

      default:
        break;
    }
    mode.finish();
    return true;

  }

  @Override
  public boolean onCreateActionMode(ActionMode mode,Menu menu) {
    MenuInflater inflater=getActivity().getMenuInflater();
    inflater.inflate(R.menu.selection_menu,menu);
    mode.setTitle("Select Items");
    setSubtitle(mode);
    return true;

  }

  @Override
  public void onDestroyActionMode(ActionMode mode) {}

  @Override
  public boolean onPrepareActionMode(ActionMode mode,Menu menu) {
    return true;
  }

  private void setSubtitle(ActionMode mode) {
    final int checkedCount=getListView().getCheckedItemCount();
    switch (checkedCount) {
      case 0:
        mode.setSubtitle(null);
        break;
      case 1:
        mode.setSubtitle("One item selected"); 
        break;
      default:
        mode.setSubtitle(""+checkedCount+" items selected");
        break;
    }
  }

}
