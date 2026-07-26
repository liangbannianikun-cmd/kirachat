package app.miuix.tavern.util;

import android.app.Activity;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import app.miuix.tavern.R;

public final class ChatNotificationManager {
    private static final String CHANNEL_ID = "chat_replies";
    private static final String NOTIFICATION_PERMISSION =
            "android.permission.POST_NOTIFICATIONS";
    public static final int REQUEST_NOTIFICATION_PERMISSION = 9721;

    private ChatNotificationManager() {
    }

    public static void prepare(Activity activity) {
        createChannel(activity);
        if (Build.VERSION.SDK_INT >= 33
                && activity.checkSelfPermission(NOTIFICATION_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            activity.requestPermissions(
                    new String[]{NOTIFICATION_PERMISSION},
                    REQUEST_NOTIFICATION_PERMISSION);
        }
    }

    public static void notifyReply(Context context, String conversationId,
                                   String title, String content, Intent openIntent) {
        createChannel(context);
        if (Build.VERSION.SDK_INT >= 33
                && context.checkSelfPermission(NOTIFICATION_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        openIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        int requestCode = conversationId == null ? 0 : conversationId.hashCode();
        PendingIntent pending = PendingIntent.getActivity(
                context, requestCode, openIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        NotificationCompat.Builder builder = new NotificationCompat.Builder(
                context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setStyle(new NotificationCompat.BigTextStyle().bigText(content))
                .setContentIntent(pending)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH);
        NotificationManagerCompat.from(context).notify(requestCode, builder.build());
    }

    private static void createChannel(Context context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager =
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null) return;
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH);
        channel.setDescription(
                context.getString(R.string.notification_channel_description));
        manager.createNotificationChannel(channel);
    }
}
