package com.waenhancer.xposed.features.others;

import android.app.Activity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.DesignUtils;
import com.waenhancer.xposed.utils.Utils;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Injects a "WaEnhancer Settings" tile into WhatsApp's settings RecyclerView.
 * Clicking it opens the full-screen settings dialog via MenuHome.showWaeSettingsDialog().
 */
public class SettingsInjector extends Feature {

    private final android.os.Handler hunterHandler = new android.os.Handler(android.os.Looper.getMainLooper());

    public SettingsInjector(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var entryPoint = getSafeString("open_wae", "1");
        XposedBridge.log("[WaEnhancer] SettingsInjector: entryPoint is " + entryPoint);
        if (!"2".equals(entryPoint)) return;

        // Hook RecyclerView attachment to inject tile into WA's Settings list
        XposedHelpers.findAndHookMethod(android.view.View.class, "onAttachedToWindow", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                View view = (View) param.thisObject;
                String className = view.getClass().getName();
                if (className.contains("RecyclerView")) {
                    hunterHandler.post(() -> {
                        try {
                            checkAndInject((ViewGroup) view);
                        } catch (Throwable t) {
                            XposedBridge.log("[WaEnhancer] SettingsInjector post-hook error: " + t.getMessage());
                        }
                    });
                }
            }
        });
    }

    private void checkAndInject(ViewGroup recyclerView) {
        checkAndInject(recyclerView, 0);
    }

    private void checkAndInject(ViewGroup recyclerView, int attempt) {
        Activity activity = Utils.getActivityFromView(recyclerView);
        if (activity == null) return;

        boolean isSettingsScreen = false;
        try {
            Class<?> settingsActivityClass = Unobfuscator.loadSettingsActivityClass(classLoader);
            if (settingsActivityClass != null && settingsActivityClass.isInstance(activity)) {
                isSettingsScreen = true;
            }
        } catch (Throwable t) {
            // Fallback to markers if Unobfuscator fails
            isSettingsScreen = hasSettingsMarkers(recyclerView);
        }

        if (isSettingsScreen) {
            if (!isAlreadyInjected(recyclerView)) {
                Object adapter = XposedHelpers.callMethod(recyclerView, "getAdapter");
                if (adapter == null && attempt < 3) {
                    XposedBridge.log("[WaEnhancer] SettingsInjector: Adapter null, retrying in 500ms... (attempt " + (attempt + 1) + ")");
                    hunterHandler.postDelayed(() -> checkAndInject(recyclerView, attempt + 1), 500);
                    return;
                }
                
                XposedBridge.log("[WaEnhancer] SettingsInjector: Detected Settings screen in " + activity.getClass().getSimpleName());
                boolean success = injectIntoRecyclerView(recyclerView, activity);
                XposedBridge.log("[WaEnhancer] SettingsInjector: Injection success = " + success);
            }
        }
    }

    private boolean isAlreadyInjected(ViewGroup recyclerView) {
        try {
            for (int i = 0; i < Math.min(3, recyclerView.getChildCount()); i++) {
                View child = recyclerView.getChildAt(i);
                if (child instanceof ViewGroup && findTextRecursive((ViewGroup) child, "WaEnhancer Settings"))
                    return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private boolean findTextRecursive(ViewGroup group, String text) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView && ((TextView) child).getText().toString().equals(text)) return true;
            if (child instanceof ViewGroup && findTextRecursive((ViewGroup) child, text)) return true;
        }
        return false;
    }

    private boolean hasSettingsMarkers(ViewGroup group) {
        int[] count = {0};
        String[] markers = {"Account", "Privacy", "Storage", "Help", "Settings", "Lists", "Chats", "Notifications"};
        boolean found = findMarkersRecursive(group, markers, count);
        if (found) {
            XposedBridge.log("[WaEnhancer] SettingsInjector: Found " + count[0] + " settings markers in " + group.getClass().getSimpleName());
        }
        return found;
    }

    private boolean findMarkersRecursive(ViewGroup group, String[] markers, int[] count) {
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child instanceof TextView) {
                String text = ((TextView) child).getText().toString();
                for (String marker : markers) {
                    if (text.equals(marker)) {
                        count[0]++;
                        if (count[0] >= 2) return true;
                    }
                }
            } else if (child instanceof ViewGroup) {
                if (findMarkersRecursive((ViewGroup) child, markers, count)) return true;
            }
        }
        return false;
    }

    private boolean injectIntoRecyclerView(ViewGroup recyclerView, Activity activity) {
        try {
            Object adapter = XposedHelpers.callMethod(recyclerView, "getAdapter");
            if (adapter == null) {
                XposedBridge.log("[WaEnhancer] SettingsInjector: Adapter is null for " + recyclerView.getClass().getSimpleName());
                return false;
            }
            XposedBridge.log("[WaEnhancer] SettingsInjector: Adapter class is " + adapter.getClass().getName());

            java.lang.reflect.Method addHeaderMethod = null;
            try {
                addHeaderMethod = Unobfuscator.loadViewAddSearchBarMethod(classLoader);
            } catch (Exception e) {
                XposedBridge.log("[WaEnhancer] SettingsInjector: Default addHeader method not found, searching hierarchy for fallbacks...");
                // Fallback: search hierarchy for any method that takes a single View and has "Header" or "Search" in its name
                Class<?> currentClass = adapter.getClass();
                while (currentClass != null && currentClass != Object.class && addHeaderMethod == null) {
                    for (java.lang.reflect.Method m : currentClass.getDeclaredMethods()) {
                        if (m.getParameterCount() == 1 && m.getParameterTypes()[0] == View.class && 
                            (m.getName().toLowerCase().contains("header") || m.getName().toLowerCase().contains("search"))) {
                            m.setAccessible(true);
                            addHeaderMethod = m;
                            XposedBridge.log("[WaEnhancer] SettingsInjector: Found potential fallback method: " + m.getName() + " in " + currentClass.getName());
                            break;
                        }
                    }
                    currentClass = currentClass.getSuperclass();
                }
            }

            if (addHeaderMethod != null) {
                XposedBridge.log("[WaEnhancer] SettingsInjector: Using method: " + addHeaderMethod.getName() + " in " + addHeaderMethod.getDeclaringClass().getName());
                View customRow = createCustomRow(activity);
                addHeaderMethod.setAccessible(true);
                if (addHeaderMethod.getParameterCount() == 1) {
                    addHeaderMethod.invoke(adapter, customRow);
                } else if (addHeaderMethod.getParameterCount() == 2 && addHeaderMethod.getParameterTypes()[1] == int.class) {
                    addHeaderMethod.invoke(adapter, customRow, 0);
                }
                XposedBridge.log("[WaEnhancer] SettingsInjector: Successfully called " + addHeaderMethod.getName() + " on adapter");
                return true;
            } else {
                XposedBridge.log("[WaEnhancer] SettingsInjector: No suitable injection method found in " + adapter.getClass().getSimpleName() + " hierarchy");
            }
        } catch (Exception e) {
            XposedBridge.log("[WaEnhancer] SettingsInjector: Tile injection error: " + e.getMessage());
            e.printStackTrace();
        }
        return false;
    }

    private View createCustomRow(Activity activity) {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        row.setPadding(Utils.dipToPixels(16), Utils.dipToPixels(12),
                Utils.dipToPixels(16), Utils.dipToPixels(12));
        row.setClickable(true);
        row.setFocusable(true);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        row.setBackground(DesignUtils.getSelectableItemBackground(activity));

        android.widget.ImageView icon = new android.widget.ImageView(activity);
        var iconDraw = DesignUtils.getDrawableByName("ic_settings");
        iconDraw.setTint(0xff8696a0);
        icon.setImageDrawable(iconDraw);
        var iconParams = new LinearLayout.LayoutParams(Utils.dipToPixels(24), Utils.dipToPixels(24));
        iconParams.rightMargin = Utils.dipToPixels(32);
        icon.setLayoutParams(iconParams);

        TextView textView = new TextView(activity);
        textView.setText("WaEnhancer Settings");
        textView.setTextSize(16);
        textView.setTextColor(DesignUtils.getPrimaryTextColor());

        row.addView(icon);
        row.addView(textView);
        row.setOnClickListener(v -> MenuHome.showWaeSettingsDialog(activity));

        return row;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Settings Injector";
    }
}
