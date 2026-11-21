package th.studypace.app;

import android.accessibilityservice.AccessibilityService;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.view.accessibility.AccessibilityNodeInfo;
import android.os.Handler;
import android.os.Looper;
import android.os.PowerManager;
import android.os.SystemClock;
import android.provider.Settings;
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
    private GateActivity gate;
    private String gatePackage = "";
    private long requestedAt;
    private boolean scheduled;
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

    static GuardService active() { return running.get(); }

    public static boolean isRunning() { return running.get() != null; }

    private final BroadcastReceiver screen = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (Intent.ACTION_SCREEN_OFF.equals(intent.getAction())) {
                account();
                flushUsage();
                saveMeter();
                screenAvailable = false;
                current = "";
                closeGate();
            } else {
                screenAvailable = canDisplay();
                // An unlock can restore the same Activity without another window event.
                lastElapsed = SystemClock.elapsedRealtime();
                reconcile(null);
            }
            schedule();
        }
    };

    @Override protected void onServiceConnected() {
        super.onServiceConnected();
        if (ready) { reload(); return; }
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
        if (!ready || event == null || apps.length() == 0) return;
        String reported = null;
        if (event.getEventType() == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED && event.getPackageName() != null)
            reported = event.getPackageName().toString();
        reconcile(reported);
        schedule();
    }

    // Read only the package on the active root. Never traverse children, text or events' contents.
    private void reconcile(String reported) {
        account();
        screenAvailable = canDisplay();
        if (!screenAvailable) {
            flushUsage();
            current = "";
            closeGate();
            return;
        }
        String foreground = null;
        AccessibilityNodeInfo root = null;
        try {
            root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) foreground = root.getPackageName().toString();
        } catch (RuntimeException ignored) {
        } finally {
            if (root != null) root.recycle();
        }
        if (foreground == null || foreground.equals(keyboard)) foreground = reported;
        if (foreground != null && !foreground.equals(keyboard) && !foreground.equals(current)) {
            flushUsage();
            saveMeter();
            current = foreground;
        }
        evaluate();
    }

    private JSONObject app() {
        return ruleFor(current);
    }

    private JSONObject ruleFor(String pkg) {
        if (pkg.isEmpty() || protectedPackages.contains(pkg)) return null;
        JSONObject rule = apps.optJSONObject(pkg);
        if (rule == null) return null;
        String mode = rule.optString("mode");
        return ("always".equals(mode) || "focus".equals(mode)) ? rule : null;
    }

    private boolean focusing(long wall) {
        return focus != null && focus.optLong("end") > wall;
    }

    private boolean exception(String pkg, long wall) {
        return focusing(wall) && exceptionEnd > wall && pkg.equals(exceptionPackage)
                && exceptionFocusStart == focus.optLong("start");
    }

    /** Account on the previous foreground state, before mutating that state. */
    private void account() {
        long elapsed = SystemClock.elapsedRealtime();
        long span = Math.max(0L, elapsed - lastElapsed);
        lastElapsed = elapsed;
        JSONObject rule = app();
        if (span == 0 || !screenAvailable || current.equals(gatePackage) || rule == null) return;
        long wall = System.currentTimeMillis();
        if (pendingMs == 0) pendingSinceWall = wall - span;
        pendingMs += span;
        if ("always".equals(rule.optString("mode")) && groupRemaining > 0 && !exception(current, wall - span)) {
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
        scheduled = false;
        reconcile(null);
        if (pendingMs >= 60000) flushUsage();
        if (pendingMs > 0 && SystemClock.elapsedRealtime() - lastCheckpoint >= 5000) saveMeter();
        schedule();
    }

    private void schedule() {
        if (!ready || !screenAvailable || apps.length() == 0) {
            main.removeCallbacks(tick);
            scheduled = false;
        } else if (!scheduled) {
            scheduled = true;
            main.postDelayed(tick, 800L);
        }
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

    private boolean blocked(String pkg, long now) {
        JSONObject rule = ruleFor(pkg);
        return rule != null && !exception(pkg, now) &&
                (focusing(now) || ("always".equals(rule.optString("mode")) && groupRemaining <= 0));
    }

    private void evaluate() {
        long now = System.currentTimeMillis();
        if (!screenAvailable || (!gatePackage.isEmpty() && !blocked(gatePackage, now))) closeGate();
        if (gate == null && requestedAt != 0 && !current.equals(getPackageName()) && !current.equals(gatePackage)
                && SystemClock.elapsedRealtime() - requestedAt >= 1600L) closeGate();
        if (gate != null && !gate.isFinishing()) {
            renderGate();
            if (current.equals(getPackageName()) || gate.hasWindowFocus()) return;
        }
        if (!screenAvailable || !blocked(current, now)) return;
        if (current.equals(gatePackage) && requestedAt != 0) {
            // Some vendor builds can suppress a background activity launch. Never leave the feed open.
            if (gate == null && SystemClock.elapsedRealtime() - requestedAt >= 1600L) goHome();
            return;
        }
        flushUsage();
        saveMeter();
        gatePackage = current;
        requestedAt = SystemClock.elapsedRealtime();
        Intent show = new Intent(this, GateActivity.class).putExtra("package", current)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(show); } catch (RuntimeException unavailable) { goHome(); }
    }

    boolean attachGate(GateActivity activity, String pkg) {
        if (pkg == null || !pkg.equals(gatePackage) || !blocked(pkg, System.currentTimeMillis()) || !canDisplay()) return false;
        if (gate != activity) pageKey = "";
        gate = activity;
        renderGate();
        return true;
    }

    void detachGate(GateActivity activity) {
        if (gate != activity) return;
        gate = null;
        page = null;
        pageKey = "";
        if (gatePackage.equals(activity.getIntent().getStringExtra("package"))) {
            gatePackage = "";
            requestedAt = 0;
        }
        if (current.equals(getPackageName())) current = "";
        schedule();
    }

    private void renderGate() {
        long now = System.currentTimeMillis();
        boolean activeFocus = focusing(now);
        JSONObject rule = ruleFor(gatePackage);
        if (rule == null || gate == null) return;
        JSONObject task = activeFocus ? focus : nextTask();
        String title = task == null ? "" : task.optString("title");
        String id = task == null ? "" : task.optString(activeFocus ? "taskId" : "id");
        long end = activeFocus ? focus.optLong("end") : 0;
        String key = gatePackage + "|" + id + "|" + title + "|" + end + "|" + breakMinutes;
        if (page != null && pageKey.equals(key)) {
            page.updateTime(end - now);
            return;
        }
        String pkg = gatePackage;
        page = new GuardPage(gate, pkg, rule.optString("name", pkg), title, activeFocus,
                breakMinutes, () -> returnToTask(activeFocus, id, title), () -> permit(activeFocus), this::goHome);
        page.updateTime(end - now);
        gate.setContentView(page);
        pageKey = key;
    }

    private void decision(String type, String note) {
        JSONObject rule = ruleFor(gatePackage);
        if (rule != null) Store.event(this, type, "", rule.optString("name", gatePackage), 1, note, gatePackage, "", System.currentTimeMillis());
    }

    private void returnToTask(boolean activeFocus, String id, String title) {
        decision("return", activeFocus ? "กลับไปทำต่อ" : "เริ่มโฟกัส");
        if (!activeFocus) {
            Store.startFocus(this, id, title.isEmpty() ? "โฟกัส 5 นาที" : title, 5);
        }
        Intent intent = AppRoutes.open(this, "focus");
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        try { startActivity(intent); }
        catch (RuntimeException ex) { performGlobalAction(GLOBAL_ACTION_HOME); }
        leaveWindow();
    }

    private void permit(boolean wasFocusing) {
        long wall = System.currentTimeMillis();
        if (wasFocusing && focusing(wall)) {
            exceptionPackage = gatePackage;
            exceptionFocusStart = focus.optLong("start");
            exceptionEnd = Math.min(focus.optLong("end"), wall + 5 * 60000L);
            decision("open", "เปิดเฉพาะแอปนี้ชั่วคราว");
        } else {
            groupRemaining = breakMinutes * 60000L;
            decision("open", "เริ่มเวลาพักร่วม");
        }
        saveMeter();
        Intent open = getPackageManager().getLaunchIntentForPackage(gatePackage);
        if (open != null) {
            open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_REORDER_TO_FRONT);
            try { startActivity(open); } catch (RuntimeException ignored) {}
        }
        leaveWindow();
    }

    private void leaveWindow() {
        closeGate();
        current = "";
        lastElapsed = SystemClock.elapsedRealtime();
        schedule();
    }

    void goHome() {
        performGlobalAction(GLOBAL_ACTION_HOME);
        leaveWindow();
    }

    private void closeGate() {
        GateActivity previous = gate;
        gate = null;
        page = null;
        pageKey = "";
        gatePackage = "";
        requestedAt = 0;
        if (previous != null && !previous.isFinishing()) previous.finishAndRemoveTask();
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
            closeGate();
        }
        super.onDestroy();
    }
}
