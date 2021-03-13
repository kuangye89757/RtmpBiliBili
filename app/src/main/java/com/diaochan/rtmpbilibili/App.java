package com.diaochan.rtmpbilibili;

import android.app.Application;

/**
 * Create by shijie.wang on 2021/3/11.
 */
public class App extends Application {
    private static App mAPP;

    public static App getAPP() {
        return mAPP;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mAPP = this;
    }
} 