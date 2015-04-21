/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package zamzar;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.CRC32;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sra.brieflet.Brieflet;
import com.sra.brieflet.SlideLoader;
import com.sra.brieflet.util.MLog;

public class ZamZar {
  
  private static Set<Integer> active=new HashSet<Integer>();
  
  private static int getChannel() {
    int c=1;
    while(active.contains(c)) {
      c++;
    }
    active.add(c);
    return(c);
  }
  
  private static void releaseChannel(Brieflet brieflet,int num) {
    active.remove(num);
    //tell brieflet to cancel the notification if it hasn't already
    brieflet.cancelNotification(num);
  }
  public static boolean isChannelActive(int chan){
      return active.contains(chan);
  }

  private static void setHeaders(URLConnection conn) {
    conn.addRequestProperty("User-Agent",
        "Mozilla/5.0 (Windows NT 6.0) AppleWebKit/534.24 (KHTML, like Gecko) Chrome/11.0.696.50 Safari/534.24");
    conn.addRequestProperty("Accept","application/xml,application/xhtml+xml,text/html;q=0.9,text/plain;q=0.8,image/png,*/*;q=0.5");
    conn.addRequestProperty("Accept-Encoding","gzip.deflate,sdch");
    conn.addRequestProperty("Accept-Language","en-US,en;q=0.8");
    conn.addRequestProperty("Accept-Charset","ISO-8859-1,utf-8;q=0.7,*;q=0.3");
  }

  private static boolean hasSupportedExtension(String str) {
    int pos=str.lastIndexOf(".");
    if (pos==-1)
      return(false);
    String ext=str.substring(pos+1).toLowerCase();
    return(ext.equals("gif")||ext.equals("png")||ext.equals("jpg")||ext.equals("bmp")||ext.equals("zip"));
  }

  private static String pad4(int i) {
    String str="0000"+i;
    return(str.substring(str.length()-4));
  }

  public static File makeDownloadFileName(String stem) {
    File dir=new File("/sdcard/Slides/downloads");
    if (!dir.exists())
      dir.mkdirs();
    String prefix=null;
    if (stem!=null) {
      prefix="/sdcard/Slides/downloads/"+stem;
    } else {
      prefix="/sdcard/Slides/downloads/Download";
    }
    File file=new File(prefix+".blt");
    if (!file.exists())
      return(file);
    int c=1;
    while (true) {
      c++;
      file=new File(prefix+pad4(c)+".blt");
      if (!file.exists()) {
        return(file);
      }
    }
  }
  
  /**
   * Take resulting zip and do an aspect-preserving scale down to screen
   * size of each image.  Then move the resulting file up to the slides
   * directory.
   * @param chan
   * @param outfile
   * @return
   */
  public static File copyOver(Brieflet act,int chan,File outfile) {
    String path=outfile.getAbsolutePath();
    int dot=path.lastIndexOf(".");
    String tpath=path.substring(0,dot)+"-tmp"+path.substring(dot); //make a temp file
    File tfile=new File(tpath);
    outfile.renameTo(tfile); //move file to temp file
    SlideLoader.scaleAllImagesInZipFile(act,chan,tfile,outfile); //scale back to original file
    tfile.delete(); //delete tempfile
    //no proceed to move original file up as necessary
    int pos=path.indexOf("/downloads");
    if (pos>-1) {
      path=path.substring(0,pos)+path.substring(pos+10);
      File file=new File(path);
      if (!file.exists()) {
        outfile.renameTo(file);
        act.updateLoading(chan,101,"Conversion Completed");
        releaseChannel(act,chan);
        return(file);
      } else {
        int c=1;
        while(file.exists()) {
          c++;
          int ext=path.lastIndexOf(".");
          file=new File(path.substring(0,ext)+c+path.substring(ext));
        }
        outfile.renameTo(file);
        act.updateLoading(chan,101,"Conversion Completed");
        releaseChannel(act,chan);
        return(file);
      }
    } else {
      MLog.v("Download file without download path:"+path);
    }
    act.updateLoading(chan,101,"Conversion Failed");
    releaseChannel(act,chan);
    return(null);
  }
  
  public static File downloadZamzar(final Brieflet act,String body) {
    int chan=-1;
    int pos=body.indexOf("http://www.zamzar.com/getFiles.php");
    // MLog.v("<"+body+">");
    // MLog.v("pos="+pos);
    if (pos>-1) {
      int pos2;
      int pos3=body.indexOf("'",pos);
      int pos4=body.indexOf("<",pos);
      // MLog.v("p3="+pos3+" p4="+pos4);
      if (pos3==-1) { // handle both shared URL from gmail and shared body from
                      // k9
        pos2=pos4;
      } else if (pos4==-1) {
        pos2=pos3;
      } else {
        pos2=Math.min(pos3,pos4);
      }
      if (pos2==-1)
        pos2=body.length(); // if shared from browser, the URL ends and the end
                            // of string
      // MLog.v("p2="+pos2);
      if (pos2>pos) {
        String urlstr=body.substring(pos,pos2);
        List<String> tofetch=new ArrayList<String>();
        try {
          MLog.v("Downloading "+urlstr);
          URL url=new URL(urlstr);
          // MLog.v(urlstr);
          URLConnection conn=url.openConnection();
          setHeaders(conn);
          conn.setDoOutput(true);
          BufferedReader in=new BufferedReader(new InputStreamReader(conn.getInputStream()));
          String line;
          while ((line=in.readLine())!=null) {
            int last=0;
            while (true) {
              pos=line.indexOf("\"/download.php",last);
              if (pos>-1) {
                pos2=line.indexOf("\"",pos+1);
                if (pos2>-1) {
                  // MLog.v(line.substring(pos+1,pos2));
                  String str=line.substring(pos+1,pos2);
                  if (hasSupportedExtension(str)) {
                    tofetch.add(str);
                  }
                }
              } else {
                break;
              }
              last=pos2+1;
            }
          }
          in.close();
          for(String name:tofetch) {
            if (name.endsWith(".zip")) {
              MLog.v("found zip archive:"+name);
              return(downloadZamZarZip(act,name));
            }
          }
          MLog.v("found "+tofetch.size()+" qualifying files");
          if (tofetch.size()==0) 
            return(null);
          File outfilename=null;
          String nm=null;
          String str0=tofetch.get(0);
          int p1=str0.lastIndexOf("=");
          int p2=str0.lastIndexOf("-");
          if (p1>-1&&p2>-1&&p2>p1) {
            nm=str0.substring(p1+1,p2);
          }
          outfilename=makeDownloadFileName(nm);
          /*
           * if (nm==null) { outfilename=act.findIncomingPresFile(); } else {
           * String str="/sdcard/Slides/"+nm; int cnt=0; while (true) { if
           * (cnt==0) { outfilename=new File(str+".blt"); } else {
           * outfilename=new File(str+cnt+".blt"); } if (!outfilename.exists())
           * break; cnt++; } }
           */
          CRC32 crc=new CRC32();
          byte[] buf=new byte[4096];
          int len=0;
          ZipOutputStream out=new ZipOutputStream(new FileOutputStream(outfilename));
          out.setLevel(6);
          ZipEntry entry;
          // write out new version of properties for this entry
          if (nm!=null) {
            entry=new ZipEntry("title.txt");
            byte[] entbuf=nm.getBytes();
            entry.setSize(entbuf.length);
            crc.reset();
            crc.update(entbuf);
            entry.setCrc(crc.getValue());
            out.putNextEntry(entry);
            out.write(entbuf);
          }
          int scnt=0;
          int tot=tofetch.size();
          int cur=0;
          final String dnm=nm;
          chan=getChannel();
          final int fchan=chan;
          act.runOnUiThread(new Runnable() {

            public void run() {
              act.startLoading(fchan,"Downloading "+dnm);
            }
          });
          for(String target:tofetch) {
            if (target.endsWith(".zip"))
              continue; // if for some reason we decided not to use the zip
            MLog.v("Downloading "+target);
            cur++;
            url=new URL("http://www.zamzar.com"+target);
            conn=url.openConnection();
            setHeaders(conn);
            conn.setDoOutput(true);
            ByteArrayOutputStream baos=new ByteArrayOutputStream();
            InputStream rin=conn.getInputStream();
            buf=new byte[4096];
            String entryName=null;
            int l1=target.lastIndexOf(".");
            String ext=target.substring(l1+1);
            scnt++;
            entryName="Slide"+scnt+"."+ext;
            entry=new ZipEntry(entryName);
            while ((len=rin.read(buf,0,buf.length))>-1) {
              baos.write(buf,0,len);
            }
            in.close();
            MLog.v(target+" returned "+baos.size()+" bytes");
            byte[] entbuf=baos.toByteArray();
            entry.setSize(entbuf.length);
            crc.reset();
            crc.update(entbuf);
            entry.setCrc(crc.getValue());
            out.putNextEntry(entry);
            out.write(entbuf);
            act.updateLoading(chan,(int)(100*cur/tot),cur+"/"+tot+" files");
          }
          out.finish();
          out.close();
          act.updateLoading(chan,100,"Download Completed");
          //act.postRefreshView();
          return(copyOver(act,chan,outfilename));
        } catch (Exception e) {
          MLog.e("Error retrieving URL",e);
          act.updateLoading(chan,101,"Download Failed");
        }
      }
    }
    releaseChannel(act,chan);
    return(null);
  }
  
  private static String reportSize(int num) {
    int kb=(num/1024);
    return(kb+"K Downloaded");
  }

  private static File downloadZamZarZip(final Brieflet act,String name) {
      String dlName=name;
    while(dlName.indexOf(" ")!=-1){
        int index=dlName.indexOf(" ");
        String first= dlName.substring(0, index);
        String second=dlName.substring(index+1);
        dlName=first+"%20"+second;
    }
    int pos0=name.lastIndexOf(".");
    int pos=name.lastIndexOf(".",pos0-1);
    String nm=null;
    if (pos0==-1) {
      nm=name;
    } else if (pos==-1) {
      nm=name.substring(0,pos0);
    } else if (pos>-1) {
      nm=name.substring(0,pos);
    }
    nm=nm.substring(nm.lastIndexOf("=")+1);
    File outfilename=makeDownloadFileName(nm);
    MLog.v("Downloading "+dlName);
    String name2=nm;
    if (name2==null)
      name2="Slides";
    final String fname2=name2;
    final int chan=getChannel();
    act.runOnUiThread(new Runnable() {

      public void run() {
        act.startLoading(chan,"Downloading "+fname2);
      }
    });
    try {
      URL url=new URL("http://www.zamzar.com"+dlName);
      URLConnection conn=url.openConnection();
      setHeaders(conn);
      conn.setDoOutput(true);
      FileOutputStream out=new FileOutputStream(outfilename);
      InputStream rin=conn.getInputStream();
      int clen=conn.getContentLength();
      MLog.v("content length="+clen);
      int tot=0;
      byte[] buf=new byte[4096];
      int len;
      int lp=0;
      while ((len=rin.read(buf,0,buf.length))>-1) {
        tot+=len;
        if (clen>-1) {
          int p=(int)((int)(1f*tot/clen*100));
          if (lp!=p) {
            if (p>100) p=100; //in case bad reported content length
            act.updateLoading(chan,p,reportSize(tot));
            MLog.v(p+"%");
            lp=p;
          }
        }
        out.write(buf,0,len);
      }
      rin.close();
      out.close();
      act.updateLoading(chan,100,"Download Complete");
      // copy of file to Slides should happen here
      return(copyOver(act,chan,outfilename));
    } catch (Exception e) {
        MLog.e("Error downloading zip", e);
      act.updateLoading(chan,101,"Download Failed");
    }
    releaseChannel(act,chan);
    return(null);
  }
}
