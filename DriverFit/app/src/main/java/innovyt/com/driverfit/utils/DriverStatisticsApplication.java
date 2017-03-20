package innovyt.com.driverfit.utils;

import android.app.Application;

import innovyt.com.driverfit.services.Obd2Service;
public class DriverStatisticsApplication extends Application {
    Obd2Service mService;
    boolean mBound = false;
    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public void onTerminate() {
        super.onTerminate();
    }

}
