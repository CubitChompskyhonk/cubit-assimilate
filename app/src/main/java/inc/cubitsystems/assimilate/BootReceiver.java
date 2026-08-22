package inc.cubitsystems.assimilate;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

/** Optional: notes boot for future scheduled assimilate — does not auto-exfiltrate. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent == null || !Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())) return;
        SharedPreferences p = context.getSharedPreferences("assimilate", Context.MODE_PRIVATE);
        if (!p.getBoolean("schedule_enabled", false)) return;
        // Future: enqueue WorkManager backup. No network upload without OAuth + user policy.
        p.edit().putLong("last_boot_ms", System.currentTimeMillis()).apply();
    }
}
