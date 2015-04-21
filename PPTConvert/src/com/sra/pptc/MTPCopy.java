/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.pptc;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;

import jmtp.PortableDevice;
import jmtp.PortableDeviceFolderObject;
import jmtp.PortableDeviceManager;
import jmtp.PortableDeviceObject;
import jmtp.PortableDeviceStorageObject;

public class MTPCopy {

  private File appdir=null;
  private boolean loaded=false;
  boolean copied;
  
  protected void copyToXoomFromGUI(String filename,String path){
     copied=false;
     copyToXoom( filename,path);
  }

  private void copyToXoom(String filename,String path) {
    try {
      if (appdir==null) {
        appdir=new File(System.getProperty("user.home")+"/.pptconvert");
        appdir.mkdir();
      }
      if (!loaded) {
        copyOut("jmtp.dll",appdir);
        String dllstr=appdir.getAbsolutePath()+"/jmtp.dll";
        System.load(dllstr);
        loaded=true;
      }
    } catch (Exception e) {}
    System.out.println("Current contents of ");
    PortableDeviceManager manager=new PortableDeviceManager();
    for(PortableDevice device:manager) {
       System.out.println("Current contents of "+device);
      if (device.toString().startsWith("\\\\?\\usb#vid_22b8&pid_70a")) { // vid=Motorola  // pid=Xoom
          device.open();
          transferFile(device,path,filename);
          device.close();
      }else{
    	  device.open();
    	  if(device.getModel().equals("Xoom")){
    		  transferFile(device,path,filename);
    	  }else{
    	      System.out.println("Device String: "+device.toString());
    	  }
    	  device.close();
      }
    }
  }
  private void transferFile(PortableDevice device, String path, String filename){
      try {
          //device.open();
          System.out.println("prot="+device.getProtocol());
          System.out.println(device);
          // System.out.println("desc=<"+device.getDescription()+">");
          // System.out.println("manu=<"+device.getManufacturer()+">");
          System.out.println("model=<"+device.getModel()+">");
          System.out.println("sn=<"+device.getSerialNumber()+">");
          System.out.println("type=<"+device.getType()+">");
          // System.out.println(device.getSyncPartner());
          System.out.println("fw="+device.getFirmwareVersion());
          // System.out.println("pl="+device.getPowerLevel());
          System.out.println("ps="+device.getPowerSource());
          PortableDeviceFolderObject folder=getFolder(device,path);
          if (folder==null) {
            System.err.println("Destination folder not found");
          } else {
            System.out.println("Current contents of "+path+" is:");
            for(PortableDeviceObject obj:folder.getChildObjects()) {
              System.out.println("  "+obj.getOriginalFileName());
            }
            File file=new File(filename);
            if (file.exists()) {
              for(PortableDeviceObject obj:folder.getChildObjects()) {
                if (filename.endsWith(obj.getOriginalFileName())) {
                  System.out.println("Already exists so delete first: "+obj.getOriginalFileName());
                  obj.delete();
                }
              }
              folder.addObject(file);
              System.out.println("Contents of "+path+" after copy is:");
              for(PortableDeviceObject obj:folder.getChildObjects()) {
                System.out.println("  "+obj.getOriginalFileName());
              }
              copied=true;
            } else {
              System.err.println("Sorry file didn't exist to copy");
            }
          }
          //device.close();
          
        } catch (Exception e) {
          e.printStackTrace();
        }
  }
  public PortableDeviceStorageObject getRoot(PortableDevice device) {
    for(PortableDeviceObject object:device.getRootObjects()) {
      if (object instanceof PortableDeviceStorageObject) {
        PortableDeviceStorageObject storage=(PortableDeviceStorageObject)object;
        return(storage); // first one for now
      }
    }
    return(null);
  }

  public PortableDeviceObject getTopObject(PortableDevice device,String name) {
    PortableDeviceStorageObject node=getRoot(device);
    for(PortableDeviceObject child:node.getChildObjects()) {
      if (name.equalsIgnoreCase(child.getOriginalFileName())) {
        System.out.println("Found:"+name);
        return(child);
      }
    }
    return(null);
  }

  public PortableDeviceFolderObject getFolder(PortableDevice device,String path) {
    if (path.startsWith("/"))
      path=path.substring(1);
    if (path.startsWith("sdcard/"))
      path=path.substring(7);
    if (!path.endsWith("/"))
      path=path+"/";
    int pos=path.indexOf("/");
    if (pos==-1)
      return(null);
    String part=path.substring(0,pos);
    PortableDeviceObject obj=getTopObject(device,part);
    if (!(obj instanceof PortableDeviceFolderObject))
      return(null);
    PortableDeviceFolderObject node=(PortableDeviceFolderObject)obj;
    if (path.length()==pos+1) return(node);
    path=path.substring(pos+1);
    while ((pos=path.indexOf("/"))>-1) {
      part=path.substring(0,pos);
      for(PortableDeviceObject child:node.getChildObjects()) {
        if (part.equalsIgnoreCase(child.getOriginalFileName())) {
          if (!(child instanceof PortableDeviceFolderObject))
            return(null);
          System.out.println("Found:"+part);
          node=(PortableDeviceFolderObject)child;
          break;
        }
      }
      if (path.length()==pos+1)
        return(node);
      path=path.substring(pos+1);
    }
    return(null);
  }

  private void copyOut(String filename,File dir) {
    InputStream in=PPTConvert.class.getResourceAsStream(filename);
    try {
      File file=new File(dir,filename);
      file.setExecutable(true);
      FileOutputStream out=new FileOutputStream(file);
      byte[] buf=new byte[4096];
      int len;
      while ((len=in.read(buf,0,buf.length))!=-1) {
        out.write(buf,0,len);
      }
      out.close();
      in.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void recurse(PortableDeviceObject obj,String prefix) {
    if (obj instanceof PortableDeviceStorageObject) {
      PortableDeviceStorageObject storage=(PortableDeviceStorageObject)obj;
      System.out.println(prefix+obj.getName());
      try {
        storage.addObject(new File("c:/test_file.png"));
      } catch (Exception e) {
        e.printStackTrace();
      }
      for(PortableDeviceObject child:storage.getChildObjects()) {
        recurse(child," "+prefix);
      }
    } else {
      System.out.println(prefix+obj.getClass());
      System.out.println(prefix+obj.getOriginalFileName());
    }
  }

  public static void main(String args[]) {
    MTPCopy m=new MTPCopy();
    m.copyToXoom("c:/test_file.png","/sdcard/CGData");
  }
}
