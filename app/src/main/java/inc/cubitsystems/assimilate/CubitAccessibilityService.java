package inc.cubitsystems.assimilate;

import android.accessibilityservice.AccessibilityService;
import android.content.SharedPreferences;
import android.view.accessibility.AccessibilityEvent;

/**
 * Minimal accessibility extremity: records last app package on window change.
 * Does NOT capture keystrokes, passwords, or content.
 */
public class CubitAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED)
            return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        SharedPreferences p = getSharedPreferences("assimilate", MODE_PRIVATE);
        if (!p.getBoolean("a11y_log", false)) return;
        p.edit().putString("last_pkg", pkg.toString())
                .putLong("last_pkg_ms", System.currentTimeMillis()).apply();
    }

    @Override
    public void onInterrupt() {}
}
