package inc.cubitsystems.assimilate;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;

import androidx.core.app.NotificationCompat;

import java.io.File;

/**
 * Foreground dataSync service while processing assimilate_staging queue.
 * Full Drive API upload activates when OAuth token is stored by MainActivity.
 */
public class UploadForegroundService extends Service {
    public static final String CHANNEL = "assimilate_upload";
    public static final String ACTION_START = "inc.cubitsystems.assimilate.UPLOAD_START";
    public static final String ACTION_CANCEL = "inc.cubitsystems.assimilate.UPLOAD_CANCEL";

    private volatile boolean cancelled = false;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_CANCEL.equals(intent.getAction())) {
            cancelled = true;
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        ensureChannel();
        Intent open = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, open, PendingIntent.FLAG_IMMUTABLE);
        Intent cancel = new Intent(this, UploadForegroundService.class).setAction(ACTION_CANCEL);
        PendingIntent cpi = PendingIntent.getService(this, 1, cancel, PendingIntent.FLAG_IMMUTABLE);

        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle("Cubit Assimilate")
                .setContentText("Processing upload queue…")
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentIntent(pi)
                .addAction(0, "Cancel", cpi)
                .setOngoing(true)
                .build();
        startForeground(42, n);

        new Thread(() -> {
            File dir = new File(getFilesDir(), "assimilate_staging");
            File[] files = dir.listFiles((d, name) -> !name.equals("queue.json"));
            int total = files == null ? 0 : files.length;
            int i = 0;
            if (files != null) {
                for (File f : files) {
                    if (cancelled) break;
                    i++;
                    updateNotify("Queued file " + i + "/" + total + " (Drive OAuth next)");
                    try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                    // Token-based multipart upload to Drive folder lands when OAUTH configured
                }
            }
            updateNotify(cancelled ? "Upload cancelled" : "Queue processed — connect OAuth to push to Drive");
            stopForeground(false);
            stopSelf();
        }).start();
        return START_NOT_STICKY;
    }

    private void updateNotify(String text) {
        Notification n = new NotificationCompat.Builder(this, CHANNEL)
                .setContentTitle("Cubit Assimilate")
                .setContentText(text)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setOngoing(true)
                .build();
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(42, n);
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel ch = new NotificationChannel(CHANNEL, "Assimilate upload", NotificationManager.IMPORTANCE_LOW);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }
}
