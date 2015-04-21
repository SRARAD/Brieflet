/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */
package com.sra.brieflet.util;

import android.content.Context;

//import com.google.android.apps.analytics.GoogleAnalyticsTracker;
import com.sra.brieflet.Brieflet;

public class MTracker {
	
    //static GoogleAnalyticsTracker tracker;
    
    public static void initTracker(Context context){
        if(Brieflet.ENABLE_TRACKING){
        	/*
            tracker=GoogleAnalyticsTracker.getInstance();
            // Start the tracker in manual dispatch mode...
            tracker.start("UA-XXXXXXXX-X",30,context); // dispatch tracker every 30 seconds
            */
        }
    }
    
    public static void trackPageView(String page){
        if(Brieflet.ENABLE_TRACKING){
        	/*
            if (tracker!=null){
                //tracker.getVisitorCustomVar(1);
                tracker.trackPageView(page);
            } else {
                MLog.v("tracker was null in track page view for page: "+page); //thought it was better to flag this since it happens often apparently -- SWB
            }
            */
        }
    }
    public static void stop(){
        if(Brieflet.ENABLE_TRACKING){
        	/*
            if (tracker!=null){
                tracker.stop();
            } else {
                MLog.v("tracker was null when trying to stop");
            }
            */
        }
    }
    public static void trackEvent(String category, String action, String label, int val){
        if(Brieflet.ENABLE_TRACKING){
        	/*
            if (tracker!=null){
                tracker.stop();
            } else {
                MLog.v("tracker was null when trying to track event " +category+" : "+action );
            }
            */
        }
    }
    
}
