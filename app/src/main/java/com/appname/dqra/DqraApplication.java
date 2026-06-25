package com.appname.dqra;

import android.app.Application;

import com.appname.dqra.cache.AppCache;

public class DqraApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        AppCache.initialize(this);
    }
}
