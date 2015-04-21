/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.pptc;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.JPanel;

import org.jawin.DispatchPtr;
import org.jawin.win32.Ole32;

public class PPTConvert  extends JPanel {

  private static String adbPath="c:/Program Files/Android/android-sdk-windows/platform-tools/adb";
  
  private static DispatchPtr preses;
  private double mult=.75;
  private File tmpdir;
  private File slidedir;
  private File appdir=null;
  private boolean loaded=false;
  private boolean GUILog=false;
  private static StringBuilder index;
  
  public PPTConvert(){
     GUILog=false;
  } 
  public PPTConvert(boolean isON){
     GUILog=isON;
  } 

 
  public void convert(String filename,String title,HashSet<String> tags,int width,int height,String zipname,boolean isDeployable, boolean preserverAspectRatio) {
    try {
      if (appdir==null) {
        appdir=new File(System.getProperty("user.home")+"/.pptconvert");
        appdir.mkdir();
      }
      tmpdir=File.createTempFile("PPTConvert","",new File(System.getProperty("user.home")));
      tmpdir.delete();
      tmpdir.mkdir();
      if (!loaded) {
        copyOut("jawin.dll",appdir);
        String dllstr=appdir.getAbsolutePath()+"/jawin.dll";
        System.setProperty("org.jawin.hardlib",dllstr);
        loaded=true;
      }
      slidedir=new File(tmpdir,"slides");
      slidedir.setWritable(true,false);
      slidedir.mkdir();
      File file=new File(filename);
      if (preses==null) {
        Ole32.CoInitialize();
      }
        DispatchPtr app=new DispatchPtr("PowerPoint.Application");
        preses=app.getObject("Presentations");
      DispatchPtr pres=(DispatchPtr)preses.invoke("open",file.getAbsolutePath(),new Integer(0),new Integer(0),Boolean.FALSE);
      DispatchPtr pagesetup=pres.getObject("PageSetup");
      System.out.println("width="+pagesetup.get("SlideWidth"));
      System.out.println("height="+pagesetup.get("SlideHeight"));
      // 720x540 request gets 960x720
      // 1280x800 request gets 1706x1066
      // 960x600 (expands for margins with 1 1/3 multiplier)
      // 1280x754 is aspect ratio = 1.7 = 
      if(preserverAspectRatio){
         Float w=(Float)pagesetup.get("SlideWidth");
         Float h=(Float)pagesetup.get("SlideHeight");
         float minRatio=Math.min((width/w), (height/h));
         w= w* minRatio;
         h= h* minRatio;
         width=w.intValue();
         height=h.intValue();        
      }
      pagesetup.put("SlideWidth",width*mult);
      pagesetup.put("SlideHeight",height*mult);

      pres.invoke("SaveAs",slidedir.getAbsolutePath(),new Integer(18));
      pres.invoke("SaveAs",slidedir.getAbsolutePath(),new Integer(20));
      pres.invoke("Close");
      index=new StringBuilder();
      try { //riskier so catch and hide errors
        //the code below was copied from imbot which was tested only on org charts
        //it needs to be made more robust to catch all text strings, until then it shouldn't be in the release
        analyzeWar(slidedir.getAbsolutePath()+".mht");
      } catch (Exception e) {
      }
      //Ole32.CoUninitialize();
      String stags=null;
      if (tags.size()>0) {
        StringBuilder sb=new StringBuilder();
        int c=0;
        for(String tag:tags) {
          if (c++>0) sb.append(",");
          sb.append(tag);
        }
        stags=sb.toString();
      }
      createZip(slidedir,title,stags,zipname,index);
      if(isDeployable)deploy(zipname);
      deleteDir(tmpdir);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private static void analyzeWar(String file/*,String name*/) {
    //if (updater!=null) updater.reportStatus("Analyzing "+file);
    //System.out.println("Analyzing "+file);
    String divider=null;
    StringBuffer sb = null;
    int slidenum=0;
    try {
        BufferedReader in = new BufferedReader(new FileReader(file));
        String line;
        while((line=in.readLine())!=null) {
            if (line.indexOf("NextPart")>-1) {
                line=line.trim();
                divider=line.substring(line.indexOf("\"")+1,line.length()-1);
                break;
            }
        }
        while(line!=null) {
            while((line=in.readLine())!=null) { //find separator
                if (line.endsWith(divider)) break;
                if (sb!=null) {
                    if (line.endsWith("=")) {
                      sb.append(line.substring(0,line.length()-1));
                    } else {
                      sb.append(line);
                    }
                }
            }
            if (sb!=null) {
                slidenum++;
                //System.out.println("***slidenum="+slidenum);
                analyzeHtml(sb.toString(),slidenum-1);
                sb=null;
            }
            line=in.readLine();
            if (line==null) break;
            line=line.trim();
            if (!line.startsWith("Content-Location:")) {
                //if (updater!=null) updater.reportException("Invalid War Missing Content-Location of File",null);
                System.err.println("Invalid War Missing Content-Location of File");
            } else {
                if (line.endsWith(".htm") && line.lastIndexOf("/slide")>-1 && (line.lastIndexOf("/slide")==line.lastIndexOf("/"))) {
                    //if (updater!=null) updater.reportStatus("Processing: "+line);
                    //System.out.println("Processing: "+line);
                    sb = new StringBuffer();
                    sb.append(line);
                } else {
                    //                      System.out.println("Skipping: "+line);
                }
            }
        }
    } catch (IOException ioe) {
        //if (updater!=null) updater.reportException("Error while reading "+file,ioe);
        System.err.println("Error while reading "+file);
    }
}

public static void index(File file) {
    String outPath=file.getAbsolutePath().substring(0,file.getAbsolutePath().length()-4)+"-dir/war.mhtml";
    analyzeWar(outPath/*,file.getName().substring(0,file.getName().length()-4)+"-dir/Slide"*/);
}  
  private static String elimtags(String text) {
    return(text.replaceAll("<([^ >]+)[^>]*>([^<]*)</\\1>","$2"));
  }
  
  private static void analyzeHtml(String text,int num) throws IOException {
    text=text.replaceAll("=\r\n","");
    //System.out.println(text);
    Pattern pat = Pattern.compile("<(div|span)[^>]+?position: ?absolute; *top:([0-9.]+)%; *left:([0-9.]+)%; *width:([0-9.]+)%; *height:([0-9.]+)%[^>]+>([^<]*?)</\\1>");
    boolean done=false;
    int cnt=0;
    text=text.replaceAll("</?b>",""); //since b's and i's are sometimes not balanced
    text=text.replaceAll("</?i>","");
    text=text.replaceAll("<br>","");
    //remove all symbol font stuff
    text=text.replaceAll("<span[^>]+?mso-char-type:[^s]*?symbol[^>]*?>.*?</span>","");
    text=text.replaceAll("<span[^>]+?ebdings[^>]*?>.*?</span>","");
    //        System.out.println("text is "+text.length()+" bytes");
    //      System.out.println("****************************************");
    //      System.out.println(text);
    while(!done) {
        //  System.out.println("****************************************");
        //      System.out.println("Starting pass "+cnt);
        cnt++;
        Matcher m=pat.matcher(text);
        StringBuffer sb = new StringBuffer();
        boolean changed=false;
        boolean skip=false;
        while(m.find()) {
            changed=true;
            String str=m.group(6);
            str=str.replaceAll("&#13;","");
            str=str.replaceAll("&nbsp;"," ");
            str=str.replaceAll("&#8217;","'");
            str=str.replaceAll("&#[0-9][0-9][0-9][0-9];"," ");
            str=str.replaceAll("&amp;","&");
            str=str.trim();
            if (!str.equals("")) {
                if (!m.group(5).equals("100.0")) {
                    //                      if (out!=null) out.println(filename+","+m.group(2)+","+m.group(3)+","+m.group(4)+","+m.group(5)+","+str);
                  //System.out.println("<"+str+"> at "+m.group(2)+","+m.group(3)+","+m.group(4)+","+m.group(5));
                  if (str.indexOf("|")>-1) str.replaceAll("|","");
                  if (index!=null) index.append(num+"|"+str+"|"+m.group(2)+"|"+m.group(3)+"|"+m.group(4)+"|"+m.group(5)+"\n");
                  /*
                    if (writer!=null) {
                        //full text index it as a document
                        Document doc = new Document();
                        doc.add(Field.Keyword("path",filename));
                        doc.add(Field.Keyword("top",m.group(2)));
                        doc.add(Field.Keyword("left",m.group(3)));
                        doc.add(Field.Keyword("width",m.group(4)));
                        doc.add(Field.Keyword("height",m.group(5)));
                        doc.add(Field.Text("contents",str));
                        writer.addDocument(doc);
                    }
                    */
                }
            } else {
                //              System.out.println("empty match");
            }
            //if we have a 100% height box, continue for a bit
            if (m.group(5).equals("100.0")) {
                //              System.out.println("making replacement of "+m.group(6)+" to continue");
                skip=true;
              m.appendReplacement(sb,m.group(6));
            } else {
              m.appendReplacement(sb,""); //may eliminate this ;-)
            }
        }
        m.appendTail(sb);
        text=sb.toString();
        //      System.out.println("text is now "+text.length()+" bytes (after pos tag elimination)");
        if (!skip) {
            String nxt=elimtags(text); //eliminate simpler tags
            if (!changed && nxt.equals(text)) done=true;
            text=nxt;
        }
        //      System.out.println("text is now "+text.length()+" bytes (after simple tag elimination)");
        //      System.out.println("****************************************");
        //      System.out.println(text);
    }
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

  private void createZip(File slidedir,String title,String tags,String zipname,StringBuilder index) {
    CRC32 crc=new CRC32();
    try {
      ZipOutputStream out=new ZipOutputStream(new FileOutputStream(zipname));
      out.setLevel(6);
      // put an entry in for our title
      ZipEntry entry=new ZipEntry("meta.txt");
      StringBuilder sb=new StringBuilder();
      sb.append("title="+title);
      if (tags!=null) sb.append("\ntags="+tags);
      byte[] txt=sb.toString().getBytes();
      entry.setSize(txt.length);
      crc.reset();
      crc.update(txt);
      entry.setCrc(crc.getValue());
      out.putNextEntry(entry);
      out.write(txt,0,txt.length);
      if (index.length()>0) {
      entry=new ZipEntry("index.txt"); //put text index if available
      txt=index.toString().getBytes();
      entry.setSize(txt.length);
      crc.reset();
      crc.update(txt);
      entry.setCrc(crc.getValue());
      out.putNextEntry(entry);
      out.write(txt,0,txt.length);
      }
      // now put the files in
      File[] files=slidedir.listFiles();
      for(File file:files) {
        FileInputStream in=new FileInputStream(file);
        byte[] buf=new byte[(int)file.length()];
        in.read(buf,0,buf.length);
        in.close();
        entry=new ZipEntry(file.getName());
        entry.setSize(file.length());
        crc.reset();
        crc.update(buf);
        entry.setCrc(crc.getValue());
        out.putNextEntry(entry);
        out.write(buf,0,buf.length);
      }
      out.finish();
      out.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void deploy(String filename) {
    Runtime run=Runtime.getRuntime();
    // String cmd=adbstr+" shell ls sdcard/CGData";
    File file=new File(filename);
    String cmd=adbPath+" push "+filename+" sdcard/Slides/"+file.getName();
    System.out.println("Executing:"+cmd);
    if(GUILog)PPTConvertGUIClient.log.append("Executing:"+cmd+"\n");
    try {
      Process pr=run.exec(cmd);
      BufferedReader in=new BufferedReader(new InputStreamReader(pr.getInputStream()));
      String line;
      while ((line=in.readLine())!=null) {
        System.out.println(line);
        if(GUILog)PPTConvertGUIClient.log.append("line="+line);
      }
      in.close();
    } catch (Exception e) {
      e.printStackTrace();
    }
  }

  private void deleteDir(File dir) {
    if (dir.isDirectory()) {
      File[] children=dir.listFiles();
      for(File child:children) {
        deleteDir(child);
      }
    }
    if (!dir.delete()) {
      System.out.println("Trouble deleting: "+dir.getAbsolutePath());
      if(GUILog)PPTConvertGUIClient.log.append("Trouble deleting: "+dir.getAbsolutePath()+"\n");
      dir.deleteOnExit();
    }
  }

  public static void main(String[] args) {
     
    PPTConvert pptc=new PPTConvert();
    pptc.convert("/path/to/test/ppt","title to display",null,1280,754,"/path/to/output/zip", true, false);
  }

}
