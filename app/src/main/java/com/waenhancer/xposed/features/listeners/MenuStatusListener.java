package com.waenhancer.xposed.features.listeners;

import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.NonNull;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.utils.ReflectionUtils;

import org.luckypray.dexkit.query.enums.StringMatchType;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import de.robv.android.xposed.XC_MethodHook;
import android.content.SharedPreferences;
import de.robv.android.xposed.XposedBridge;

public class MenuStatusListener extends Feature {

    public static HashSet<onMenuItemStatusListener> menuStatuses = new HashSet<>();

    public static synchronized void registerStatusListener(onMenuItemStatusListener listener) {
        menuStatuses.removeIf(l -> l.getClass().getName().equals(listener.getClass().getName()));
        menuStatuses.add(listener);
    }

    public MenuStatusListener(@NonNull ClassLoader classLoader, @NonNull SharedPreferences preferences) {
        super(classLoader, preferences);
    }

    @Override
    public void doHook() throws Throwable {
        var menuStatusMethod = Unobfuscator.loadMenuStatusMethod(classLoader);
        var menuManagerClass = Unobfuscator.loadMenuManagerClass(classLoader);
        Class<?> statusPlaybackContactFragmentClass = resolveStatusPlaybackContactFragmentClass();
        Class<?> statusPlaybackBaseFragmentClass = resolveStatusPlaybackBaseFragmentClass(statusPlaybackContactFragmentClass);

        XposedBridge.hookMethod(menuStatusMethod, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                try {
                    var fieldObjects = getAllFieldValues(param.thisObject);

                    Object fragmentInstance;
                    if (param.thisObject != null && statusPlaybackContactFragmentClass.isInstance(param.thisObject)) {
                        fragmentInstance = param.thisObject;
                    } else {
                        fragmentInstance = fieldObjects.stream()
                                .filter(Objects::nonNull)
                                .filter(obj -> statusPlaybackContactFragmentClass.isInstance(obj) || statusPlaybackBaseFragmentClass.isInstance(obj))
                                .findFirst()
                                .orElse(null);
                    }
                    if (fragmentInstance == null) {
                        logDebug("MenuStatusListener: fragmentInstance is null");
                        return;
                    }

                    Menu menu;
                    if (param.args.length > 0 && param.args[0] instanceof Menu) {
                        menu = (Menu) param.args[0];
                    } else {
                        var menuManager = fieldObjects.stream().filter(menuManagerClass::isInstance).findFirst().orElse(null);
                        var menuField = ReflectionUtils.getFieldByExtendType(menuManagerClass, Menu.class);
                        menu = menuField == null ? null : (Menu) ReflectionUtils.getObjectField(menuField, menuManager);
                    }
                    if (menu == null) {
                        logDebug("MenuStatusListener: menu is null");
                        return;
                    }

                    Object object = resolveCurrentStatusObject(fragmentInstance);
                    if (object == null) {
                        logDebug("MenuStatusListener: unable to resolve current status object");
                        return;
                    }

                    for (onMenuItemStatusListener menuStatus : menuStatuses) {
                        Object fMessageObject = ReflectionUtils.findFMessageInObject(object, FMessageWpp.TYPE, FMessageWpp.Key.TYPE, classLoader);
                        FMessageWpp fMessage = fMessageObject != null ? new FMessageWpp(fMessageObject) : null;
                        var menuItem = fMessage != null ? menuStatus.addMenu(menu, fMessage) : menuStatus.addRawMenu(menu, object);
                        if (menuItem == null) continue;
                        CharSequence title = menuItem.getTitle();
                        if (title == null || title.length() == 0) {
                            menu.removeItem(menuItem.getItemId());
                            continue;
                        }
                        menuItem.setOnMenuItemClickListener(item -> {
                            if (fMessage != null) {
                                menuStatus.onClick(item, fragmentInstance, fMessage);
                            } else {
                                menuStatus.onRawClick(item, fragmentInstance, object);
                            }
                            return true;
                        });
                    }
                } catch (Throwable t) {
                    XposedBridge.log("[WAE] MenuStatusListener error in hook: " + t);
                    for (var element : t.getStackTrace()) {
                        XposedBridge.log("    at " + element.toString());
                    }
                }
            }
        });
    }

    private Class<?> resolveStatusPlaybackContactFragmentClass() throws Exception {
        try {
            return Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith,
                    "StatusPlaybackContactFragment");
        } catch (Throwable ignored) {
            return Unobfuscator.loadStatusActivePage(classLoader).getDeclaringClass();
        }
    }

    private Class<?> resolveStatusPlaybackBaseFragmentClass(Class<?> contactClass) {
        try {
            return Unobfuscator.findFirstClassUsingName(classLoader, StringMatchType.EndsWith,
                    "StatusPlaybackBaseFragment");
        } catch (Throwable ignored) {
            return contactClass != null && contactClass.getSuperclass() != null ? contactClass.getSuperclass() : Object.class;
        }
    }

    private List<Object> getAllFieldValues(Object instance) {
        if (instance == null) return List.of();
        List<Object> values = new ArrayList<>();
        Class<?> current = instance.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                field.setAccessible(true);
                Object value = ReflectionUtils.getObjectField(field, instance);
                if (value != null) {
                    values.add(value);
                }
            }
            current = current.getSuperclass();
        }
        return values;
    }

    private Object resolveCurrentStatusObject(Object fragmentInstance) {
        if (fragmentInstance == null) return null;

        Object directMatch = ReflectionUtils.findFMessageInObject(fragmentInstance, FMessageWpp.TYPE, FMessageWpp.Key.TYPE, classLoader);
        if (directMatch != null) {
            return directMatch;
        }

        List<Field> listFields = ReflectionUtils.getFieldsByExtendType(fragmentInstance.getClass(), List.class);
        List<Field> indexFields = ReflectionUtils.getFieldsByType(fragmentInstance.getClass(), int.class);

        for (Field listField : listFields) {
            Object value = ReflectionUtils.getObjectField(listField, fragmentInstance);
            if (!(value instanceof List<?> listStatus) || listStatus.isEmpty()) {
                continue;
            }

            Object indexedObject = resolveStatusObjectByIndex(fragmentInstance, listStatus, indexFields);
            if (indexedObject != null) {
                return indexedObject;
            }

            if (listStatus.size() == 1) {
                return listStatus.get(0);
            }
        }

        for (Object fieldObject : getAllFieldValues(fragmentInstance)) {
            Object nestedMatch = ReflectionUtils.findFMessageInObject(fieldObject, FMessageWpp.TYPE, FMessageWpp.Key.TYPE, classLoader);
            if (nestedMatch != null) {
                return nestedMatch;
            }
        }

        return null;
    }

    private Object resolveStatusObjectByIndex(Object fragmentInstance, List<?> listStatus, List<Field> indexFields) {
        for (String preferredFieldName : new String[]{"A00", "A01", "A02", "A03", "A04", "A05"}) {
            for (Field field : indexFields) {
                if (preferredFieldName.equals(field.getName())) {
                    Object candidate = getStatusObjectFromIndexField(field, fragmentInstance, listStatus);
                    if (candidate != null) {
                        return candidate;
                    }
                }
            }
        }

        for (Field field : indexFields) {
            Object candidate = getStatusObjectFromIndexField(field, fragmentInstance, listStatus);
            if (candidate != null) {
                return candidate;
            }
        }

        return null;
    }

    private Object getStatusObjectFromIndexField(Field field, Object fragmentInstance, List<?> listStatus) {
        try {
            int index = field.getInt(fragmentInstance);
            if (index < 0 || index >= listStatus.size()) {
                return null;
            }

            Object candidate = listStatus.get(index);
            if (candidate == null) {
                return null;
            }

            return ReflectionUtils.findFMessageInObject(candidate, FMessageWpp.TYPE, FMessageWpp.Key.TYPE, classLoader) != null
                    ? candidate
                    : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Menu Status";
    }

    public abstract static class onMenuItemStatusListener {

        public abstract MenuItem addMenu(Menu menu, FMessageWpp fMessage);

        public abstract void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp);

        public MenuItem addRawMenu(Menu menu, Object statusObject) {
            return null;
        }

        public void onRawClick(MenuItem item, Object fragmentInstance, Object statusObject) {
        }
    }
}
