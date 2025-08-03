package de.robv.android.xposed.installer.util;

import android.annotation.SuppressLint;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class FileUtils {

    public static int setPermissions(File path, int mode, int uid, int gid) {
        return setPermissions(path.getAbsoluteFile(), mode, uid, gid);
    }

    public static int setPermissions(String path, int mode, int uid, int gid) {
        Class<?>[] types = {String.class, int.class, int.class, int.class};
        Object ret = doCall("setPermissions", types, path, mode, uid, gid);
        return (ret != null) ? (Integer) ret : -1;
    }

    public static boolean deleteContentsAndDir(File dir) {
        Class<?>[] types = {File.class};
        Object ret = doCall("deleteContentsAndDir", types, dir);
        return (ret != null) ? (Boolean) ret : false;
    }

    @SuppressLint("PrivateApi")
    private static Object doCall(String method, Class<?>[] types, Object... params) {
        try {
            Class<?> clz = Class.forName("android.os.FileUtils");
            Method m = clz.getDeclaredMethod(method, types);
            m.setAccessible(true);
            return m.invoke(null, params);
        } catch (ClassNotFoundException e) {
            // ignore
        } catch (NoSuchMethodException e) {
            // ignore
        } catch (InvocationTargetException e) {
            // ignore
        } catch (IllegalAccessException e) {
            // ignore
        }
        return null;
    }
}
