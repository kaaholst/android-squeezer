/*
 * Copyright (c) 2013 Google Inc.  All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.org.ngo.squeezer;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;

import androidx.annotation.IntDef;

import com.google.android.material.textfield.TextInputLayout;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

import uk.org.ngo.squeezer.dialog.ServerAddressView;
import uk.org.ngo.squeezer.framework.BaseActivity;
import uk.org.ngo.squeezer.itemlist.HomeActivity;
import uk.org.ngo.squeezer.service.event.HandshakeComplete;

/**
 * An activity for when the user is not connected to a Squeezeserver.
 * <p>
 * Provide a UI for connecting to the configured server, launch HomeMenuActivity when the user
 * connects.
 */
public class ConnectActivity extends BaseActivity {
    @IntDef({MANUAL_DISCONNECT, CONNECTION_FAILED, LOGIN_FAILED, INVALID_URL})
    @Retention(RetentionPolicy.SOURCE)
    private  @interface DisconnectionReasons {}
    private static final int MANUAL_DISCONNECT = 0;
    private static final int CONNECTION_FAILED = 1;
    private static final int LOGIN_FAILED = 2;
    private static final int INVALID_URL = 3;

    private static final String EXTRA_DISCONNECTION_REASON = "reason";

    private ServerAddressView serverAddressView;

    @DisconnectionReasons private int mDisconnectionReason = MANUAL_DISCONNECT;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Bundle extras = getIntent().getExtras();
        if (extras != null) {
            //noinspection ResourceType
            mDisconnectionReason = extras.getInt(EXTRA_DISCONNECTION_REASON);
        }

        setContentView(R.layout.disconnected);
        serverAddressView = findViewById(R.id.server_address_view);
        setErrorMessageFromReason(mDisconnectionReason);
    }

    /**
     * Show this activity.
     * <p>
     * Flags are set to clear the previous activities, as trying to go back while disconnected makes
     * no sense.
     * <p>
     * The pending transition is overridden to animate the activity in place, rather than having it
     * appear to move in from off-screen.
     *
     * @param disconnectionReason identifies why the activity is being shown.
     */
    private static void show(Activity activity, @DisconnectionReasons int disconnectionReason) {
        // If the activity is already running then make sure the header message is appropriate
        // and stop, as there's no need to start another instance of the activity.
        if (activity instanceof ConnectActivity) {
            ((ConnectActivity) activity).setErrorMessageFromReason(disconnectionReason);
            return;
        }

        final Intent intent = new Intent(activity, ConnectActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        intent.putExtra(EXTRA_DISCONNECTION_REASON, disconnectionReason);
        activity.startActivity(intent);
        activity.overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }

    /**
     * Set the text and visibility of the optional header message that's shown to the user
     * based on the reason for the activity being shown.
     *
     * @param disconnectionReason The reason.
     */
    @SuppressLint("SwitchIntDef")
    private void setErrorMessageFromReason(@DisconnectionReasons int disconnectionReason) {
        TextInputLayout serverAddress = serverAddressView.findViewById(R.id.server_address_til);
        TextInputLayout userName = serverAddressView.findViewById(R.id.username_til);
        serverAddress.setError(null);
        userName.setError(null);


        switch (disconnectionReason) {
            case CONNECTION_FAILED:
                serverAddress.setError(getString(R.string.connection_failed_text));
                break;
            case LOGIN_FAILED:
                userName.setError(getString(R.string.login_failed_text));
                break;
            case INVALID_URL:
                serverAddress.setError(getString(R.string.invalid_url_text));
                break;
        }
    }

    /**
     * Show this activity.
     * @see #show(android.app.Activity)
     */
    public static void show(Activity activity) {
        show(activity, MANUAL_DISCONNECT);
    }

    /**
     * Show this activity on login failure.
     * @see #show(android.app.Activity)
     */
    public static void showLoginFailed(Activity activity) {
        show(activity, LOGIN_FAILED);
    }

    public static void showConnectionFailed(Activity activity) {
        show(activity, CONNECTION_FAILED);
    }

    public static void showInvalidUrl(Activity activity) {
        show(activity, INVALID_URL);
    }

    /**
     * Act on the user requesting a server connection through the activity's UI.
     *
     * @param view The view the user pressed.
     */
    public void onUserInitiatesConnect(View view) {
        if (serverAddressView.savePreferences()) {
            NowPlayingFragment fragment = (NowPlayingFragment) getSupportFragmentManager()
                    .findFragmentById(R.id.now_playing_fragment);
            fragment.startVisibleConnection(false);
        }
    }

    public void onEventMainThread(HandshakeComplete event) {
        final Intent intent = new Intent(this, HomeActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
    }
}
