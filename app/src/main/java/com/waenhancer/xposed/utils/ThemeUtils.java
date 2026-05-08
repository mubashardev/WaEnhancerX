package com.waenhancer.xposed.utils;

import android.content.Context;
import android.content.res.Configuration;

public class ThemeUtils {

    public static boolean isNightMode(Context context) {
        if (context == null) return false;
        try {
            int uiMode = context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            return uiMode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable t) {
            return false;
        }
    }

    public static int getThemeBackgroundColor(Context context, boolean isDark) {
        return isDark ? 0xff080808 : 0xfff8f9fa;
    }

    public static int getThemeCardColor(Context context, boolean isDark) {
        return isDark ? 0xff121212 : 0xffffffff;
    }

    public static int getThemeTextColorPrimary(Context context, boolean isDark) {
        return isDark ? 0xffececec : 0xff1a1a1a;
    }

    public static int getThemeTextColorSecondary(Context context, boolean isDark) {
        return isDark ? 0xff9e9e9e : 0xff70757a;
    }

    public static int getThemeAccentColor(Context context) {
        return 0xff00a884; // WhatsApp Teal (Premium)
    }

    public static int getThemeAccentColorSecondary(Context context) {
        return 0xff00d4a1; // Brighter Teal for gradients
    }
}
