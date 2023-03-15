package uk.org.ngo.squeezer;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ListView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.CheckBoxPreference;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;

import uk.org.ngo.squeezer.dialog.CallStateDialog;
import uk.org.ngo.squeezer.download.DownloadFilenameStructure;
import uk.org.ngo.squeezer.download.DownloadPathStructure;
import uk.org.ngo.squeezer.framework.EnumWithText;
import uk.org.ngo.squeezer.service.ISqueezeService;
import uk.org.ngo.squeezer.service.SqueezeService;
import uk.org.ngo.squeezer.util.Scrobble;
import uk.org.ngo.squeezer.util.ThemeManager;
import uk.org.ngo.squeezer.widget.CallStatePermissionLauncher;

public class SettingsFragment  extends PreferenceFragmentCompat implements
        Preference.OnPreferenceChangeListener, SharedPreferences.OnSharedPreferenceChangeListener,
        CallStateDialog.CallStateDialogHost {

    private final String TAG = "SettingsFragment";

    private ISqueezeService service = null;

    private IntEditTextPreference fadeInPref;

    private final ServiceConnection serviceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            SettingsFragment.this.service = (ISqueezeService) service;
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            service = null;
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        getActivity().bindService(new Intent(getActivity(), SqueezeService.class), serviceConnection,
                Context.BIND_AUTO_CREATE);
        Log.d(TAG, "did bindService; service = " + service);

        getPreferenceManager().setSharedPreferencesName(Preferences.NAME);
        setPreferencesFromResource(R.xml.preferences, rootKey);

        SharedPreferences sharedPreferences = getPreferenceManager().getSharedPreferences();
        sharedPreferences.registerOnSharedPreferenceChangeListener(this);
        Preferences preferences = new Preferences(getActivity(), sharedPreferences);

        fadeInPref = findPreference(Preferences.KEY_FADE_IN_SECS);
        fadeInPref.setOnPreferenceChangeListener(this);
        updateFadeInSecondsSummary(preferences.getFadeInSecs());

        fillIncomingCallPreferences(preferences);

        fillDisplayPreferences(preferences);

        fillUserInterfacePreferences(preferences);

        fillScrobblePreferences(sharedPreferences);

        fillDownloadPreferences(preferences);

        SwitchPreferenceCompat startSqueezePlayerPref = findPreference(
                Preferences.KEY_SQUEEZEPLAYER_ENABLED);
        startSqueezePlayerPref.setChecked(sharedPreferences.getBoolean(Preferences.KEY_SQUEEZEPLAYER_ENABLED, true));
    }

    private void fillScrobblePreferences(SharedPreferences preferences) {
        SwitchPreferenceCompat scrobblePref = findPreference(Preferences.KEY_SCROBBLE_ENABLED);
        scrobblePref.setOnPreferenceChangeListener(this);

        if (!Scrobble.canScrobble()) {
            scrobblePref.setSummaryOff(getString(R.string.settings_scrobble_noapp));
            scrobblePref.setChecked(false);
        } else {
            scrobblePref.setSummaryOff(getString(R.string.settings_scrobble_off));

            scrobblePref
                    .setChecked(preferences.getBoolean(Preferences.KEY_SCROBBLE_ENABLED, false));

            // If an old KEY_SCROBBLE preference exists, use it, delete it, and
            // upgrade it to the new KEY_SCROBBLE_ENABLED preference.
            if (preferences.contains(Preferences.KEY_SCROBBLE)) {
                boolean enabled = (Integer.parseInt(
                        preferences.getString(Preferences.KEY_SCROBBLE, "0")) > 0);
                scrobblePref.setChecked(enabled);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putBoolean(Preferences.KEY_SCROBBLE_ENABLED, enabled);
                editor.remove(Preferences.KEY_SCROBBLE);
                editor.apply();
            }
        }
    }

    private void fillDownloadPreferences(Preferences preferences) {
        final ListPreference pathStructurePreference = findPreference(Preferences.KEY_DOWNLOAD_PATH_STRUCTURE);
        final ListPreference filenameStructurePreference = findPreference(Preferences.KEY_DOWNLOAD_FILENAME_STRUCTURE);

        fillEnumPreference(pathStructurePreference, DownloadPathStructure.class, preferences.getDownloadPathStructure());
        fillEnumPreference(filenameStructurePreference, DownloadFilenameStructure.class, preferences.getDownloadFilenameStructure());

        updateDownloadPreferences(preferences);
    }

    private void updateDownloadPreferences(Preferences preferences) {
        final SwitchPreferenceCompat downloadEnabled = findPreference(Preferences.KEY_DOWNLOAD_ENABLED);
        final CheckBoxPreference downloadConfirmation = findPreference(Preferences.KEY_DOWNLOAD_CONFIRMATION);
        final CheckBoxPreference useServerPathPreference = findPreference(Preferences.KEY_DOWNLOAD_USE_SERVER_PATH);
        final ListPreference pathStructurePreference = findPreference(Preferences.KEY_DOWNLOAD_PATH_STRUCTURE);
        final ListPreference filenameStructurePreference = findPreference(Preferences.KEY_DOWNLOAD_FILENAME_STRUCTURE);
        final boolean enabled = preferences.isDownloadEnabled();
        final boolean useServerPath = preferences.isDownloadUseServerPath();

        downloadEnabled.setChecked(enabled);
        downloadConfirmation.setChecked(preferences.isDownloadConfirmation());
        useServerPathPreference.setChecked(useServerPath);

        downloadConfirmation.setEnabled(enabled);
        useServerPathPreference.setEnabled(enabled);
        pathStructurePreference.setEnabled(enabled && !useServerPath);
        filenameStructurePreference.setEnabled(enabled && !useServerPath);
    }

    private void fillIncomingCallPreferences(Preferences preferences) {
        ListPreference incomingCallPref = findPreference(Preferences.KEY_ACTION_ON_INCOMING_CALL);
        fillEnumPreference(incomingCallPref, Preferences.IncomingCallAction.class, preferences.getActionOnIncomingCall());
        updateIncomingCallPreferences(preferences);
    }

    private void updateIncomingCallPreferences(Preferences preferences) {
        final CheckBoxPreference restoreMusicPreference = findPreference(Preferences.KEY_RESTORE_MUSIC_AFTER_CALL);
        restoreMusicPreference.setEnabled(preferences.getActionOnIncomingCall() != Preferences.IncomingCallAction.NONE);
    }

    private void fillDisplayPreferences(Preferences preferences) {
        ListPreference onSelectThemePref = findPreference(Preferences.KEY_ON_THEME_SELECT_ACTION);
        ArrayList<String> entryValues = new ArrayList<>();
        ArrayList<String> entries = new ArrayList<>();

        for (ThemeManager.Theme theme : ThemeManager.Theme.values()) {
            entryValues.add(theme.name());
            entries.add(theme.getText(getActivity()));
        }

        onSelectThemePref.setEntryValues(entryValues.toArray(new String[entryValues.size()]));
        onSelectThemePref.setEntries(entries.toArray(new String[0]));
        onSelectThemePref.setDefaultValue(ThemeManager.getDefaultTheme().name());
        if (onSelectThemePref.getValue() == null) {
            onSelectThemePref.setValue(ThemeManager.getDefaultTheme().name());
        } else {
            try {
                ThemeManager.Theme t = ThemeManager.Theme.valueOf(onSelectThemePref.getValue());
            } catch (Exception e) {
                onSelectThemePref.setValue(ThemeManager.getDefaultTheme().name());
            }
        }
        onSelectThemePref.setOnPreferenceChangeListener(this);

        ListPreference screensaverPref = findPreference(Preferences.KEY_SCREENSAVER);
        fillEnumPreference(screensaverPref, Preferences.ScreensaverMode.class, preferences.getScreensaverMode());
    }

    private void fillUserInterfacePreferences(Preferences preferences) {
        final SwitchPreferenceCompat clearPlaylistConfirmation = findPreference(Preferences.KEY_CLEAR_PLAYLIST_CONFIRMATION);
        clearPlaylistConfirmation.setChecked(preferences.isClearPlaylistConfirmation());

        ListPreference customizeHomePref = findPreference(Preferences.KEY_CUSTOMIZE_HOME_MENU_MODE);
        fillEnumPreference(customizeHomePref, Preferences.CustomizeHomeMenuMode.class, preferences.getCustomizeHomeMenuMode());

        ListPreference customizeShortcutsPref = findPreference(Preferences.KEY_CUSTOMIZE_SHORTCUT_MODE);
        fillEnumPreference(customizeShortcutsPref, Preferences.CustomizeShortcutsMode.class, preferences.getCustomizeShortcutsMode());
    }

    private <E extends Enum<E> & EnumWithText> void fillEnumPreference(ListPreference listPreference, Class<E> actionTypes, E defaultValue) {
        fillEnumPreference(listPreference, actionTypes.getEnumConstants(), defaultValue);
    }

    private <E extends Enum<E> & EnumWithText> void fillEnumPreference(ListPreference listPreference, E[] actionTypes, E defaultValue) {
        String[] values = new String[actionTypes.length];
        String[] entries = new String[actionTypes.length];
        for (int i = 0; i < actionTypes.length; i++) {
            values[i] = actionTypes[i].name();
            entries[i] = actionTypes[i].getText(getActivity());
        }
        listPreference.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        listPreference.setEntryValues(values);
        listPreference.setEntries(entries);
        listPreference.setDefaultValue(defaultValue);
        if (listPreference.getValue() == null) {
            listPreference.setValue(defaultValue.name());
        }
        listPreference.setOnPreferenceChangeListener(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        getActivity().unbindService(serviceConnection);
    }

    private void updateFadeInSecondsSummary(int fadeInSeconds) {
        if (fadeInSeconds == 0) {
            fadeInPref.setSummary(R.string.disabled);
        } else {
            fadeInPref.setSummary(fadeInSeconds + " " + getResources()
                    .getQuantityString(R.plurals.seconds, fadeInSeconds));
        }
    }

    /**
     * A preference has been changed by the user, but has not yet been persisted.
     */
    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        final String key = preference.getKey();
        Log.v(TAG, "preference change for: " + key);

        if (Preferences.KEY_FADE_IN_SECS.equals(key)) {
            updateFadeInSecondsSummary(Util.getInt(newValue.toString()));
        }

        // If the user has enabled Scrobbling but we don't think it will work
        // pop up a dialog with links to Google Play for apps to install.
        if (Preferences.KEY_SCROBBLE_ENABLED.equals(key)) {
            if (newValue.equals(true) && !Scrobble.canScrobble()) {
                new ScrobbleAppsDialog().show(getFragmentManager(), TAG);
                return false;
            }
        }

        // If the user has enabled action on call first check for permission
        if (Preferences.KEY_ACTION_ON_INCOMING_CALL.equals(key)) {
            requestCallStateLauncher.trySetAction(Preferences.IncomingCallAction.valueOf((String) newValue));
            return false;
        }

        return true;
    }

    private final CallStatePermissionLauncher requestCallStateLauncher = new CallStatePermissionLauncher(this);

    @Override
    public void requestCallStatePermission() {
        requestCallStateLauncher.requestCallStatePermission();
    }

    /**
     * A preference has been changed by the user and is going to be persisted.
     */
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        Log.v(TAG, "Preference changed: " + key);

        // The fragment may no longer be attached to its activity. If so, do nothing.
        if (!isAdded()) {
            return;
        }

        Preferences preferences = new Preferences(getActivity(), sharedPreferences);

        if (key.equals(Preferences.KEY_DOWNLOAD_USE_SERVER_PATH) ||
                key.equals(Preferences.KEY_DOWNLOAD_ENABLED)
        ) {
            updateDownloadPreferences(preferences);
        }

        if (Preferences.KEY_ACTION_ON_INCOMING_CALL.equals(key)) {
            ListPreference incomingCallPref = findPreference(Preferences.KEY_ACTION_ON_INCOMING_CALL);
            incomingCallPref.setValue(sharedPreferences.getString(Preferences.KEY_ACTION_ON_INCOMING_CALL, null));
            updateIncomingCallPreferences(preferences);
        }

        if (service != null) {
            service.preferenceChanged(preferences, key);
        } else {
            Log.v(TAG, "service is null!");
        }
    }

    public static class ScrobbleAppsDialog extends DialogFragment {
        @NonNull
        @Override
        public AlertDialog onCreateDialog(Bundle savedInstanceState) {
            final CharSequence[] apps = {
                    "Last.fm", "ScrobbleDroid", "SLS"
            };
            final CharSequence[] urls = {
                    "fm.last.android", "net.jjc1138.android.scrobbler",
                    "com.adam.aslfms"
            };

            final int[] icons = {
                    R.drawable.ic_launcher_lastfm,
                    R.drawable.ic_launcher_scrobbledroid, R.drawable.ic_launcher_sls
            };

            final View dialogView = LayoutInflater.from(getActivity()).inflate(R.layout.scrobbler_choice_dialog, null);
            AlertDialog dialog = new MaterialAlertDialogBuilder(getActivity())
                    .setView(dialogView)
                    .setTitle("Scrobbling applications")
                    .create();

            ListView appList = dialogView.findViewById(R.id.scrobble_apps);
            appList.setAdapter(new IconRowAdapter(getActivity(), apps, icons));

            final Context context = dialog.getContext();
            appList.setOnItemClickListener((parent, view, position, id1) -> {
                Intent intent = new Intent(Intent.ACTION_VIEW);
                intent.setData(Uri.parse("market://details?id=" + urls[position]));
                try {
                    startActivity(intent);
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(context, R.string.settings_market_not_found,
                            Toast.LENGTH_SHORT).show();
                }
            });

            return dialog;
        }

    }
}
