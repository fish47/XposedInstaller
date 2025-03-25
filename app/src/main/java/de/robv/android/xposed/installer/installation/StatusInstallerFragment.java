package de.robv.android.xposed.installer.installation;

import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.pm.ApplicationInfo;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.SwitchCompat;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Set;

import de.robv.android.xposed.installer.R;
import de.robv.android.xposed.installer.XposedApp;
import de.robv.android.xposed.installer.util.FrameworkZips;
import de.robv.android.xposed.installer.util.InstallZipUtil;
import de.robv.android.xposed.installer.util.NavUtil;

public class StatusInstallerFragment extends Fragment {
    public static final File DISABLE_FILE = new File(XposedApp.BASE_DIR + "conf/disabled");

    private static boolean checkClassExists(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.status_installer, container, false);

        // Disable switch
        final SwitchCompat disableSwitch = (SwitchCompat) v.findViewById(R.id.disableSwitch);
        disableSwitch.setChecked(!DISABLE_FILE.exists());
        disableSwitch.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (DISABLE_FILE.exists()) {
                    DISABLE_FILE.delete();
                    Snackbar.make(disableSwitch, R.string.xposed_on_next_reboot, Snackbar.LENGTH_LONG).show();
                } else {
                    try {
                        DISABLE_FILE.createNewFile();
                        Snackbar.make(disableSwitch, R.string.xposed_off_next_reboot, Snackbar.LENGTH_LONG).show();
                    } catch (IOException e) {
                        Log.e(XposedApp.TAG, "Could not create " + DISABLE_FILE, e);
                    }
                }
            }
        });

        // Device info
        TextView androidSdk = (TextView) v.findViewById(R.id.android_version);
        TextView manufacturer = (TextView) v.findViewById(R.id.ic_manufacturer);
        TextView cpu = (TextView) v.findViewById(R.id.cpu);

        androidSdk.setText(getString(R.string.android_sdk, Build.VERSION.RELEASE, getAndroidVersion(), Build.VERSION.SDK_INT));
        manufacturer.setText(getUIFramework());
        cpu.setText(FrameworkZips.ARCH);

        determineVerifiedBootState(v);

        // Known issues
        refreshKnownIssue(v);

        // Display warning dialog to new users
        if (!XposedApp.getPreferences().getBoolean("hide_install_warning", false)) {
            new MaterialDialog.Builder(getActivity())
                    .title(R.string.install_warning_title)
                    .content(R.string.install_warning)
                    .positiveText(android.R.string.ok)
                    .onPositive(new MaterialDialog.SingleButtonCallback() {
                        @Override
                        public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                            if (dialog.isPromptCheckBoxChecked()) {
                                XposedApp.getPreferences().edit().putBoolean("hide_install_warning", true).apply();
                            }
                        }
                    })
                    .checkBoxPromptRes(R.string.dont_show_again, false, null)
                    .cancelable(false)
                    .show();
        }

        return v;
    }

    @Override
    public void onResume() {
        super.onResume();
        refreshInstallStatus();
    }

    private void refreshInstallStatus() {
        View v = getView();
        TextView txtInstallError = (TextView) v.findViewById(R.id.framework_install_errors);
        View txtInstallContainer = v.findViewById(R.id.status_container);
        ImageView txtInstallIcon = (ImageView) v.findViewById(R.id.status_icon);
        View disableWrapper = v.findViewById(R.id.disableView);

        // TODO This should probably compare the full version string, not just the number part.
        int active = XposedApp.getActiveXposedVersion();
        int installed = XposedApp.getInstalledXposedVersion();
        if (installed < 0) {
            txtInstallError.setText(R.string.framework_not_installed);
            txtInstallError.setTextColor(getResources().getColor(R.color.warning));
            txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.warning));
            txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_error));
            disableWrapper.setVisibility(View.GONE);
        } else if (installed != active) {
            txtInstallError.setText(getString(R.string.framework_not_active, XposedApp.getXposedProp().getVersion()));
            txtInstallError.setTextColor(getResources().getColor(R.color.amber_500));
            txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.amber_500));
            txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_warning));
        } else {
            txtInstallError.setText(getString(R.string.framework_active, XposedApp.getXposedProp().getVersion()));
            txtInstallError.setTextColor(getResources().getColor(R.color.darker_green));
            txtInstallContainer.setBackgroundColor(getResources().getColor(R.color.darker_green));
            txtInstallIcon.setImageDrawable(getResources().getDrawable(R.drawable.ic_check_circle));
        }
    }

    private File getCanonicalFile(File file) {
        try {
            return file.getCanonicalFile();
        } catch (IOException e) {
            Log.e(XposedApp.TAG, "Failed to get canonical file for " + file.getAbsolutePath(), e);
            return file;
        }
    }

    private String getPathWithCanonicalPath(File file, File canonical) {
        if (file.equals(canonical)) {
            return file.getAbsolutePath();
        } else {
            return file.getAbsolutePath() + " \u2192 " + canonical.getAbsolutePath();
        }
    }

    @SuppressLint("StringFormatInvalid")
    private void refreshKnownIssue(View v) {
        final String issueName;
        final String issueLink;
        final ApplicationInfo appInfo = getActivity().getApplicationInfo();
        final File baseDir = new File(XposedApp.BASE_DIR);
        final File baseDirCanonical = getCanonicalFile(baseDir);
        final File baseDirActual = new File(Build.VERSION.SDK_INT >= 24 ? appInfo.deviceProtectedDataDir : appInfo.dataDir);
        final File baseDirActualCanonical = getCanonicalFile(baseDirActual);
        final InstallZipUtil.XposedProp prop = XposedApp.getXposedProp();
        final Set<String> missingFeatures = prop != null ? prop.getMissingInstallerFeatures() : null;

        if (missingFeatures != null && !missingFeatures.isEmpty()) {
            InstallZipUtil.reportMissingFeatures(missingFeatures);
            issueName = getString(R.string.installer_needs_update, getString(R.string.app_name));
            issueLink = getString(R.string.about_support);
        } else if (new File("/system/framework/core.jar.jex").exists()) {
            issueName = "Aliyun OS";
            issueLink = "https://forum.xda-developers.com/showpost.php?p=52289793&postcount=5";
        } else if (Build.VERSION.SDK_INT < 24 && (new File("/data/miui/DexspyInstaller.jar").exists() || checkClassExists("miui.dexspy.DexspyInstaller"))) {
            issueName = "MIUI/Dexspy";
            issueLink = "https://forum.xda-developers.com/showpost.php?p=52291098&postcount=6";
        } else if (Build.VERSION.SDK_INT < 24 && new File("/system/framework/twframework.jar").exists()) {
            issueName = "Samsung TouchWiz ROM";
            issueLink = "https://forum.xda-developers.com/showthread.php?t=3034811";
        } else if (!baseDirCanonical.equals(baseDirActualCanonical)) {
            Log.e(XposedApp.TAG, "Base directory: " + getPathWithCanonicalPath(baseDir, baseDirCanonical));
            Log.e(XposedApp.TAG, "Expected: " + getPathWithCanonicalPath(baseDirActual, baseDirActualCanonical));
            issueName = getString(R.string.known_issue_wrong_base_directory, getPathWithCanonicalPath(baseDirActual, baseDirActualCanonical));
            issueLink = "https://github.com/rovo89/XposedInstaller/issues/395";
        } else if (!baseDir.exists()) {
            issueName = getString(R.string.known_issue_missing_base_directory);
            issueLink = "https://github.com/rovo89/XposedInstaller/issues/393";
        } else {
            issueName = null;
            issueLink = null;
        }

        TextView txtKnownIssue = (TextView) v.findViewById(R.id.framework_known_issue);
        if (issueName != null) {
            txtKnownIssue.setText(getString(R.string.install_known_issue, issueName));
            txtKnownIssue.setVisibility(View.VISIBLE);
            txtKnownIssue.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    NavUtil.startURL(getActivity(), issueLink);
                }
            });
        } else {
            txtKnownIssue.setVisibility(View.GONE);
        }
    }

    private String getAndroidVersion() {
        switch (Build.VERSION.SDK_INT) {
            case 15:
                return "Ice Cream Sandwich";
            case 16:
            case 17:
            case 18:
                return "Jelly Bean";
            case 19:
                return "KitKat";
            case 21:
            case 22:
                return "Lollipop";
            case 23:
                return "Marshmallow";
            case 24:
            case 25:
                return "Nougat";
            case 26:
            case 27:
                return "Oreo";
            default:
                return "unknown";
        }
    }

    private String getUIFramework() {
        String manufacturer = Character.toUpperCase(Build.MANUFACTURER.charAt(0)) + Build.MANUFACTURER.substring(1);
        if (!Build.BRAND.equals(Build.MANUFACTURER)) {
            manufacturer += " " + Character.toUpperCase(Build.BRAND.charAt(0)) + Build.BRAND.substring(1);
        }
        manufacturer += " " + Build.MODEL + " ";
        if (manufacturer.contains("Samsung")) {
            manufacturer += new File("/system/framework/twframework.jar").exists() ? "(TouchWiz)" : "(AOSP-based ROM)";
        } else if (manufacturer.contains("Xioami")) {
            manufacturer += new File("/system/framework/framework-miui-res.apk").exists() ? "(MIUI)" : "(AOSP-based ROM)";
        }
        return manufacturer;
    }

    private void determineVerifiedBootState(View v) {
        try {
            Class<?> c = Class.forName("android.os.SystemProperties");
            Method m = c.getDeclaredMethod("get", String.class, String.class);
            m.setAccessible(true);

            String propSystemVerified = (String) m.invoke(null, "partition.system.verified", "0");
            String propState = (String) m.invoke(null, "ro.boot.verifiedbootstate", "");
            File fileDmVerityModule = new File("/sys/module/dm_verity");

            boolean verified = !propSystemVerified.equals("0");
            boolean detected = !propState.isEmpty() || fileDmVerityModule.exists();

            TextView tv = v.findViewById(R.id.dmverity);
            if (verified) {
                tv.setText(R.string.verified_boot_active);
                tv.setTextColor(getResources().getColor(R.color.warning));
            } else if (detected) {
                tv.setText(R.string.verified_boot_deactivated);
                v.findViewById(R.id.dmverity_explanation).setVisibility(View.GONE);
            } else {
                v.findViewById(R.id.dmverity_row).setVisibility(View.GONE);
            }
        } catch (Exception e) {
            Log.e(XposedApp.TAG, "Could not detect Verified Boot state", e);
        }
    }
}
