package com.rucksack.odatoggle;

import android.app.Activity;
import android.os.Bundle;

/** Unsichtbare Activity: App-Icon antippen = umschalten. */
public class ToggleActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ToggleWidget.doToggle(getApplicationContext());
        finish();
    }
}
