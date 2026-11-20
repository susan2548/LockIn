package th.studypace.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/** Listens exclusively for changes of the visible window's package. */
public final class GuardService extends AccessibilityService {
    private static volatile WeakReference<GuardService> running = new WeakReference<>(null);
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Set<String> protectedPackages = new HashSet<>();
    private final Runnable tick = this::refreshTimer;
    private final Calendar splitCalendar = Calendar.getInstance();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
    private SharedPreferences meter;
    private WindowManager windows;
    private JSONObject apps = new JSONObject();
    private JSONArray tasks = new JSONArray();
    private JSONObject focus;
    private String current = "";
    private String keyboard = "";
    private long lastElapsed;
    private long groupRemaining;
    private long exceptionEnd;
    private String exceptionPackage = "";
    private long exceptionFocusStart;
    private int breakMinutes = 5;
    private long pendingMs;
    private long pendingSinceWall;
    private long lastCheckpoint;
    private GuardPage page;
    private String pageKey = "";
    private boolean screenAvailable;
    private boolean ready;

    public static void refresh(Context unused) {
        GuardService service = running.get();
        if (service != null) service.main.post(service::reload);
    }

    public static boolean isRunning() { return running.get() != null; }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                account();
                flushUsage();
                saveMeter();
                screenAvailable = false;
                hide();
            } else {
                screenAvailable = canDisplay();
                // An unlock can restore the same Activity without another window event.
                lastElapsed = SystemClock.elapsedRealtime();
                evaluate();
            }
            schedule();
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (ready) { reload(); return; }
        windows = (WindowManager) getSystemService(WINDOW_SERVICE);
        meter = getSharedPreferences("guard_meter_v1", MODE_PRIVATE);
        groupRemaining = Math.max(0, meter.getLong("groupRemaining", 0L));
        exceptionEnd = meter.getLong("exceptionEnd", 0L);
        exceptionPackage = meter.getString("exceptionPackage", "");
        exceptionFocusStart = meter.getLong("exceptionFocusStart", 0L);
        protectedPackages.add(getPackageName());
        protectedPackages.add("android");
        protectedPackages.add("com.android.systemui");
        protectedPackages.add("com.android.settings");
        protectedPackages.add("com.android.permissioncontroller");
        protectedPackages.add("com.google.android.permissioncontroller");
        protect(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME));
        protect(new Intent(Intent.ACTION_DIAL));
        protect(new Intent(Settings.ACTION_SETTINGS));
        try {
            String ime = Settings.Secure.getString(getContentResolver(), Settings.Secure.DEFAULT_INPUT_METHOD);
            if (ime != null) keyboard = ime.split("/", 2)[0];
        } catch (RuntimeException ignored) {}
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        if (android.os.Build.VERSION.SDK_INT >= 33) registerReceiver(screen, filter, Context.RECEIVER_NOT_EXPORTED);
        else registerReceiver(screen, filter);
        screenAvailable = canDisplay();
        lastElapsed = SystemClock.elapsedRealtime();
        lastCheckpoint = lastElapsed;
        ready = true;
        running = new WeakReference<>(this);
        reload();
    }

    private void protect(Intent intent) {
        ResolveInfo result = getPackageManager().resolveActivity(intent, 0);
        if (result != null && result.activityInfo != null) protectedPackages.add(result.activityInfo.packageName);
    }

    private boolean canDisplay() {
        PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
        KeyguardManager lock = (KeyguardManager) getSystemService(KEYGUARD_SERVICE);
        return power != null && power.isInteractive() && (lock == null || !lock.isKeyguardLocked());
    }

    private void reload() {
        if (!ready) return;
        account();
        try {
            JSONObject rules = new JSONObject(Store.section(this, "rules"));
            apps = rules.optJSONObject("apps");
            if (apps == null) apps = new JSONObject();
            breakMinutes = Math.max(1, Math.min(180, rules.optInt("breakMinutes", 5)));
            String focusData = Store.section(this, "focus");
            focus = focusData != null && focusData.startsWith("{") ? new JSONObject(focusData) : null;
            tasks = new JSONArray(Store.section(this, "tasks"));
        } catch (JSONException | NullPointerException ignored) {}
        if (exceptionFocusStart != 0 && (focus == null || exceptionFocusStart != focus.optLong("start"))) {
            exceptionEnd = 0;
            exceptionFocusStart = 0;
            exceptionPackage = "";
            saveMeter();
        }
        if (app() == null) flushUsage();
        evaluate();
        schedule();
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {
        if (!ready || event == null || event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED ||
                event.getPackageName() == null) return;
        String next = event.getPackageName().toString();
        if (next.equals(keyboard)) return;
        String className = event.getClassName() == null ? "" : event.getClassName().toString();
        // Adding or dismissing our own panel may dispatch events after removeView returns.
        // Only the host activity constitutes actually leaving the app being timed.
        if (!current.equals(next) && next.equals(getPackageName()) && !className.endsWith("MainActivity")) return;
        if (!canDisplay()) {
            if (screenAvailable) {
                account();
                flushUsage();
                saveMeter();
            }
            screenAvailable = false;
            hide();
            schedule();
            return;
        }
        account();
        screenAvailable = true;
        if (!current.equals(next)) {
            flushUsage();
            saveMeter();
            hide();
            current = next;
        }
        evaluate();
        schedule();
    }

    private JSONObject app() {
        if (current.isEmpty() || protectedPackages.contains(current)) return null;
        JSONObject rule = apps.optJSONObject(current);
        if (rule == null) return null;
        String mode = rule.optString("mode");
        return ("always".equals(mode) || "focus".equals(mode)) ? rule : null;
    }

    private boolean focusing(long wall) {
        return focus != null && focus.optLong("end") > wall;
    }

    private boolean exception(long wall) {
        return focusing(wall) && exceptionEnd > wall && current.equals(exceptionPackage)
                && exceptionFocusStart == focus.optLong("start");
    }

    /** Account on the previous foreground state, before mutating that state. */
    private void account() {
        long elapsed = SystemClock.elapsedRealtime();
        long span = Math.max(0L, elapsed - lastElapsed);
        lastElapsed = elapsed;
        JSONObject rule = app();
        if (span == 0 || !screenAvailable || page != null || rule == null) return;
        long wall = System.currentTimeMillis();
        if (pendingMs == 0) pendingSinceWall = wall - span;
        pendingMs += span;
        if ("always".equals(rule.optString("mode")) && groupRemaining > 0 && !exception(wall - span)) {
            groupRemaining = Math.max(0, groupRemaining - span);
        }
    }

    private void flushUsage() {
        if (pendingMs <= 0 || current.isEmpty()) return;
        long start = pendingSinceWall;
        long stop = start + pendingMs;
        pendingMs = 0;
        JSONObject rule = apps.optJSONObject(current);
        String name = rule == null ? current : rule.optString("name", current);
        while (start < stop) {
            splitCalendar.setTimeInMillis(start);
            splitCalendar.add(Calendar.DAY_OF_MONTH, 1);
            splitCalendar.set(Calendar.HOUR_OF_DAY, 0);
            splitCalendar.set(Calendar.MINUTE, 0);
            splitCalendar.set(Calendar.SECOND, 0);
            splitCalendar.set(Calendar.MILLISECOND, 0);
            long next = Math.min(stop, splitCalendar.getTimeInMillis());
            if (next <= start) next = stop;
            Store.event(this, "usage", "", name, (next - start) / 1000.0, "", current, "", start);
            start = next;
        }
    }

    private void saveMeter() {
        if (meter == null) return;
        meter.edit().putLong("groupRemaining", groupRemaining)
                .putLong("exceptionEnd", exceptionEnd)
                .putLong("exceptionFocusStart", exceptionFocusStart)
                .putString("exceptionPackage", exceptionPackage).apply();
        lastCheckpoint = SystemClock.elapsedRealtime();
    }

    private void refreshTimer() {
        if (!ready) return;
        account();
        if (pendingMs >= 60000) flushUsage();
        if (SystemClock.elapsedRealtime() - lastCheckpoint >= 5000 && page == null) saveMeter();
        evaluate();
        schedule();
    }

    private void schedule() {
        main.removeCallbacks(tick);
        if (ready && screenAvailable && app() != null) main.postDelayed(tick, 1000L);
    }

    private JSONObject nextTask() {
        String today = dateFormat.format(new java.util.Date());
        JSONObject candidate = null;
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject t = tasks.optJSONObject(i);
            if (t == null || t.optBoolean("done")) continue;
            String day = t.optString("date");
            if (!day.isEmpty() && day.compareTo(today) > 0) continue;
            if (candidate == null || (today.equals(day) && !today.equals(candidate.optString("date")))) candidate = t;
        }
        return candidate;
    }

    private void evaluate() {
        JSONObject rule = app();
        long now = System.currentTimeMillis();
        boolean activeFocus = focusing(now);
        if (!screenAvailable || rule == null || exception(now) ||
                (!activeFocus && (!"always".equals(rule.optString("mode")) || groupRemaining > 0))) {
            hide();
            return;
        }
        JSONObject task = activeFocus ? focus : nextTask();
        String title = task == null ? "" : task.optString("title");
        String id = task == null ? "" : task.optString(activeFocus ? "taskId" : "id");
        long end = activeFocus ? focus.optLong("end") : 0;
        String key = current + "|" + id + "|" + title + "|" + end + "|" + breakMinutes;
        if (page != null && pageKey.equals(key)) {
            page.updateTime(end - now);
            return;
        }
        flushUsage();
        saveMeter();
        hide();
        GuardPage newPage = new GuardPage(this, rule.optString("name", current), title, activeFocus,
                breakMinutes, () -> returnToTask(activeFocus, id, title), () -> permit(activeFocus), this::goHome);
        newPage.updateTime(end - now);
        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.OPAQUE);
        params.setTitle("พอดี");
        try {
            windows.addView(newPage, params);
            page = newPage;
            pageKey = key;
        } catch (RuntimeException ignored) {
            // Service can be disabled while a window event is queued.
        }
    }

    private void decision(String type, String note) {
        JSONObject rule = app();
        if (rule == null) return;
        Store.event(this, type, "", rule.optString("name", current), 1, note, current, "", System.currentTimeMillis());
    }

    private void returnToTask(boolean activeFocus, String id, String title) {
        decision("return", activeFocus ? "กลับไปทำต่อ" : "เริ่มโฟกัส");
        if (!activeFocus) {
            Store.startFocus(this, id, title.isEmpty() ? "โฟกัส 5 นาที" : title, 5);
        }
        leaveWindow();
        Intent intent = AppRoutes.open(this, "focus");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(intent); }
        catch (RuntimeException ex) { performGlobalAction(GLOBAL_ACTION_HOME); }
    }

    private void permit(boolean wasFocusing) {
        long wall = System.currentTimeMillis();
        if (wasFocusing && focusing(wall)) {
            exceptionPackage = current;
            exceptionFocusStart = focus.optLong("start");
            exceptionEnd = Math.min(focus.optLong("end"), wall + 5 * 60000L);
            decision("open", "เปิดเฉพาะแอปนี้ชั่วคราว");
        } else {
            groupRemaining = breakMinutes * 60000L;
            decision("open", "เริ่มเวลาพักร่วม");
        }
        saveMeter();
        hide();
        lastElapsed = SystemClock.elapsedRealtime();
        evaluate();
        schedule();
    }

    private void leaveWindow() {
        hide();
        current = "";
        lastElapsed = SystemClock.elapsedRealtime();
        schedule();
    }

    private void goHome() {
        leaveWindow();
        performGlobalAction(GLOBAL_ACTION_HOME);
    }

    private void hide() {
        if (page != null) {
            GuardPage oldPage = page;
            page = null;
            pageKey = "";
            try { windows.removeViewImmediate(oldPage); } catch (RuntimeException ignored) {}
        }
    }

    @Override public void onInterrupt() {}

    @Override public void onDestroy() {
        if (ready) {
            account();
            flushUsage();
            saveMeter();
            ready = false;
            main.removeCallbacks(tick);
            running = new WeakReference<>(null);
            try { unregisterReceiver(screen); } catch (RuntimeException ignored) {}
            hide();
        }
        super.onDestroy();
    }
}
