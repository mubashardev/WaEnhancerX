package com.waenhancer.xposed.features.others;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class GroupAdmin extends Feature {

    public GroupAdmin(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        boolean enabled = prefs.getBoolean("admin_grp", false)
            || prefs.getBoolean("show_admin_group_icon", false)
            || (!prefs.contains("admin_grp") && !prefs.contains("show_admin_group_icon"));
        if (!enabled) return;
        
        var jidFactory = Unobfuscator.loadJidFactory(classLoader);
        var grpAdmin1 = Unobfuscator.loadGroupAdminMethod(classLoader);
        var grpcheckAdmin = Unobfuscator.loadGroupCheckAdminMethod(classLoader);
        var fMessageClass = Unobfuscator.loadFMessageClass(classLoader);
        Class<?> conversationRowClass = null;
        try {
            conversationRowClass = Unobfuscator.loadConversationRowClass(classLoader);
        } catch (Throwable ignored) {
        }
        
        var hooked = new XC_MethodHook() {
            @SuppressLint("ResourceType")
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    Object targetObj = param.thisObject;
                    Object[] args = param.args;

                    if (targetObj == null && args != null && args.length > 0) {
                        for (Object arg : args) {
                            if (arg == null) continue;
                            if (arg instanceof View || XposedHelpers.findMethodExactIfExists(arg.getClass(), "getFMessage") != null) {
                                targetObj = arg;
                                break;
                            }
                        }
                    }

                    if (targetObj == null) return;

                    Object fMessageObj = null;
                    if (XposedHelpers.findMethodExactIfExists(targetObj.getClass(), "getFMessage") != null) {
                        fMessageObj = XposedHelpers.callMethod(targetObj, "getFMessage");
                    }
                    
                    if (fMessageObj == null && args != null) {
                        for (Object arg : args) {
                            if (arg != null && fMessageClass.isAssignableFrom(arg.getClass())) {
                                fMessageObj = arg;
                                break;
                            }
                        }
                    }

                    if (fMessageObj == null) return;
                    
                    var fMessage = new FMessageWpp(fMessageObj);
                    var userJid = fMessage.getUserJid();
                    if (userJid == null || userJid.userJid == null) return;
                    
                    var chatCurrentJid = resolveGroupJid(fMessage);
                    if (chatCurrentJid == null || !chatCurrentJid.isGroup()) return;

                    // Try to find the View
                    View rowView = targetObj instanceof View ? (View) targetObj : null;
                    if (rowView == null) {
                        rowView = extractViewFromObject(targetObj);
                    }
                    if (rowView == null) return;

                    // Try to find GroupParticipantManager instance
                    Object manager = null;
                    Class<?> managerClass = grpcheckAdmin.getDeclaringClass();
                    
                    // Search in the target object fields
                    Field managerField = ReflectionUtils.findFieldUsingFilterIfExists(targetObj.getClass(), f -> managerClass.isAssignableFrom(f.getType()));
                    if (managerField != null) {
                        manager = managerField.get(targetObj);
                    }
                    
                    // Search in Activity
                    if (manager == null) {
                        Context ctx = rowView.getContext();
                        while (ctx instanceof android.content.ContextWrapper && !(ctx instanceof android.app.Activity)) {
                            ctx = ((android.content.ContextWrapper) ctx).getBaseContext();
                        }
                        if (ctx instanceof android.app.Activity) {
                            Field f = ReflectionUtils.findFieldUsingFilterIfExists(ctx.getClass(), field -> managerClass.isAssignableFrom(field.getType()));
                            if (f != null) {
                                manager = f.get(ctx);
                            }
                        }
                    }

                    if (manager == null && args != null) {
                        for (Object arg : args) {
                            if (arg != null && managerClass.isAssignableFrom(arg.getClass())) {
                                manager = arg;
                                break;
                            }
                        }
                    }

                    if (manager == null) return;

                    Object jidGrp;
                    String groupRawJid = chatCurrentJid.getPhoneRawString();
                    if (Modifier.isStatic(jidFactory.getModifiers())) {
                        jidGrp = jidFactory.invoke(null, groupRawJid);
                    } else {
                        jidGrp = XposedHelpers.callStaticMethod(jidFactory.getDeclaringClass(), "A03", groupRawJid);
                    }
                    
                    if (jidGrp == null) return;

                    var result = grpcheckAdmin.invoke(manager, jidGrp, userJid.userJid);
                    boolean isAdmin = (result instanceof Boolean && (Boolean) result);
                    
                    // Fallback for LIDs
                    if (!isAdmin && userJid.phoneJid != null && !userJid.phoneJid.equals(userJid.userJid)) {
                        Object res = grpcheckAdmin.invoke(manager, jidGrp, userJid.phoneJid);
                        isAdmin = (res instanceof Boolean && (Boolean) res);
                    }

                    TextView iconAdmin;
                    if ((iconAdmin = rowView.findViewById(0x7fff0010)) == null) {
                        ViewGroup nameGroup = rowView.findViewById(Utils.getID("name_in_group", "id"));
                        if (nameGroup == null) {
                            nameGroup = findNameContainer(rowView);
                        }
                        if (nameGroup == null) return;
                        
                        iconAdmin = new TextView(rowView.getContext());
                        iconAdmin.setId(0x7fff0010);
                        iconAdmin.setTextSize(12);
                        
                        String adminEmoji = prefs.getString("admin_emoji", "👑");
                        if (adminEmoji == null || adminEmoji.isEmpty()) {
                            adminEmoji = "👑";
                        }
                        iconAdmin.setText(adminEmoji);
                        
                        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.WRAP_CONTENT, 
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        );
                        params.leftMargin = Utils.dipToPixels(4);
                        params.gravity = Gravity.CENTER_VERTICAL;
                        iconAdmin.setLayoutParams(params);
                        
                        nameGroup.addView(iconAdmin);
                    }
                    iconAdmin.setVisibility(isAdmin ? View.VISIBLE : View.GONE);
                } catch (Throwable t) {
                    XposedBridge.log("GroupAdmin HOOK ERROR: " + t.getMessage());
                }
            }
        };

        Set<Method> bindMethods = collectBindCandidates(grpAdmin1, fMessageClass);
        if (conversationRowClass != null) {
            bindMethods.addAll(collectBindCandidatesFromClass(conversationRowClass, fMessageClass));
        }
        for (Method method : bindMethods) {
            XposedBridge.hookMethod(method, hooked);
        }
    }

    private Set<Method> collectBindCandidates(@NonNull Method primaryMethod, @NonNull Class<?> fMessageClass) {
        Set<Method> result = new LinkedHashSet<>();
        result.add(primaryMethod);

        Class<?> rowClass = primaryMethod.getDeclaringClass();
        for (Method method : rowClass.getDeclaredMethods()) {
            if (method.isSynthetic()) continue;
            if (method.getReturnType() != Void.TYPE) continue;
            if (method.getParameterCount() == 0 || method.getParameterCount() > 4) continue;

            boolean looksLikeBind = false;
            for (Class<?> paramType : method.getParameterTypes()) {
                if (fMessageClass.isAssignableFrom(paramType) || paramType.getName().contains("FMessage")) {
                    looksLikeBind = true;
                    break;
                }
            }

            if (!looksLikeBind && Modifier.isStatic(method.getModifiers())) {
                Class<?>[] params = method.getParameterTypes();
                if (params.length > 0 && rowClass.isAssignableFrom(params[0])) {
                    looksLikeBind = true;
                }
            }

            if (looksLikeBind) {
                method.setAccessible(true);
                result.add(method);
            }
        }

        return result;
    }

    private Set<Method> collectBindCandidatesFromClass(@NonNull Class<?> rowClass, @NonNull Class<?> fMessageClass) {
        Set<Method> result = new HashSet<>();
        Class<?> cursor = rowClass;
        while (cursor != null && cursor != Object.class) {
            for (Method method : cursor.getDeclaredMethods()) {
                if (method.isSynthetic()) continue;
                if (method.getParameterCount() == 0 || method.getParameterCount() > 7) continue;

                boolean looksLikeBind = false;
                for (Class<?> paramType : method.getParameterTypes()) {
                    if (fMessageClass.isAssignableFrom(paramType)
                            || paramType.getName().contains("FMessage")
                            || paramType.getName().contains("12L")
                            || paramType.getName().contains("1Z7")) {
                        looksLikeBind = true;
                        break;
                    }
                }

                if (!looksLikeBind && Modifier.isStatic(method.getModifiers())) {
                    Class<?>[] params = method.getParameterTypes();
                    if (params.length > 0 && rowClass.isAssignableFrom(params[0])) {
                        looksLikeBind = true;
                    }
                }

                if (looksLikeBind) {
                    method.setAccessible(true);
                    result.add(method);
                }
            }
            cursor = cursor.getSuperclass();
        }
        return result;
    }

    private ViewGroup findNameContainer(@NonNull View root) {
        if (root instanceof LinearLayout) {
            LinearLayout layout = (LinearLayout) root;
            for (int i = 0; i < layout.getChildCount(); i++) {
                if (layout.getChildAt(i) instanceof TextView) {
                    return layout;
                }
            }
        }
        if (root instanceof ViewGroup) {
            ViewGroup vg = (ViewGroup) root;
            for (int i = 0; i < vg.getChildCount(); i++) {
                var child = vg.getChildAt(i);
                var found = findNameContainer(child);
                if (found != null) return (ViewGroup) found;
            }
        }
        return null;
    }

    private View extractViewFromObject(Object holder) {
        if (holder == null) return null;
        if (holder instanceof View) return (View) holder;
        Class<?> cursor = holder.getClass();
        while (cursor != null && cursor != Object.class) {
            for (Field field : cursor.getDeclaredFields()) {
                if (!View.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(holder);
                    if (value instanceof View) {
                        return (View) value;
                    }
                } catch (Throwable ignored) {
                }
            }
            cursor = cursor.getSuperclass();
        }
        return null;
    }

    private FMessageWpp.UserJid resolveGroupJid(@NonNull FMessageWpp fMessage) {
        try {
            var key = fMessage.getKey();
            if (key != null && key.remoteJid != null && key.remoteJid.isGroup()) {
                return key.remoteJid;
            }
        } catch (Throwable ignored) {
        }

        try {
            var currentJid = WppCore.getCurrentUserJid();
            if (currentJid != null && currentJid.isGroup()) {
                return currentJid;
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GroupAdmin";
    }
}
