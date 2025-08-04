package de.robv.android.xposed.installer;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Application;
import android.app.Application.ActivityLifecycleCallbacks;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.annotation.Keep;
import android.util.Log;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import de.robv.android.xposed.installer.util.FileUtils;
import de.robv.android.xposed.installer.util.InstallZipUtil;
import de.robv.android.xposed.installer.util.InstallZipUtil.XposedProp;
import de.robv.android.xposed.installer.util.NotificationUtil;
import de.robv.android.xposed.installer.util.RepoLoader;

public class XposedApp extends Application implements ActivityLifecycleCallbacks {
    public static final String TAG = "XposedInstaller";

    @SuppressLint("SdCardPath")
    private static final String BASE_DIR_LEGACY = "/data/data/de.robv.android.xposed.installer/";

    public static final String BASE_DIR = Build.VERSION.SDK_INT >= 24
            ? "/data/user_de/0/de.robv.android.xposed.installer/" : BASE_DIR_LEGACY;

    public static final String ENABLED_MODULES_LIST_FILE = XposedApp.BASE_DIR + "conf/enabled_modules.list";

    private static final String[] XPOSED_PROP_FILES = new String[]{
            "/sbin/xposed.prop",      // official systemless
            "/system/xposed.prop",    // classical
    };

    private static XposedApp mInstance = null;
    private static Thread mUiThread;
    private static Handler mMainHandler;
    private boolean mIsUiLoaded = false;
    private SharedPreferences mPref;
    private XposedProp mXposedProp;

    public static XposedApp getInstance() {
        return mInstance;
    }

    public static void runOnUiThread(Runnable action) {
        if (Thread.currentThread() != mUiThread) {
            mMainHandler.post(action);
        } else {
            action.run();
        }
    }

    // This method is hooked by XposedBridge to return the current version
    @Keep
    public static int getActiveXposedVersion() {
        return -1;
    }

    public static int getInstalledXposedVersion() {
        XposedProp prop = getXposedProp();
        return prop != null ? prop.getVersionInt() : -1;
    }

    public static XposedProp getXposedProp() {
        synchronized (mInstance) {
            return mInstance.mXposedProp;
        }
    }

    public static SharedPreferences getPreferences() {
        return mInstance.mPref;
    }

    public void onCreate() {
        super.onCreate();
        mInstance = this;
        mUiThread = Thread.currentThread();
        mMainHandler = new Handler();

        mPref = PreferenceManager.getDefaultSharedPreferences(this);
        reloadXposedProp();
        createDirectories();
        NotificationUtil.init();

        registerActivityLifecycleCallbacks(this);
    }

    private void createDirectories() {
        FileUtils.setPermissions(BASE_DIR, 00711, -1, -1);
        mkdirAndChmod("conf", 00771);
        mkdirAndChmod("log", 00777);

        if (Build.VERSION.SDK_INT >= 24) {
            FileUtils.deleteContentsAndDir(new File(BASE_DIR_LEGACY, "bin"));
            FileUtils.deleteContentsAndDir(new File(BASE_DIR_LEGACY, "conf"));
            FileUtils.deleteContentsAndDir(new File(BASE_DIR_LEGACY, "log"));
        }
    }

    private void mkdirAndChmod(String dir, int permissions) {
        dir = BASE_DIR + dir;
        new File(dir).mkdir();
        FileUtils.setPermissions(dir, permissions, -1, -1);
    }

    public void reloadXposedProp() {
        XposedProp prop = null;

        for (String path : XPOSED_PROP_FILES) {
            File file = new File(path);
            if (file.canRead()) {
                FileInputStream is = null;
                try {
                    is = new FileInputStream(file);
                    prop = InstallZipUtil.parseXposedProp(is);
                    break;
                } catch (IOException e) {
                    Log.e(XposedApp.TAG, "Could not read " + file.getPath(), e);
                } finally {
                    if (is != null) {
                        try {
                            is.close();
                        } catch (IOException ignored) {
                        }
                    }
                }
            }
        }

        synchronized (this) {
            mXposedProp = prop;
        }
    }

    // TODO find a better way to trigger actions only when any UI is shown for the first time
    @Override
    public synchronized void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        if (mIsUiLoaded)
            return;

        RepoLoader.getInstance().triggerFirstLoadIfNecessary();
        mIsUiLoaded = true;
    }

    @Override
    public synchronized void onActivityResumed(Activity activity) {
    }

    @Override
    public synchronized void onActivityPaused(Activity activity) {
    }

    @Override
    public void onActivityStarted(Activity activity) {
    }

    @Override
    public void onActivityStopped(Activity activity) {
    }

    @Override
    public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
    }

    @Override
    public void onActivityDestroyed(Activity activity) {
    }
}
