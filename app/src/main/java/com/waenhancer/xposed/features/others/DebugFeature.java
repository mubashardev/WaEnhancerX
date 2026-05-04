package com.waenhancer.xposed.features.others;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;

import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;

public class DebugFeature extends Feature {


    public DebugFeature(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {

    }


    @NonNull
    @Override
    public String getPluginName() {
        return "Debug Feature";
    }


}
