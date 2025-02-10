package uk.org.ngo.squeezer.widget;

import android.view.View;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ViewUtilities {

    public static void setInsetsListener(View view, boolean top, boolean bottom, boolean ime) {
        ViewCompat.setOnApplyWindowInsetsListener(view, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout() | (ime ? WindowInsetsCompat.Type.ime() : 0 ));
            v.setPadding(bars.left, top ? bars.top : 0, bars.right, bottom ? bars.bottom : 0);
            return insets;
        });
    }

}
