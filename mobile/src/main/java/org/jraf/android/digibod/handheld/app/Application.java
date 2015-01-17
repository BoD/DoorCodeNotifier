package org.jraf.android.digibod.handheld.app;

import android.os.Handler;
import android.os.StrictMode;

import com.crashlytics.android.Crashlytics;

import org.jraf.android.digibod.BuildConfig;
import org.jraf.android.util.Constants;
import org.jraf.android.util.log.wrapper.Log;

import io.fabric.sdk.android.Fabric;

public class Application extends android.app.Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // Log
        Log.init(Constants.TAG);

        // Strict mode
        if (BuildConfig.STRICT_MODE) setupStrictMode();

        // Crashlytics
        Fabric.with(this, new Crashlytics());

    }

    private void setupStrictMode() {
        // Do this in a Handler.post because of this issue: http://code.google.com/p/android/issues/detail?id=35298
        new Handler().post(new Runnable() {
            @Override
            public void run() {
                StrictMode.setThreadPolicy(new StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build());
                StrictMode.setVmPolicy(new StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build());
            }
        });
    }
}
