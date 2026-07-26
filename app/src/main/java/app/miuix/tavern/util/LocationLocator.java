package app.miuix.tavern.util;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import java.util.List;

public final class LocationLocator {
    public interface Callback {
        void onLocation(Location location);

        void onError(String message);
    }

    private LocationLocator() {
    }

    @SuppressWarnings("MissingPermission")
    public static void locate(Context context, Callback callback) {
        LocationManager manager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        if (manager == null) {
            callback.onError("设备不支持定位服务");
            return;
        }
        try {
            Location best = null;
            List<String> providers = manager.getProviders(true);
            for (String provider : providers) {
                Location candidate = manager.getLastKnownLocation(provider);
                if (candidate != null && (best == null
                        || candidate.getTime() > best.getTime())) {
                    best = candidate;
                }
            }
            if (best != null && System.currentTimeMillis() - best.getTime() < 15 * 60_000L) {
                callback.onLocation(best);
                return;
            }
            String provider = chooseProvider(manager);
            if (provider == null) {
                callback.onError("请先开启系统定位服务");
                return;
            }
            Handler handler = new Handler(Looper.getMainLooper());
            final boolean[] completed = {false};
            LocationListener listener = new LocationListener() {
                @Override
                public void onLocationChanged(Location location) {
                    if (completed[0]) return;
                    completed[0] = true;
                    manager.removeUpdates(this);
                    callback.onLocation(location);
                }

                @Override
                public void onStatusChanged(String provider, int status, Bundle extras) {
                }

                @Override
                public void onProviderEnabled(String provider) {
                }

                @Override
                public void onProviderDisabled(String provider) {
                }
            };
            Location fallback = best;
            Runnable timeout = () -> {
                if (completed[0]) return;
                completed[0] = true;
                manager.removeUpdates(listener);
                if (fallback != null) callback.onLocation(fallback);
                else callback.onError("暂时无法获取当前位置，请到开阔处重试");
            };
            handler.postDelayed(timeout, 12_000L);
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper());
        } catch (SecurityException error) {
            callback.onError("没有位置权限");
        } catch (RuntimeException error) {
            callback.onError("定位失败：" + error.getMessage());
        }
    }

    private static String chooseProvider(LocationManager manager) {
        try {
            if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                return LocationManager.NETWORK_PROVIDER;
            }
            if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                return LocationManager.GPS_PROVIDER;
            }
        } catch (RuntimeException ignored) {
        }
        return null;
    }
}
