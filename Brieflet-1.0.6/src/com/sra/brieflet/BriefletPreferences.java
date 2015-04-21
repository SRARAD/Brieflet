/*
 * Copyright (c) 2011-2012 SRA International, Inc., All Rights Reserved.
 */

package com.sra.brieflet;

import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.view.MenuItem;

import com.sra.brieflet.R;
import com.sra.brieflet.util.MTracker;

public class BriefletPreferences extends PreferenceActivity {

  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    MTracker.initTracker(this);
    addPreferencesFromResource(R.xml.preferences);
  }
  
  public boolean onOptionsItemSelected(MenuItem item){
	  boolean b=super.onOptionsItemSelected(item);
	  MTracker.trackEvent("Preferences", item.getTitle().toString(), "Brieflet", 1);
	  return b;
  }

}
