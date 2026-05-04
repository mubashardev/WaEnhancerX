package com.waenhancer.xposed.features.customization;


import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.Utils;

import java.util.Objects;
import java.util.Properties;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class BubbleColors extends Feature {
    public BubbleColors(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    @Override
    public void doHook() throws Exception {

        Properties properties = Utils.getProperties(prefs, "custom_css", "custom_filters");

        boolean bubbleColor = prefs.getBoolean("bubble_color", false);

        if (!bubbleColor && !Objects.equals(properties.getProperty("bubble_colors"), "true"))
            return;

        int bubbleLeftColor = bubbleColor ? prefs.getInt("bubble_left", 0) : Color.parseColor(DesignUtils.checkSystemColor(properties.getProperty("bubble_left", "#00000000")));
        int bubbleRightColor = bubbleColor ? prefs.getInt("bubble_right", 0) : Color.parseColor(DesignUtils.checkSystemColor(properties.getProperty("bubble_right", "#00000000")));

        var dateWrapper = Unobfuscator.loadBallonDateDrawable(classLoader);

        XposedBridge.hookMethod(dateWrapper, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var drawable = (Drawable) param.getResult();
                if (drawable == null)return;
                var position = (int) param.args[0];
                if (position == 3) {
                    // If bubble color is not enabled and color is 0, skip
                    if (!bubbleColor && bubbleRightColor == 0) {
                        return;
                    }
                    // If color is 0, clear the filter; otherwise apply the color
                    if (bubbleRightColor == 0) {
                        drawable.setColorFilter(null);
                    } else {
                        drawable.setColorFilter(new PorterDuffColorFilter(bubbleRightColor, PorterDuff.Mode.SRC_IN));
                    }
                } else {
                    // If bubble color is not enabled and color is 0, skip
                    if (!bubbleColor && bubbleLeftColor == 0) {
                        return;
                    }
                    // If color is 0, clear the filter; otherwise apply the color
                    if (bubbleLeftColor == 0) {
                        drawable.setColorFilter(null);
                    } else {
                        drawable.setColorFilter(new PorterDuffColorFilter(bubbleLeftColor, PorterDuff.Mode.SRC_IN));
                    }
                }
            }
        });

        var babblon = Unobfuscator.loadBallonBorderDrawable(classLoader);
        XposedBridge.hookMethod(babblon, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var drawable = (Drawable) param.getResult();
                if (drawable == null)return;
                var position = (int) param.args[1];
                if (position == 3) {
                    // If bubble color is not enabled and color is 0, skip
                    if (!bubbleColor && bubbleRightColor == 0) {
                        return;
                    }
                    // If color is 0, clear the filter; otherwise apply the color
                    if (bubbleRightColor == 0) {
                        drawable.setColorFilter(null);
                    } else {
                        drawable.setColorFilter(new PorterDuffColorFilter(bubbleRightColor, PorterDuff.Mode.SRC_IN));
                    }
                } else {
                    // If bubble color is not enabled and color is 0, skip
                    if (!bubbleColor && bubbleLeftColor == 0) {
                        return;
                    }
                    // If color is 0, clear the filter; otherwise apply the color
                    if (bubbleLeftColor == 0) {
                        drawable.setColorFilter(null);
                    } else {
                        drawable.setColorFilter(new PorterDuffColorFilter(bubbleLeftColor, PorterDuff.Mode.SRC_IN));
                    }
                }
            }
        });


        var bubbleDrawableMethod = Unobfuscator.loadBubbleDrawableMethod(classLoader);

        XposedBridge.hookMethod(bubbleDrawableMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                var position = (int) param.args[0];
                var draw = (Drawable) param.getResult();
                var right = position == 3;
                if (right) {
                    // If bubble color is not enabled and color is 0, skip
                    if (!bubbleColor && bubbleRightColor == 0) {
                        return;
                    }
                    // If color is 0, clear the filter; otherwise apply the color
                    if (bubbleRightColor == 0) {
                        draw.setColorFilter(null);
                    } else {
                        draw.setColorFilter(new PorterDuffColorFilter(bubbleRightColor, PorterDuff.Mode.SRC_IN));
                    }
                } else {
                    // If bubble color is not enabled and color is 0, skip
                    if (!bubbleColor && bubbleLeftColor == 0) {
                        return;
                    }
                    // If color is 0, clear the filter; otherwise apply the color
                    if (bubbleLeftColor == 0) {
                        draw.setColorFilter(null);
                    } else {
                        draw.setColorFilter(new PorterDuffColorFilter(bubbleLeftColor, PorterDuff.Mode.SRC_IN));
                    }
                }
            }
        });

    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Bubble Colors";
    }
}
