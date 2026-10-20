package io.github.miclean.module;

import android.app.Application;
import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class ModuleApp extends Application {
    @Override public void onCreate() {
        super.onCreate();
        Settings.prepareLocal(this);
        XposedServiceHelper.registerListener(new XposedServiceHelper.OnServiceListener() {
            @Override public void onServiceBind(XposedService service) {
                new Thread(() -> Settings.bind(ModuleApp.this, service), "NoAdMi-settings").start();
            }
            @Override public void onServiceDied(XposedService service) { Settings.disconnect(service); }
        });
    }
}
