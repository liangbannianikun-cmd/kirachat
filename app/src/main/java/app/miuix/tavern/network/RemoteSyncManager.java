package app.miuix.tavern.network;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import app.miuix.tavern.data.SyncSettings;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class RemoteSyncManager {
    public interface Callback {
        void onComplete(RemoteSyncClient.Result result, Exception error);
    }

    private static final ScheduledExecutorService EXECUTOR =
            Executors.newSingleThreadScheduledExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static ScheduledFuture<?> pending;
    private static long lastForegroundAttempt;

    private RemoteSyncManager() {
    }

    public static synchronized void schedule(Context context) {
        Context app = context.getApplicationContext();
        SyncSettings settings = new SyncSettings(app);
        if (!settings.autoSync() || !settings.configured()) return;
        if (pending != null) pending.cancel(false);
        pending = EXECUTOR.schedule(
                () -> run(app, RemoteSyncClient.Mode.AUTOMATIC, null),
                2500, TimeUnit.MILLISECONDS);
    }

    public static synchronized void syncOnForeground(Context context, Callback callback) {
        Context app = context.getApplicationContext();
        SyncSettings settings = new SyncSettings(app);
        if (!settings.autoSync() || !settings.configured()) return;
        long now = System.currentTimeMillis();
        if (now - lastForegroundAttempt < 15000) return;
        lastForegroundAttempt = now;
        EXECUTOR.execute(() -> run(app, RemoteSyncClient.Mode.AUTOMATIC, callback));
    }

    public static void runNow(
            Context context, RemoteSyncClient.Mode mode, Callback callback) {
        Context app = context.getApplicationContext();
        EXECUTOR.execute(() -> run(app, mode, callback));
    }

    private static void run(
            Context context, RemoteSyncClient.Mode mode, Callback callback) {
        if (!RUNNING.compareAndSet(false, true)) {
            deliver(callback, null, new IllegalStateException("同步正在进行中"));
            return;
        }
        try {
            RemoteSyncClient.Result result = new RemoteSyncClient(context).synchronize(mode);
            new SyncSettings(context).setLastStatus(result.message);
            deliver(callback, result, null);
        } catch (Exception error) {
            new SyncSettings(context).setLastStatus(error.getMessage());
            deliver(callback, null, error);
        } finally {
            RUNNING.set(false);
        }
    }

    private static void deliver(
            Callback callback, RemoteSyncClient.Result result, Exception error) {
        if (callback != null) MAIN.post(() -> callback.onComplete(result, error));
    }
}
