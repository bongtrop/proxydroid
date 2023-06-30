/* proxydroid - Global / Individual Proxy App for Android
 * Copyright (C) 2011 Max Lv <max.c.lv@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 *
 *                            ___====-_  _-====___
 *                      _--^^^#####//      \\#####^^^--_
 *                   _-^##########// (    ) \\##########^-_
 *                  -############//  |\^^/|  \\############-
 *                _/############//   (@::@)   \\############\_
 *               /#############((     \\//     ))#############\
 *              -###############\\    (oo)    //###############-
 *             -#################\\  / VV \  //#################-
 *            -###################\\/      \//###################-
 *           _#/|##########/\######(   /\   )######/\##########|\#_
 *           |/ |#/\#/\#/\/  \#/\##\  |  |  /##/\#/  \/\#/\#/\#| \|
 *           `  |/  V  V  `   V  \#\| |  | |/#/  V   '  V  V  \|  '
 *              `   `  `      `   / | |  | | \   '      '  '   '
 *                               (  | |  | |  )
 *                              __\ | |  | | /__
 *                             (vvv(VVV)(VVV)vvv)
 *
 *                              HERE BE DRAGONS
 *
 */

package org.proxydroid;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.preference.CheckBoxPreference;
import android.preference.EditTextPreference;
import android.preference.ListPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;
import android.preference.PreferenceScreen;
import android.preference.SwitchPreference;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewParent;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;

import com.ksmaze.android.preference.ListPreferenceMultiSelect;

import org.proxydroid.utils.Constraints;
import org.proxydroid.utils.Utils;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

public class ProxyDroid extends PreferenceActivity
        implements OnSharedPreferenceChangeListener {

    private static final String TAG = "ProxyDroid";
    private static final int MSG_UPDATE_FINISHED = 0;
    private static final int MSG_NO_ROOT = 1;
    final Handler handler = new Handler(msg -> {
        switch (msg.what) {
            case MSG_UPDATE_FINISHED:
                Toast.makeText(ProxyDroid.this, getString(R.string.update_finished), Toast.LENGTH_LONG)
                        .show();
                break;
            case MSG_NO_ROOT:
                showAToast(getString(R.string.require_root_alert));
                break;
        }
        return true;
    });
    private ProgressDialog pd = null;
    private String profile;
    private final Profile mProfile = new Profile();
    private CheckBoxPreference isAutoConnectCheck;
    private CheckBoxPreference isAutoSetProxyCheck;
    private CheckBoxPreference isAuthCheck;
    private CheckBoxPreference isPACCheck;
    private ListPreference profileList;
    private EditTextPreference hostText;
    private EditTextPreference portText;
    private EditTextPreference userText;
    private EditTextPreference passwordText;
    private ListPreferenceMultiSelect ssidList;
    private ListPreferenceMultiSelect excludedSsidList;
    private ListPreference proxyTypeList;
    private EditTextPreference dnsText;
    private Preference isRunningCheck;
    private CheckBoxPreference isBypassAppsCheck;
    private Preference proxyedApps;
    private Preference bypassAddrs;

    private final BroadcastReceiver ssidReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (!action.equals(ConnectivityManager.CONNECTIVITY_ACTION)) {
                Log.w(TAG, "onReceived() called uncorrectly");
                return;
            }

            loadNetworkList();
        }
    };

    private void showAbout() {

        WebView web = new WebView(this);
        web.loadUrl("file:///android_asset/pages/about.html");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
                return true;
            }
        });

        String versionName;
        try {
            versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (NameNotFoundException ex) {
            versionName = "";
        }

        new AlertDialog.Builder(this).setTitle(
                        String.format(getString(R.string.about_title), versionName))
                .setCancelable(false)
                .setNegativeButton(getString(R.string.ok_iknow), (dialog, id) -> dialog.cancel())
                .setView(web)
                .create()
                .show();
    }

    private void CopyAssets() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        String abi;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
            abi = Build.SUPPORTED_ABIS[0];
        } else {
            abi = Build.CPU_ABI;
        }
        try {
            if (abi.matches("armeabi-v7a|arm64-v8a"))
                files = assetManager.list("armeabi-v7a");
            else
                files = assetManager.list("x86");
        } catch (IOException e) {
            Log.e(TAG, e.getMessage());
        }
        if (files != null) {
            for (String file : files) {
                InputStream in;
                OutputStream out;
                try {
                    if (abi.matches("armeabi-v7a|arm64-v8a"))
                        in = assetManager.open("armeabi-v7a/" + file);
                    else
                        in = assetManager.open("x86/" + file);
                    out = new FileOutputStream(getFilesDir().getAbsolutePath() + "/" + file);
                    copyFile(in, out);
                    in.close();
                    out.flush();
                    out.close();
                } catch (Exception e) {
                    Log.e(TAG, e.getMessage());
                }
            }
        }
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private boolean isTextEmpty(String s, String msg) {
        if (s == null || s.length() <= 0) {
            showAToast(msg);
            return true;
        }
        return false;
    }

    private void loadProfileList() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String[] profileEntries = settings.getString("profileEntries", "").split("\\|");
        String[] profileValues = settings.getString("profileValues", "").split("\\|");

        profileList.setEntries(profileEntries);
        profileList.setEntryValues(profileValues);
    }

    private void loadNetworkList() {
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Please enable location permission first", Toast.LENGTH_LONG).show();
            return;
        }
        List<WifiConfiguration> wcs = wm.getConfiguredNetworks();
        String[] ssidEntries;
        String[] pureSsid;
        int n = 3;
        int wifiIndex = n;

        if (wcs == null) {
            ssidEntries = new String[n];

            ssidEntries[0] = Constraints.WIFI_AND_3G;
            ssidEntries[1] = Constraints.ONLY_WIFI;
            ssidEntries[2] = Constraints.ONLY_3G;
        } else {
            ssidEntries = new String[wcs.size() + n];

            ssidEntries[0] = Constraints.WIFI_AND_3G;
            ssidEntries[1] = Constraints.ONLY_WIFI;
            ssidEntries[2] = Constraints.ONLY_3G;

            for (WifiConfiguration wc : wcs) {
                if (wc != null && wc.SSID != null) {
                    ssidEntries[n++] = wc.SSID.replace("\"", "");
                } else {
                    ssidEntries[n++] = "unknown";
                }
            }
        }
        ssidList.setEntries(ssidEntries);
        ssidList.setEntryValues(ssidEntries);

        pureSsid = Arrays.copyOfRange(ssidEntries, wifiIndex, ssidEntries.length);
        excludedSsidList.setEntries(pureSsid);
        excludedSsidList.setEntryValues(pureSsid);
    }

    @Override
    public void onStart() {
        super.onStart();
    }

    @Override
    public void onStop() {
        super.onStop();
    }

    private LinearLayout getLayout(ViewParent parent) {
        if (parent instanceof LinearLayout) return (LinearLayout) parent;
        if (parent != null) {
            return getLayout(parent.getParent());
        } else {
            return null;
        }
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        addPreferencesFromResource(R.xml.proxydroid_preference);

        hostText = (EditTextPreference) findPreference("host");
        portText = (EditTextPreference) findPreference("port");
        userText = (EditTextPreference) findPreference("user");
        passwordText = (EditTextPreference) findPreference("password");
        bypassAddrs = findPreference("bypassAddrs");
        ssidList = (ListPreferenceMultiSelect) findPreference("ssid");
        excludedSsidList = (ListPreferenceMultiSelect) findPreference("excludedSsid");
        proxyTypeList = (ListPreference) findPreference("proxyType");
        dnsText = (EditTextPreference) findPreference("dns");
        proxyedApps = findPreference("proxyedApps");
        profileList = (ListPreference) findPreference("profile");

        isRunningCheck = (Preference) findPreference("isRunning");
        isAutoSetProxyCheck = (CheckBoxPreference) findPreference("isAutoSetProxy");
        isAuthCheck = (CheckBoxPreference) findPreference("isAuth");
        isPACCheck = (CheckBoxPreference) findPreference("isPAC");
        isAutoConnectCheck = (CheckBoxPreference) findPreference("isAutoConnect");
        isBypassAppsCheck = (CheckBoxPreference) findPreference("isBypassApps");

        final SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        String profileValuesString = settings.getString("profileValues", "");

        if (profileValuesString.equals("")) {
            Editor ed = settings.edit();
            profile = "1";
            ed.putString("profileValues", "1|0");
            ed.putString("profileEntries",
                    getString(R.string.profile_default) + "|" + getString(R.string.profile_new));
            ed.putString("profile", "1");
            ed.apply();

            profileList.setDefaultValue("1");
        }

        registerReceiver(ssidReceiver,
                new IntentFilter(android.net.ConnectivityManager.CONNECTIVITY_ACTION));

        loadProfileList();

        loadNetworkList();

        new Thread() {
            @Override
            public void run() {

                try {
                    // Try not to block activity
                    Thread.sleep(2000);
                } catch (InterruptedException ignore) {
                    // Nothing
                }

                if (!Utils.isRoot()) {
                    handler.sendEmptyMessage(MSG_NO_ROOT);
                }

                String versionName;
                try {
                    versionName = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                } catch (NameNotFoundException e) {
                    versionName = "NONE";
                }

                if (!settings.getBoolean(versionName, false)) {

                    String version;
                    try {
                        version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
                    } catch (NameNotFoundException e) {
                        version = "NONE";
                    }

                    reset();

                    Editor edit = settings.edit();
                    edit.putBoolean(version, true);
                    edit.apply();

                    handler.sendEmptyMessage(MSG_UPDATE_FINISHED);
                }
            }
        }.start();
    }

    /**
     * Called when the activity is closed.
     */
    @Override
    public void onDestroy() {
        unregisterReceiver(ssidReceiver);

        super.onDestroy();
    }

    private void serviceStop() {

        if (!Utils.isWorking()) return;

        try {
            stopService(new Intent(ProxyDroid.this, ProxyDroidService.class));
        } catch (Exception ignored) {
        }
    }

    /**
     * Called when connect button is clicked.
     */
    private void serviceStart() {

        if (Utils.isWorking()) return;

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        mProfile.getProfile(settings);

        try {

            Intent it = new Intent(ProxyDroid.this, ProxyDroidService.class);
            Bundle bundle = new Bundle();
            bundle.putString("host", mProfile.getHost());
            bundle.putString("user", mProfile.getUser());
            bundle.putString("bypassAddrs", mProfile.getBypassAddrs());
            bundle.putString("password", mProfile.getPassword());
            bundle.putString("dns", mProfile.getDNS());
            bundle.putString("certificate", mProfile.getCertificate());

            bundle.putString("proxyType", mProfile.getProxyType());
            bundle.putBoolean("isAutoSetProxy", mProfile.isAutoSetProxy());
            bundle.putBoolean("isBypassApps", mProfile.isBypassApps());
            bundle.putBoolean("isAuth", mProfile.isAuth());
            bundle.putBoolean("isDNSProxy", mProfile.isDNSProxy());
            bundle.putBoolean("isPAC", mProfile.isPAC());

            bundle.putInt("port", mProfile.getPort());

            it.putExtras(bundle);
            startService(it);
        } catch (Exception ignore) {
            // Nothing
        }

    }

    private void onProfileChange(String oldProfileName) {

        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        mProfile.getProfile(settings);
        Editor ed = settings.edit();
        ed.putString(oldProfileName, mProfile.toString());
        ed.apply();

        String profileString = settings.getString(profile, "");

        if (profileString.equals("")) {
            mProfile.init();
            mProfile.setName(getProfileName(profile));
        } else {
            mProfile.decodeJson(profileString);
        }

        hostText.setText(mProfile.getHost());
        userText.setText(mProfile.getUser());
        passwordText.setText(mProfile.getPassword());
        proxyTypeList.setValue(mProfile.getProxyType());
        dnsText.setText(mProfile.getDNS());
        ssidList.setValue(mProfile.getSsid());
        excludedSsidList.setValue(mProfile.getExcludedSsid());

        isAuthCheck.setChecked(mProfile.isAuth());
        isAutoConnectCheck.setChecked(mProfile.isAutoConnect());
        isAutoSetProxyCheck.setChecked(mProfile.isAutoSetProxy());
        isBypassAppsCheck.setChecked(mProfile.isBypassApps());
        isPACCheck.setChecked(mProfile.isPAC());

        portText.setText(Integer.toString(mProfile.getPort()));

        Log.d(TAG, mProfile.toString());

        mProfile.setProfile(settings);
    }

    private void showAToast(String msg) {
        if (!isFinishing()) {
            AlertDialog.Builder builder = new AlertDialog.Builder(this);
            builder.setMessage(msg)
                    .setCancelable(false)
                    .setNegativeButton(getString(R.string.ok_iknow), (dialog, id) -> dialog.cancel());
            AlertDialog alert = builder.create();
            alert.show();
        }
    }

    private void disableAll() {
        hostText.setEnabled(false);
        portText.setEnabled(false);
        userText.setEnabled(false);
        passwordText.setEnabled(false);
        ssidList.setEnabled(false);
        excludedSsidList.setEnabled(false);
        proxyTypeList.setEnabled(false);
        dnsText.setEnabled(false);
        proxyedApps.setEnabled(false);
        profileList.setEnabled(false);
        bypassAddrs.setEnabled(false);

        isAuthCheck.setEnabled(false);
        isAutoSetProxyCheck.setEnabled(false);
        isAutoConnectCheck.setEnabled(false);
        isPACCheck.setEnabled(false);
        isBypassAppsCheck.setEnabled(false);
    }

    private void enableAll() {
        hostText.setEnabled(true);
        dnsText.setEnabled(true);

        if (!isPACCheck.isChecked()) {
            portText.setEnabled(true);
            proxyTypeList.setEnabled(true);
        }

        bypassAddrs.setEnabled(true);

        if (isAuthCheck.isChecked()) {
            userText.setEnabled(true);
            passwordText.setEnabled(true);
        }
        if (!isAutoSetProxyCheck.isChecked()) {
            proxyedApps.setEnabled(true);
            isBypassAppsCheck.setEnabled(true);
        }
        if (isAutoConnectCheck.isChecked()) {
            ssidList.setEnabled(true);
            excludedSsidList.setEnabled(true);
        }

        profileList.setEnabled(true);
        isAutoSetProxyCheck.setEnabled(true);
        isAuthCheck.setEnabled(true);
        isAutoConnectCheck.setEnabled(true);
        isPACCheck.setEnabled(true);
    }

    @Override
    public boolean onPreferenceTreeClick(PreferenceScreen preferenceScreen, Preference preference) {

        if (preference.getKey() != null && preference.getKey().equals("bypassAddrs")) {
            Intent intent = new Intent(this, BypassListActivity.class);
            startActivity(intent);
        } else if (preference.getKey() != null && preference.getKey().equals("proxyedApps")) {
            Intent intent = new Intent(this, AppManager.class);
            startActivity(intent);
        }

        return super.onPreferenceTreeClick(preferenceScreen, preference);
    }

    private String getProfileName(String profile) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        return settings.getString("profile" + profile,
                getString(R.string.profile_base) + " " + profile);
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);

        if (settings.getBoolean("isAutoSetProxy", false)) {
            proxyedApps.setEnabled(false);
            isBypassAppsCheck.setEnabled(false);
        } else {
            proxyedApps.setEnabled(true);
            isBypassAppsCheck.setEnabled(true);
        }

        if (settings.getBoolean("isAutoConnect", false)) {
            ssidList.setEnabled(true);
            excludedSsidList.setEnabled(true);
        } else {
            ssidList.setEnabled(false);
            excludedSsidList.setEnabled(false);
        }

        if (settings.getBoolean("isPAC", false)) {
            portText.setEnabled(false);
            proxyTypeList.setEnabled(false);
            hostText.setTitle(R.string.host_pac);
            hostText.setSummary(R.string.host_pac_summary);
        }

        if (!settings.getBoolean("isAuth", false)) {
            userText.setEnabled(false);
            passwordText.setEnabled(false);
        }

        Editor edit = settings.edit();

        if (Utils.isWorking()) {
            if (settings.getBoolean("isConnecting", false)) isRunningCheck.setEnabled(false);
            edit.putBoolean("isRunning", true);
        } else {
            if (settings.getBoolean("isRunning", false)) {
                new Thread() {
                    @Override
                    public void run() {
                        reset();
                    }
                }.start();
            }
            edit.putBoolean("isRunning", false);
        }

        edit.apply();

        if (settings.getBoolean("isRunning", false)) {
            ((SwitchPreference) isRunningCheck).setChecked(true);
            disableAll();
        } else {
            ((SwitchPreference) isRunningCheck).setChecked(false);
            enableAll();
        }

        // Setup the initial values
        profile = settings.getString("profile", "1");
        profileList.setValue(profile);

        profileList.setSummary(getProfileName(profile));

        if (!settings.getString("ssid", "").equals("")) {
            ssidList.setSummary(settings.getString("ssid", ""));
        }
        if (!settings.getString("excludedSsid", "").equals("")) {
            excludedSsidList.setSummary(settings.getString("excludedSsid", ""));
        }
        if (!settings.getString("user", "").equals("")) {
            userText.setSummary(settings.getString("user", getString(R.string.user_summary)));
        }
        if (!settings.getString("bypassAddrs", "").equals("")) {
            bypassAddrs.setSummary(
                    settings.getString("bypassAddrs", getString(R.string.set_bypass_summary))
                            .replace("|", ", "));
        } else {
            bypassAddrs.setSummary(R.string.set_bypass_summary);
        }
        if (!settings.getString("port", "-1").equals("-1") && !settings.getString("port", "-1")
                .equals("")) {
            portText.setSummary(settings.getString("port", getString(R.string.port_summary)));
        }
        if (!settings.getString("host", "").equals("")) {
            hostText.setSummary(settings.getString("host", getString(
                    settings.getBoolean("isPAC", false) ? R.string.host_pac_summary
                            : R.string.host_summary)));
        }
        if (!settings.getString("dns", "").equals("")) {
            dnsText.setSummary(settings.getString("dns", getString(R.string.dns_summary)));
        }
        if (!settings.getString("password", "").equals("")) passwordText.setSummary("*********");
        if (!settings.getString("proxyType", "").equals("")) {
            proxyTypeList.setSummary(settings.getString("proxyType", "").toUpperCase());
        }

        // Set up a listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();

        // Unregister the listener whenever a key changes
        getPreferenceScreen().getSharedPreferences().unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences settings, String key) {
        // Let's do something a preference value changes

        if (key.equals("profile")) {
            String profileString = settings.getString("profile", "");
            if (profileString.equals("0")) {
                String[] profileEntries = settings.getString("profileEntries", "").split("\\|");
                String[] profileValues = settings.getString("profileValues", "").split("\\|");
                int newProfileValue = Integer.parseInt(profileValues[profileValues.length - 2]) + 1;

                StringBuilder profileEntriesBuffer = new StringBuilder();
                StringBuilder profileValuesBuffer = new StringBuilder();

                for (int i = 0; i < profileValues.length - 1; i++) {
                    profileEntriesBuffer.append(profileEntries[i]).append("|");
                    profileValuesBuffer.append(profileValues[i]).append("|");
                }
                profileEntriesBuffer.append(getProfileName(Integer.toString(newProfileValue))).append("|");
                profileValuesBuffer.append(newProfileValue).append("|");
                profileEntriesBuffer.append(getString(R.string.profile_new));
                profileValuesBuffer.append("0");

                Editor ed = settings.edit();
                ed.putString("profileEntries", profileEntriesBuffer.toString());
                ed.putString("profileValues", profileValuesBuffer.toString());
                ed.putString("profile", Integer.toString(newProfileValue));
                ed.apply();

                loadProfileList();
            } else {
                String oldProfile = profile;
                profile = profileString;
                profileList.setValue(profile);
                onProfileChange(oldProfile);
                profileList.setSummary(getProfileName(profileString));
            }
        }

        if (key.equals("isConnecting")) {
            if (settings.getBoolean("isConnecting", false)) {
                Log.d(TAG, "Connecting start");
                isRunningCheck.setEnabled(false);
                pd = ProgressDialog.show(this, "", getString(R.string.connecting), true, true);
            } else {
                Log.d(TAG, "Connecting finish");
                if (pd != null) {
                    pd.dismiss();
                    pd = null;
                }
                isRunningCheck.setEnabled(true);
            }
        }

        if (key.equals("isPAC")) {
            if (settings.getBoolean("isPAC", false)) {
                portText.setEnabled(false);
                proxyTypeList.setEnabled(false);
                hostText.setTitle(R.string.host_pac);
            } else {
                portText.setEnabled(true);
                proxyTypeList.setEnabled(true);
                hostText.setTitle(R.string.host);
            }
            if (settings.getString("host", "").equals("")) {
                hostText.setSummary(settings.getBoolean("isPAC", false) ? R.string.host_pac_summary
                        : R.string.host_summary);
            } else {
                hostText.setSummary(settings.getString("host", ""));
            }
        }

        if (key.equals("isAuth")) {
            if (!settings.getBoolean("isAuth", false)) {
                userText.setEnabled(false);
                passwordText.setEnabled(false);
            } else {
                userText.setEnabled(true);
                passwordText.setEnabled(true);
            }
        }

        if (key.equals("isAutoConnect")) {
            if (settings.getBoolean("isAutoConnect", false)) {
                loadNetworkList();
                ssidList.setEnabled(true);
                excludedSsidList.setEnabled(true);
            } else {
                ssidList.setEnabled(false);
                excludedSsidList.setEnabled(false);
            }
        }

        if (key.equals("isAutoSetProxy")) {
            if (settings.getBoolean("isAutoSetProxy", false)) {
                proxyedApps.setEnabled(false);
                isBypassAppsCheck.setEnabled(false);
            } else {
                proxyedApps.setEnabled(true);
                isBypassAppsCheck.setEnabled(true);
            }
        }

        if (key.equals("isRunning")) {
            if (settings.getBoolean("isRunning", false)) {
                disableAll();
                ((SwitchPreference) isRunningCheck).setChecked(true);
                if (!Utils.isConnecting()) serviceStart();
            } else {
                enableAll();
                ((SwitchPreference) isRunningCheck).setChecked(false);
                if (!Utils.isConnecting()) serviceStop();
            }
        }

        switch (key) {
            case "ssid":
                if (settings.getString("ssid", "").equals("")) {
                    ssidList.setSummary(getString(R.string.ssid_summary));
                } else {
                    ssidList.setSummary(settings.getString("ssid", ""));
                }
                break;
            case "excludedSsid":
                if (settings.getString("excludedSsid", "").equals("")) {
                    excludedSsidList.setSummary(getString(R.string.excluded_ssid_summary));
                } else {
                    excludedSsidList.setSummary(settings.getString("excludedSsid", ""));
                }
                break;
            case "user":
                if (settings.getString("user", "").equals("")) {
                    userText.setSummary(getString(R.string.user_summary));
                } else {
                    userText.setSummary(settings.getString("user", ""));
                }
                break;
            case "proxyType":
                if (settings.getString("proxyType", "").equals("")) {
                    proxyTypeList.setSummary(getString(R.string.proxy_type_summary));
                } else {
                    proxyTypeList.setSummary(settings.getString("proxyType", "").toUpperCase());
                }
                break;
            case "bypassAddrs":
                if (settings.getString("bypassAddrs", "").equals("")) {
                    bypassAddrs.setSummary(getString(R.string.set_bypass_summary));
                } else {
                    bypassAddrs.setSummary(settings.getString("bypassAddrs", "").replace("|", ", "));
                }
                break;
            case "port":
                if (settings.getString("port", "-1").equals("-1") || settings.getString("port", "-1")
                        .equals("")) {
                    portText.setSummary(getString(R.string.port_summary));
                } else {
                    portText.setSummary(settings.getString("port", ""));
                }
                break;
            case "host":
                if (settings.getString("host", "").equals("")) {
                    hostText.setSummary(settings.getBoolean("isPAC", false) ? R.string.host_pac_summary
                            : R.string.host_summary);
                } else {
                    hostText.setSummary(settings.getString("host", ""));
                }
                break;
            case "dns":
                if (settings.getString("dns", "").equals("")) {
                    dnsText.setSummary(R.string.host_summary);
                } else {
                    dnsText.setSummary(settings.getString("dns", ""));
                }
                break;
            case "password":
                if (!settings.getString("password", "").equals("")) {
                    passwordText.setSummary("*********");
                } else {
                    passwordText.setSummary(getString(R.string.password_summary));
                }
                break;
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        menu.add(Menu.NONE, Menu.FIRST + 1, 4, getString(R.string.recovery))
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER);
        menu.add(Menu.NONE, Menu.FIRST + 2, 2, getString(R.string.profile_del))
                .setIcon(android.R.drawable.ic_menu_delete)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(Menu.NONE, Menu.FIRST + 3, 5, getString(R.string.about))
                .setIcon(android.R.drawable.ic_menu_info_details)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);
        menu.add(Menu.NONE, Menu.FIRST + 4, 1, getString(R.string.change_name))
                .setIcon(android.R.drawable.ic_menu_edit)
                .setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM | MenuItem.SHOW_AS_ACTION_WITH_TEXT);

        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case Menu.FIRST + 1:
                new Thread() {
                    @Override
                    public void run() {
                        reset();
                    }
                }.start();
                return true;
            case Menu.FIRST + 2:
                AlertDialog ad = new AlertDialog.Builder(this).setTitle(R.string.profile_del)
                        .setMessage(R.string.profile_del_confirm)
                        .setPositiveButton(R.string.alert_dialog_ok, (dialog, whichButton) -> {
                            /* User clicked OK so do some stuff */
                            delProfile(profile);
                        })
                        .setNegativeButton(R.string.alert_dialog_cancel, (dialog, whichButton) -> {
                            /* User clicked Cancel so do some stuff */
                            dialog.dismiss();
                        })
                        .create();

                ad.show();

                return true;
            case Menu.FIRST + 3:
                showAbout();
                return true;
            case Menu.FIRST + 4:
                rename();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    private void rename() {
        LayoutInflater factory = LayoutInflater.from(this);
        final View textEntryView = factory.inflate(R.layout.alert_dialog_text_entry, null);
        final EditText profileName = (EditText) textEntryView.findViewById(R.id.text_edit);
        profileName.setText(getProfileName(profile));

        AlertDialog ad = new AlertDialog.Builder(this).setTitle(R.string.change_name)
                .setView(textEntryView)
                .setPositiveButton(R.string.alert_dialog_ok, (dialog, whichButton) -> {
                    SharedPreferences settings =
                            PreferenceManager.getDefaultSharedPreferences(ProxyDroid.this);
                    String name = profileName.getText().toString();
                    name = name.replace("|", "");
                    if (name.length() <= 0) return;
                    Editor ed = settings.edit();
                    ed.putString("profile" + profile, name);
                    ed.apply();

                    profileList.setSummary(getProfileName(profile));

                    String[] profileEntries = settings.getString("profileEntries", "").split("\\|");
                    String[] profileValues = settings.getString("profileValues", "").split("\\|");

                    StringBuilder profileEntriesBuffer = new StringBuilder();
                    StringBuilder profileValuesBuffer = new StringBuilder();

                    for (int i = 0; i < profileValues.length - 1; i++) {
                        if (profileValues[i].equals(profile)) {
                            profileEntriesBuffer.append(getProfileName(profile)).append("|");
                        } else {
                            profileEntriesBuffer.append(profileEntries[i]).append("|");
                        }
                        profileValuesBuffer.append(profileValues[i]).append("|");
                    }

                    profileEntriesBuffer.append(getString(R.string.profile_new));
                    profileValuesBuffer.append("0");

                    ed = settings.edit();
                    ed.putString("profileEntries", profileEntriesBuffer.toString());
                    ed.putString("profileValues", profileValuesBuffer.toString());

                    ed.apply();

                    loadProfileList();
                })
                .setNegativeButton(R.string.alert_dialog_cancel, (dialog, whichButton) -> {
                    /* User clicked cancel so do some stuff */
                })
                .create();
        ad.show();
    }

    private void delProfile(String profile) {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(this);
        String[] profileEntries = settings.getString("profileEntries", "").split("\\|");
        String[] profileValues = settings.getString("profileValues", "").split("\\|");

        Log.d(TAG, "Profile :" + profile);
        if (profileEntries.length > 2) {
            StringBuilder profileEntriesBuffer = new StringBuilder();
            StringBuilder profileValuesBuffer = new StringBuilder();

            String newProfileValue = "1";

            for (int i = 0; i < profileValues.length - 1; i++) {
                if (!profile.equals(profileValues[i])) {
                    profileEntriesBuffer.append(profileEntries[i]).append("|");
                    profileValuesBuffer.append(profileValues[i]).append("|");
                    newProfileValue = profileValues[i];
                }
            }
            profileEntriesBuffer.append(getString(R.string.profile_new));
            profileValuesBuffer.append("0");

            Editor ed = settings.edit();
            ed.putString("profileEntries", profileEntriesBuffer.toString());
            ed.putString("profileValues", profileValuesBuffer.toString());
            ed.putString("profile", newProfileValue);
            ed.apply();

            loadProfileList();
        }
    }

    private void reset() {
        try {
            stopService(new Intent(ProxyDroid.this, ProxyDroidService.class));
        } catch (Exception e) {
            // Nothing
        }

        CopyAssets();

        String filePath = getFilesDir().getAbsolutePath();

        Utils.runRootCommand(Utils.getIptables()
                + " -t nat -F OUTPUT\n"
                + getFilesDir().getAbsolutePath());

        Utils.runRootCommand(
                "chmod 700 " + filePath + "/gost.sh\n"
                        + "chmod 700 " + filePath + "/gost_dns.sh\n"
                        + "chmod 700 " + filePath + "/gost\n");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && event.getRepeatCount() == 0) {
            try {
                finish();
            } catch (Exception ignore) {
                // Nothing
            }
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
