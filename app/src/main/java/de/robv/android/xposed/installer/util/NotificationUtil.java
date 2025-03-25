package de.robv.android.xposed.installer.util;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.WelcomeActivity;
import de.robv.android.xposed.installer.XposedApp;

public final class NotificationUtil {
    public static final int NOTIFICATION_MODULE_NOT_ACTIVATED_YET = 0;
    public static final int NOTIFICATION_MODULES_UPDATED = 1;
    private static final int PENDING_INTENT_OPEN_MODULES = 0;
    private static final int PENDING_INTENT_OPEN_INSTALL = 1;
    private static Context sContext = null;
    private static NotificationManager sNotificationManager;

    public static void init() {
        if (sContext != null)
            throw new IllegalStateException(
                    "NotificationUtil has already been initialized");

        sContext = XposedApp.getInstance();
        sNotificationManager = (NotificationManager) sContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    public static void showNotActivatedNotification(String packageName,
                                                    String appName) {
        Intent intent = new Intent(sContext, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("fragment", 1);

        PendingIntent pModulesTab = PendingIntent.getActivity(sContext, PENDING_INTENT_OPEN_MODULES, intent, PendingIntent.FLAG_UPDATE_CURRENT);

        String title = sContext.getString(R.string.module_is_not_activated_yet);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(sContext).setContentTitle(title).setContentText(appName)
                .setTicker(title).setContentIntent(pModulesTab)
                .setVibrate(new long[]{0}).setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(sContext.getResources().getColor(R.color.colorPrimary));

        if (Build.VERSION.SDK_INT >= 21)
            builder.setPriority(2);

        NotificationCompat.BigTextStyle notiStyle = new NotificationCompat.BigTextStyle();
        notiStyle.setBigContentTitle(title);
        notiStyle.bigText(sContext.getString(
                R.string.module_is_not_activated_yet_detailed, appName));
        builder.setStyle(notiStyle);


        sNotificationManager.notify(packageName,
                NOTIFICATION_MODULE_NOT_ACTIVATED_YET, builder.build());
    }

    @SuppressWarnings("deprecation")
    public static void showModulesUpdatedNotification() {
        Intent intent = new Intent(sContext, WelcomeActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra("fragment", 0);

        PendingIntent pInstallTab = PendingIntent.getActivity(sContext,
                PENDING_INTENT_OPEN_INSTALL, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        String title = sContext
                .getString(R.string.xposed_module_updated_notification_title);
        String message = sContext
                .getString(R.string.xposed_module_updated_notification);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                sContext).setContentTitle(title).setContentText(message)
                .setTicker(title).setContentIntent(pInstallTab)
                .setVibrate(new long[]{0}).setAutoCancel(true)
                .setSmallIcon(R.drawable.ic_notification)
                .setColor(sContext.getResources().getColor(R.color.colorPrimary));

        if (Build.VERSION.SDK_INT >= 21)
            builder.setPriority(2);

        sNotificationManager.notify(null, NOTIFICATION_MODULES_UPDATED, builder.build());
    }
}
