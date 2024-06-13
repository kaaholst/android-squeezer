package uk.org.ngo.squeezer.screensaver;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.view.WindowManager;


public class BlackScreensaver extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = 0;
        params.flags &= ~ WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        getWindow().setAttributes(params);

    }

    @Override
    public void onUserInteraction() {
        super.onUserInteraction();
        WindowManager.LayoutParams params = getWindow().getAttributes();
        params.screenBrightness = -1;
        params.flags |= WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON;
        finish();
    }

}
