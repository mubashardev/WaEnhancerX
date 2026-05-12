package com.waenhancer.xposed.features.media;

import static com.waenhancer.xposed.features.listeners.MenuStatusListener.menuStatuses;

import android.content.Intent;
import android.net.Uri;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.content.FileProvider;

import com.waenhancer.xposed.core.Feature;
import com.waenhancer.xposed.core.WppCore;
import com.waenhancer.xposed.core.components.FMessageWpp;
import com.waenhancer.xposed.core.db.MessageStore;
import com.waenhancer.xposed.core.devkit.Unobfuscator;
import com.waenhancer.xposed.features.listeners.MenuStatusListener;
import com.waenhancer.xposed.utils.MimeTypeUtils;
import com.waenhancer.xposed.utils.ReflectionUtils;
import com.waenhancer.R;
import com.waenhancer.xposed.utils.Utils;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import de.robv.android.xposed.XposedBridge;
import android.content.SharedPreferences;
import de.robv.android.xposed.XSharedPreferences;

public class StatusDownload extends Feature {

    // Use dedicated unique IDs that won't collide with WhatsApp's
    // dynamically-resolved
    // string resource IDs (which are unpredictable across WA versions/obfuscation
    // passes).
    private static final int MENU_ID_DOWNLOAD = 0x7EAD0001;
    private static final int MENU_ID_SHARE_STATUS = 0x7EAD0002;

    public StatusDownload(ClassLoader loader, SharedPreferences preferences) {
        super(loader, preferences);
    }

    public void doHook() throws Exception {
        if (!prefs.getBoolean("downloadstatus", false))
            return;

        var downloadStatus = new MenuStatusListener.onMenuItemStatusListener() {

            @Override
            public MenuItem addMenu(Menu menu, FMessageWpp fMessage) {
                // Guard against duplicate entries using our own unique ID
                if (menu.findItem(MENU_ID_DOWNLOAD) != null)
                    return null;
                if (fMessage.getKey().isFromMe)
                    return null;
                if (!fMessage.isMediaFile())
                    return null;
                return menu.add(0, MENU_ID_DOWNLOAD, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.R.string.download, "Download"));
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp) {
                downloadFile(fMessageWpp);
            }

            @Override
            public MenuItem addRawMenu(Menu menu, Object statusObject) {
                RawStatusData rawStatusData = extractRawStatusData(statusObject);
                if (rawStatusData == null || rawStatusData.isFromMe || rawStatusData.mediaFile == null)
                    return null;
                if (menu.findItem(MENU_ID_DOWNLOAD) != null)
                    return null;
                return menu.add(0, MENU_ID_DOWNLOAD, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.R.string.download, "Download"));
            }

            @Override
            public void onRawClick(MenuItem item, Object fragmentInstance, Object statusObject) {
                downloadRawStatus(statusObject);
            }
        };
        MenuStatusListener.registerStatusListener(downloadStatus);

        var sharedMenu = new MenuStatusListener.onMenuItemStatusListener() {

            @Override
            public MenuItem addMenu(Menu menu, FMessageWpp fMessage) {
                if (fMessage.getKey().isFromMe)
                    return null;
                // Guard against duplicate entries using our own unique ID
                if (menu.findItem(MENU_ID_SHARE_STATUS) != null)
                    return null;
                return menu.add(0, MENU_ID_SHARE_STATUS, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.R.string.share_as_status, "Share as status"));
            }

            @Override
            public void onClick(MenuItem item, Object fragmentInstance, FMessageWpp fMessageWpp) {
                sharedStatus(fMessageWpp);
            }

            @Override
            public MenuItem addRawMenu(Menu menu, Object statusObject) {
                RawStatusData rawStatusData = extractRawStatusData(statusObject);
                if (rawStatusData == null || rawStatusData.isFromMe)
                    return null;
                if (menu.findItem(MENU_ID_SHARE_STATUS) != null)
                    return null;
                return menu.add(0, MENU_ID_SHARE_STATUS, 0, com.waenhancer.xposed.core.FeatureLoader.getModuleString(com.waenhancer.R.string.share_as_status, "Share as status"));
            }

            @Override
            public void onRawClick(MenuItem item, Object fragmentInstance, Object statusObject) {
                shareRawStatus(statusObject);
            }
        };
        MenuStatusListener.registerStatusListener(sharedMenu);
    }

    private void shareRawStatus(Object statusObject) {
        RawStatusData rawStatusData = extractRawStatusData(statusObject);
        if (rawStatusData == null) {
            Utils.showToast("Unable to read current status", Toast.LENGTH_SHORT);
            return;
        }

        try {
            if (rawStatusData.mediaFile == null) {
                Intent intent = new Intent();
                Class clazz;
                try {
                    clazz = Unobfuscator.getClassByName("TextStatusComposerActivity", classLoader);
                } catch (Exception ignored) {
                    clazz = Unobfuscator.getClassByName("ConsolidatedStatusComposerActivity", classLoader);
                    intent.putExtra("status_composer_mode", 2);
                }
                intent.setClassName(Utils.getApplication().getPackageName(), clazz.getName());
                intent.putExtra("android.intent.extra.TEXT", rawStatusData.text);
                WppCore.getCurrentActivity().startActivity(intent);
                return;
            }

            Uri mediaUri;
            try {
                String authority = Utils.getApplication().getPackageName() + ".fileprovider";
                mediaUri = FileProvider.getUriForFile(Utils.getApplication(), authority, rawStatusData.mediaFile);
            } catch (IllegalArgumentException e) {
                XposedBridge.log("WaEnhancer: FileProvider failed for " + rawStatusData.mediaFile.getAbsolutePath() + ": " + e.getMessage());
                mediaUri = Uri.fromFile(rawStatusData.mediaFile);
            }

            Intent intent = new Intent();
            var clazz = Unobfuscator.getClassByName("MediaComposerActivity", classLoader);
            intent.setClassName(Utils.getApplication().getPackageName(), clazz.getName());
            intent.putExtra("jids", new ArrayList<>(Collections.singleton("status@broadcast")));
            intent.putExtra("android.intent.extra.STREAM", new ArrayList<>(Collections.singleton(mediaUri)));
            if (!TextUtils.isEmpty(rawStatusData.text)) {
                intent.putExtra("android.intent.extra.TEXT", rawStatusData.text);
            }
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            WppCore.getCurrentActivity().startActivity(intent);
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: shareRawStatus error: " + e.getMessage());
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    private void downloadRawStatus(Object statusObject) {
        RawStatusData rawStatusData = extractRawStatusData(statusObject);
        if (rawStatusData == null || rawStatusData.mediaFile == null) {
            Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.download_not_available, "Please wait until it is fully downloaded in WhatsApp before trying again."), 1);
            return;
        }

        try {
            var fileType = rawStatusData.mediaFile.getName().substring(rawStatusData.mediaFile.getName().lastIndexOf(".") + 1);
            var destination = getStatusDestination(rawStatusData.mediaFile);
            var name = buildRawStatusFileName(rawStatusData, fileType);
            var error = Utils.copyFile(rawStatusData.mediaFile, destination, name);
            if (TextUtils.isEmpty(error)) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.saved_to, "Saved to: ") + destination,
                        Toast.LENGTH_SHORT);
            } else {
                Utils.showToast(
                        com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.error_when_saving_try_again, "Error when saving, try again") + ": " + error,
                        Toast.LENGTH_SHORT);
            }
        } catch (Throwable e) {
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    private String buildRawStatusFileName(RawStatusData rawStatusData, String fileFormat) {
        String prefix = rawStatusData.messageId;
        if (TextUtils.isEmpty(prefix)) {
            prefix = "status";
        }
        return Utils.toValidFileName(prefix) + "_" + System.currentTimeMillis() + "." + fileFormat;
    }

    private RawStatusData extractRawStatusData(Object statusObject) {
        try {
            Object keyObject = findKeyObject(statusObject, Collections.newSetFromMap(new IdentityHashMap<>()), 0);
            if (keyObject == null) return null;

            String messageId = extractMessageId(keyObject);
            if (TextUtils.isEmpty(messageId)) return null;
            boolean isFromMe = extractIsFromMe(keyObject);
            File mediaFile = findNestedFile(statusObject, Collections.newSetFromMap(new IdentityHashMap<>()), 0);

            MessageStore messageStore = MessageStore.getInstance();
            long rowId = messageStore.getIdfromKey(messageId);
            String text = messageStore.getCurrentMessageByKey(messageId);
            if (mediaFile == null && rowId > 0) {
                String filePath = messageStore.getMediaFromID(rowId);
                if (!TextUtils.isEmpty(filePath)) {
                    File file = new File(filePath);
                    if (file.exists()) {
                        mediaFile = file;
                    }
                }
            }
            return new RawStatusData(messageId, isFromMe, text, mediaFile);
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: extractRawStatusData error: " + e.getMessage());
            return null;
        }
    }

    private String extractMessageId(Object keyObject) {
        try {
            for (Field field : keyObject.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == String.class) {
                    Object value = field.get(keyObject);
                    if (value instanceof String s && !TextUtils.isEmpty(s)) {
                        return s.trim();
                    }
                }
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private boolean extractIsFromMe(Object keyObject) {
        try {
            for (Field field : keyObject.getClass().getDeclaredFields()) {
                field.setAccessible(true);
                if (field.getType() == boolean.class || field.getType() == Boolean.class) {
                    return field.getBoolean(keyObject);
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    private File findNestedFile(Object object, Set<Object> visited, int depth) {
        if (object == null || depth > 6) return null;
        if (!visited.add(object)) return null;

        if (object instanceof File file) {
            return file.exists() ? file : null;
        }

        if (object instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                File match = findNestedFile(item, visited, depth + 1);
                if (match != null) return match;
            }
            return null;
        }

        if (object.getClass().isArray() && !object.getClass().getComponentType().isPrimitive()) {
            Object[] array = (Object[]) object;
            for (Object item : array) {
                File match = findNestedFile(item, visited, depth + 1);
                if (match != null) return match;
            }
            return null;
        }

        Class<?> current = object.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().isPrimitive()) continue;
                field.setAccessible(true);
                Object nested = ReflectionUtils.getObjectField(field, object);
                File match = findNestedFile(nested, visited, depth + 1);
                if (match != null) return match;
            }
            current = current.getSuperclass();
        }

        return null;
    }

    private Object findKeyObject(Object object, Set<Object> visited, int depth) {
        if (object == null || depth > 5) return null;
        if (!visited.add(object)) return null;

        if (FMessageWpp.Key.TYPE != null && FMessageWpp.Key.TYPE.isInstance(object)) {
            return object;
        }

        String value = String.valueOf(object);
        if (value.startsWith("Key(id=") || value.startsWith("Key(")) {
            return object;
        }

        if (object instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                Object match = findKeyObject(item, visited, depth + 1);
                if (match != null) return match;
            }
            return null;
        }

        if (object.getClass().isArray() && !object.getClass().getComponentType().isPrimitive()) {
            Object[] array = (Object[]) object;
            for (Object item : array) {
                Object match = findKeyObject(item, visited, depth + 1);
                if (match != null) return match;
            }
            return null;
        }

        Class<?> current = object.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (field.getType().isPrimitive()) continue;
                field.setAccessible(true);
                Object nested = ReflectionUtils.getObjectField(field, object);
                Object match = findKeyObject(nested, visited, depth + 1);
                if (match != null) return match;
            }
            current = current.getSuperclass();
        }

        return null;
    }

    private static class RawStatusData {
        final String messageId;
        final boolean isFromMe;
        final String text;
        final File mediaFile;

        RawStatusData(String messageId, boolean isFromMe, String text, File mediaFile) {
            this.messageId = messageId;
            this.isFromMe = isFromMe;
            this.text = text;
            this.mediaFile = mediaFile;
        }
    }

    private void sharedStatus(FMessageWpp fMessageWpp) {
        try {
            if (!fMessageWpp.isMediaFile()) {
                // Text-only status: open the text status composer
                Intent intent = new Intent();
                Class clazz;
                try {
                    clazz = Unobfuscator.getClassByName("TextStatusComposerActivity", classLoader);
                } catch (Exception ignored) {
                    clazz = Unobfuscator.getClassByName("ConsolidatedStatusComposerActivity", classLoader);
                    intent.putExtra("status_composer_mode", 2);
                }
                intent.setClassName(Utils.getApplication().getPackageName(), clazz.getName());
                intent.putExtra("android.intent.extra.TEXT", fMessageWpp.getMessageStr());
                WppCore.getCurrentActivity().startActivity(intent);
                return;
            }

            var file = fMessageWpp.getMediaFile();
            if (file == null) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.download_not_available, "Please wait until it is fully downloaded in WhatsApp before trying again."), 1);
                return;
            }

            // Build a content:// URI via FileProvider so Android 7+ doesn't block it.
            // The FileProvider in AndroidManifest covers external-path "." so all WA
            // media files under external storage are reachable.
            Uri mediaUri;
            try {
                String authority = Utils.getApplication().getPackageName() + ".fileprovider";
                mediaUri = FileProvider.getUriForFile(Utils.getApplication(), authority, file);
            } catch (IllegalArgumentException e) {
                // Fallback: if file is outside FileProvider paths, use file:// URI
                // (works on root devices and Android < 7, better than silently failing)
                XposedBridge
                        .log("WaEnhancer: FileProvider failed for " + file.getAbsolutePath() + ": " + e.getMessage());
                mediaUri = Uri.fromFile(file);
            }

            Intent intent = new Intent();
            var clazz = Unobfuscator.getClassByName("MediaComposerActivity", classLoader);
            intent.setClassName(Utils.getApplication().getPackageName(), clazz.getName());
            intent.putExtra("jids", new ArrayList<>(Collections.singleton("status@broadcast")));
            intent.putExtra("android.intent.extra.STREAM", new ArrayList<>(Collections.singleton(mediaUri)));
            // Carry caption text if present
            String caption = fMessageWpp.getMessageStr();
            if (!TextUtils.isEmpty(caption)) {
                intent.putExtra("android.intent.extra.TEXT", caption);
            }
            // Grant read permission to WhatsApp so it can read the content:// URI
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            WppCore.getCurrentActivity().startActivity(intent);
        } catch (Throwable e) {
            XposedBridge.log("WaEnhancer: sharedStatus error: " + e.getMessage());
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    private void downloadFile(FMessageWpp fMessage) {
        try {
            var file = fMessage.getMediaFile();
            if (file == null) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.download_not_available, "Please wait until it is fully downloaded in WhatsApp before trying again."), 1);
                return;
            }
            var userJid = fMessage.getUserJid();
            var fileType = file.getName().substring(file.getName().lastIndexOf(".") + 1);
            var destination = getStatusDestination(file);
            var name = Utils.generateName(userJid, fileType);
            var error = Utils.copyFile(file, destination, name);
            if (TextUtils.isEmpty(error)) {
                Utils.showToast(com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.saved_to, "Saved to: ") + destination,
                        Toast.LENGTH_SHORT);
            } else {
                Utils.showToast(
                        com.waenhancer.xposed.core.FeatureLoader.getModuleString(R.string.error_when_saving_try_again, "Error when saving, try again") + ": " + error,
                        Toast.LENGTH_SHORT);
            }
        } catch (Throwable e) {
            Utils.showToast(e.getMessage(), Toast.LENGTH_SHORT);
        }
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "Download Status";
    }

    @NonNull
    private String getStatusDestination(@NonNull File f) throws Exception {
        var fileName = f.getName().toLowerCase();
        var mimeType = MimeTypeUtils.getMimeTypeFromExtension(fileName);
        var folderPath = "";
        if (mimeType.contains("video")) {
            folderPath = "Status Videos";
        } else if (mimeType.contains("image")) {
            folderPath = "Status Images";
        } else if (mimeType.contains("audio")) {
            folderPath = "Status Sounds";
        } else {
            folderPath = "Status Media";
        }
        return Utils.getDestination(folderPath);
    }

}
