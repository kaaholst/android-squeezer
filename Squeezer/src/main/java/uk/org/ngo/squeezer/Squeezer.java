package uk.org.ngo.squeezer;


import android.app.Application;
import android.content.Context;

// Trick to make the app context useful available everywhere.
// See http://stackoverflow.com/questions/987072/using-application-context-everywhere

public class Squeezer extends Application {

    private static Squeezer instance;

    public Squeezer() {
        instance = this;
    }

    public static Context getContext() {
        return instance;
    }

    @Override
    public void onCreate() {
        super.onCreate();
    }
}

