/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.pptc;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.util.Date;
import java.util.HashSet;
import java.util.prefs.*;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileFilter;


public class PPTConvertGUIClient {
   static private final String newline = "\n";
   private String source="c:/";
   JButton openButton;
   JButton convertButton;
   JButton aboutButton;
   static JTextArea log;
   PPTConvert pptc;
   MTPCopy mtp;
   boolean runADB=false;
   HashSet<String> tags=new HashSet<String>();// declare my variable at the top of my Java class
   private Preferences prefs;

 
   
   public PPTConvertGUIClient (String[] args){
     parseArgs(args);
     pptc=new PPTConvert(true);
     mtp=new MTPCopy();
     runADB=false;
     // create a Preferences instance (somewhere later in the code)
     prefs = Preferences.userNodeForPackage(this.getClass());
   }
   
  private void parseArgs(String[] args) {
    for(int i=0;i<args.length;i++) {
      if (args[i].startsWith("-t")) {
        for(int j=i+1;j<args.length;j++) {
          if (!args[j].startsWith("-")) {
            tags.add(args[j]);
          }
        }
      }
    }
  }
   
  public static void main(String[] args) {
    try {
        UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
        PPTConvertGUIClient pptClient= new PPTConvertGUIClient(args);
        pptClient.createAndShowGUI();
    } catch (Exception evt) {}
  }
  
  private  void createAndShowGUI() {
     JFrame f = new JFrame("PowerPoint Converter");
     f.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
     f.setResizable(false);
     
     Container cp = f.getContentPane();
     final JLabel title = new JLabel("Title:",SwingConstants.RIGHT);
     final JLabel filet = new JLabel("File:",SwingConstants.RIGHT);
     final JTextField fileN = new JTextField();
     final JTextField titleN = new JTextField(97); 

     final JCheckBox aspectratio =new JCheckBox("Preserve Aspect Ratio");
     openButton=new JButton("Browse");
     convertButton=new JButton("Convert");
     aboutButton=new JButton("About");
     log = new JTextArea(25,75);
     log.setMinimumSize(new Dimension(25,75));
     log.setMargin(new Insets(5,5,5,5));
     log.setEditable(false);
     JScrollPane logScrollPane = new JScrollPane(log);
     
     try{   
     JPanel pane = new JPanel(new GridBagLayout());
     GridBagConstraints c = new GridBagConstraints();
     //natural height, maximum width
     c.fill = GridBagConstraints.HORIZONTAL;
     
     c.ipadx = 4;    //make this component wide
     c.ipady = 2;    //make this component long
     c.gridx = 0;
     c.gridy = 0;
     c.weightx = 0.0;
     c.weighty = 0.0;
     c.gridwidth = 1;
     pane.add(filet,c);    
     c.gridx = 0;
     c.gridy = 1;
     pane.add(title ,c);
     c.gridx = 1;
     c.gridy = 2;
     pane.add(aspectratio ,c);
     
     c.gridx = 1;
     c.gridy = 0;
     c.weightx = 0.5;
     pane.add(fileN,c);
     c.gridy = 1;
     c.gridwidth = 2;
     pane.add(titleN,c);
     
     c.gridx = 2;
     c.gridy = 0;
     c.gridwidth = 1;
     c.weightx = 0.0;
     pane.add(openButton,c);
     c.gridx = 2;
     c.gridy = 2;
     pane.add(convertButton, c);
    
     c.gridx = 1;
     c.gridy = 3;
     c.gridwidth = 2;
     c.weightx = 1.0;
     c.anchor = GridBagConstraints.PAGE_END; //bottom of space
     c.insets = new Insets(5,0,1,2);  //top padding
     pane.add(logScrollPane, c);   
     cp.add(pane);   
     c.gridx = 2;
     c.gridy = 4;
     pane.add(aboutButton,c);
     }catch(Exception e){
     }
  // Create a file chooser that opens up as an Open dialog
     aboutButton.addActionListener(new ActionListener() {
       public void actionPerformed(ActionEvent ae) {
          String msg="PowerPoint Converter Version 1.01 \nCopyright 2011 SRA International, All Rights Reserved";
          //ImageIcon icon = new ImageIcon("sra1.png", "SRA Logo");
          JOptionPane.showMessageDialog(null, msg, "About PowerPointConverter", JOptionPane.INFORMATION_MESSAGE);         
       }
     });
   // Create a file chooser that opens up as an Open dialog
     openButton.addActionListener(new ActionListener() {
       public void actionPerformed(ActionEvent ae) {
         JFileChooser chooser = new JFileChooser();
         String lastSelectedDir = prefs.get("LAST_SELECTED_DIR", "");
         if(lastSelectedDir!=null ||!lastSelectedDir.equals("")){ 
            chooser.setCurrentDirectory(new File(lastSelectedDir) );//getCurrentDirectory();
         }
         chooser.addChoosableFileFilter(new FileFilter() {
          @Override
          public boolean accept(File file ) {
            return(file.isDirectory()||(file.getName().endsWith(".ppt")) ||
                   (file.getName().endsWith(".pptx")));
          }
          @Override
          public String getDescription() {
            return("PowerPoint Files");
          }
         });
         chooser.setMultiSelectionEnabled(true);
         int option = chooser.showOpenDialog(PPTConvertGUIClient.this.pptc);
         if (option == JFileChooser.APPROVE_OPTION) {
          // File[] sf = chooser.getSelectedFiles();  
           File sf=chooser.getSelectedFile();
           prefs.put("LAST_SELECTED_DIR", sf.getAbsolutePath()); 
           fileN.setText(sf.getName());
           source=sf.getPath();
           titleN.setText(sf.getName().substring(0,sf.getName().lastIndexOf(".")));
         }
         else {
           log.append("You canceled.\n"  );
         }
       }
     });
  // Create a listener that respond to Clicking convert button
     convertButton.addActionListener(new ActionListener() {
       public void actionPerformed(ActionEvent ae) {

         (new Thread() {public void run() {
         openButton.setEnabled(false);
         runADB=false; //make sure flage is reset to false
         Date d1 = new Date();
         logAppend("PPTConvert is starting..."+newline );
         logAppend("Time:"+ d1.toString()+newline );
         boolean resAspectRatio= aspectratio.isSelected();

         if (!titleN.getText().trim().equals("")&& !fileN.getText().trim().equals("")) {
            logAppend("File title: "+titleN.getText()+newline  );
            logAppend("File name: "+fileN.getText()+newline  );
            logAppend("Conversion has started. Please wait..."+newline );
            String fileToCopy=source.substring(0,source.lastIndexOf(File.separator)+1)+fileN.getText().substring(0, fileN.getText().lastIndexOf("."))+".blt";     
            if(titleN.getText().contains("!~")){ // secret key to exec adb tool
               runADB=true;
               logAppend("PPTConvert was done using Adb tool.\n"  );
            }
            PPTConvertGUIClient.this.pptc.convert(source,titleN.getText(),tags,1280,754,fileToCopy, runADB,resAspectRatio);
            
            logAppend("Zip file created: "+fileToCopy +newline );
            if(!runADB){
                try{
               PPTConvertGUIClient.this.mtp.copyToXoomFromGUI(fileToCopy,"/sdcard/Slides");
                }catch(Exception e){
                    e.printStackTrace();
                }
               if(mtp.copied)
               logAppend("Zip file copied to device directory /sdcard/Slides using Media Transfer Protocol (MTP)."+newline   );
               
               logAppend("PPTConvert complete.\n_______________________________________"+newline+newline);
               if(!mtp.copied){
                   logAppend("Zip file located in: " + fileToCopy+ newline+ 
                           "You can now email the file to yourself and open the attachment on your device. OR "+newline+ "Copy the file directly" +
                          " to the /sdcard/Slides directory on your device.");
               }    
            }
         }
         else {
           logAppend("Please enter title and/or choose a file."+newline   );
         }

         
         openButton.setEnabled(true);
         }}).start();
       }
     });
     f.pack();
     f.setVisible(true);
 }
private void about(){
  // JOptionPane.showMessageDialog(frame, "Eggs are not supposed to be green.");
}
 private void logAppend(final String text) {
   SwingUtilities.invokeLater(new Runnable() { public void run() { log.append(text); }});
 }
 
}


           
         
    
