package th.studypace.app;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.net.Uri;
import android.service.notification.StatusBarNotification;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.Map;
import java.util.TreeMap;

/** One OS alarm tracks the next reminder. Calendar occurrences are calculated in local time. */
public class Alerts extends BroadcastReceiver {
    private static final String TICK = "th.studypace.app.ALERT_TICK";
    private static final String SNOOZE = "th.studypace.app.ALERT_SNOOZE";
    private static final String LOG_HABIT = "th.studypace.app.ALERT_LOG_HABIT";
    private static final String PLANNER = "pordee.planner.v1";
    private static final String REMINDERS = "pordee.reminders";
    private static final String JOURNAL = "pordee.journal";
    private static final String FOCUS = "pordee.focus";
    private static final long MINUTE = 60_000L;
    private static final long DAY = 24 * 60 * MINUTE;
    private static final int IMMUTABLE = PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT;

    private static final class Item {
        final String key, kind, id, title, body, route;
        final long time, grace;
        Item(String key, String kind, String id, String title, String body,
             String route, long time, long grace) {
            this.key = key;
            this.kind = kind;
            this.id = id;
            this.title = title;
            this.body = body;
            this.route = route;
            this.time = time;
            this.grace = grace;
        }
        boolean routine() { return "task".equals(kind) || "habit".equals(kind); }
    }

    @Override public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;
        synchronized (Alerts.class) {
            SharedPreferences prefs = context.getSharedPreferences(PLANNER, 0);
            if (SNOOZE.equals(action) || LOG_HABIT.equals(action)) {
                handleAction(context, prefs, intent);
                reschedule(context);
                return;
            }
            // Alarms do not survive reboot; also recompute when the system clock changes.
            prefs.edit().remove("next").apply();
            reschedule(context);
        }
    }

    public static synchronized void reschedule(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PLANNER, 0);
        long now = System.currentTimeMillis();
        long firstUse = prefs.getLong("firstUse", now);
        if (!prefs.contains("firstUse")) prefs.edit().putLong("firstUse", firstUse).apply();
        JSONObject data = Store.read(context);
        ZoneId zone = ZoneId.systemDefault();
        LocalDate today = dateAt(now, zone);
        Map<String, Item> items = new TreeMap<>();
        JSONArray tasks = array(data, "tasks");
        for (int i = 0; i < tasks.length(); i++) {
            JSONObject task = tasks.optJSONObject(i);
            if (task == null || task.optBoolean("done")) continue;
            String id = task.optString("id");
            if (id.isEmpty()) continue;
            LocalDate day = parseDay(task.optString("date"));
            LocalTime time = parseTime(task.optString("time"));
            if (day == null || time == null) continue;
            String key = "task/" + id + "/" + day + "/" + time;
            put(items, new Item(key, "task", id, task.optString("title", "งานที่วางไว้"),
                "ถึงเวลาที่ตั้งไว้ · แตะเพื่อเริ่มรอบโฟกัส", "start:" + id,
                epoch(day, time, zone), 3 * 60 * MINUTE));
        }

        JSONArray habits = array(data, "habits"), events = array(data, "events");
        for (int i = 0; i < habits.length(); i++) {
            JSONObject habit = habits.optJSONObject(i);
            if (habit == null) continue;
            String id = habit.optString("id");
            LocalTime time = parseTime(habit.optString("time"));
            if (time == null || id.isEmpty()) continue;
            String unit = habit.optString("unit", "ครั้ง");
            double goal = Math.max(1, habit.optDouble("goal", 1));
            // Include the start of a new week even when a weekly goal was already met.
            for (int add = -1; add <= 8; add++) {
                LocalDate day = today.plusDays(add);
                if (logged(habit, events, day, zone) >= goal) continue;
                String key = "habit/" + id + "/" + day;
                put(items, new Item(key, "habit", id, habit.optString("title", "กิจวัตร"),
                    "บันทึกความคืบหน้าวันนี้ · +1 " + unit, "habit:" + id,
                    epoch(day, time, zone), 2 * 60 * MINUTE));
            }
        }

        JSONObject rules = data.optJSONObject("rules");
        if (rules == null) rules = new JSONObject();
        LocalTime recap = parseTime(rules.optString("dailyTime", "22:30"));
        if (recap != null) {
            for (int add = -1; add <= 1; add++) {
                LocalDate day = today.plusDays(add);
                long time = epoch(day, recap, zone);
                if (dateAt(firstUse, zone).isAfter(day)) continue;
                put(items, new Item("day/" + day, "journal", day.toString(), "บันทึกประจำวันพร้อมแล้ว",
                    "ดูงานที่ทำเสร็จ กิจวัตร และรอบโฟกัสของวันนี้", "journal:" + day,
                    time, 6 * 60 * MINUTE));
            }
            YearMonth current = YearMonth.from(today);
            for (int add = 0; add <= 1; add++) {
                YearMonth next = current.plusMonths(add);
                LocalDate openingDay = next.atDay(1);
                if (!dateAt(firstUse, zone).isBefore(openingDay)) continue;
                String month = next.minusMonths(1).toString();
                put(items, new Item("month/" + month, "journal", month,
                    "ย้อนดูเดือนที่ผ่านมา", "สรุปภาพรวมรายเดือนพร้อมให้เปิดดูและบันทึกเป็นภาพ",
                    "month:" + month, epoch(openingDay, recap, zone), DAY));
            }
        }
        JSONObject focus = data.optJSONObject("focus");
        long focusEnd = focus == null ? 0 : focus.optLong("end");
        if (focusEnd > 0) {
            put(items, new Item("focus/" + focus.optLong("start") + "/" + focusEnd, "focus", "",
                "ครบรอบโฟกัสแล้ว", focus.optString("title", "บันทึกความคืบหน้ารอบนี้"),
                "focus", focusEnd, DAY));
        }
        long next = Long.MAX_VALUE;
        LocalTime quietStart = parseTime(rules.optString("quietStart"));
        LocalTime quietEnd = parseTime(rules.optString("quietEnd"));
        cancelObsolete(context, items, today, zone);
        SharedPreferences.Editor editor = prefs.edit();
        for (Item item : items.values()) {
            String done = "sent/" + item.key, later = "later/" + item.key;
            long snooze = prefs.getLong(later, 0);
            if (snooze == 0 && prefs.contains(done)) continue;
            long when = Math.max(item.time, snooze);
            if (item.routine()) {
                // Postpone planned reminders overlapping a quiet period or a focus round.
                when = quietUntil(when, quietStart, quietEnd, zone);
                if (focus != null && when >= focus.optLong("start") && when < focusEnd)
                    when = quietUntil(focusEnd, quietStart, quietEnd, zone);
            }
            if (now > when + item.grace) continue;
            if (item.routine() && when <= now) {
                long resumeAt = quietUntil(now, quietStart, quietEnd, zone);
                if (resumeAt > now) when = resumeAt;
                if (focus != null && now >= focus.optLong("start") && now < focusEnd)
                    when = Math.max(when, quietUntil(focusEnd, quietStart, quietEnd, zone));
            }
            if ("habit".equals(item.kind) && !dateAt(Math.max(when, now), zone).equals(dateAt(item.time, zone))
                && when <= now) continue;
            if (when > now) {
                next = Math.min(next, when);
                continue;
            }
            show(context, item, snooze);
            editor.putLong(done, now).remove(later);
        }
        // Discard occurrence markers after all grace periods have passed.
        for (Map.Entry<String, ?> stored : prefs.getAll().entrySet()) {
            String key = stored.getKey();
            if (!(stored.getValue() instanceof Long)) continue;
            if ((key.startsWith("sent/") || key.startsWith("acted/") || key.startsWith("later/"))
                && (Long) stored.getValue() < now - DAY * 8) editor.remove(key);
        }
        editor.apply();
        arm(context, prefs, next);
    }

    private static void put(Map<String, Item> items, Item item) { items.put(item.key, item); }

    private static void cancelObsolete(Context context, Map<String, Item> items, LocalDate today, ZoneId zone) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return;
        for (StatusBarNotification posted : manager.getActiveNotifications()) {
            String key = posted.getTag();
            if (key == null || posted.getId() != 1) continue;
            if (!(key.startsWith("task/") || key.startsWith("habit/") || key.startsWith("focus/"))) continue;
            Item item = items.get(key);
            if (item == null || ("habit".equals(item.kind) && today.isAfter(dateAt(item.time, zone))))
                manager.cancel(key, posted.getId());
        }
    }

    private static void arm(Context context, SharedPreferences prefs, long next) {
        AlarmManager alarms = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (alarms == null) return;
        PendingIntent tick = PendingIntent.getBroadcast(context, 0,
            new Intent(context, Alerts.class).setAction(TICK), IMMUTABLE);
        if (next == Long.MAX_VALUE) {
            alarms.cancel(tick);
            prefs.edit().remove("next").apply();
            return;
        }
        if (prefs.getLong("next", -1) == next) return;
        try {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, tick);
        } catch (SecurityException noExactPermission) {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next, tick);
        }
        prefs.edit().putLong("next", next).apply();
    }

    private static void show(Context context, Item item, long snooze) {
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager == null || !manager.areNotificationsEnabled()) return;
        String channel = item.routine() ? REMINDERS : "focus".equals(item.kind) ? FOCUS : JOURNAL;
        String channelName = item.routine() ? "งานและกิจวัตร" : "focus".equals(item.kind) ? "รอบโฟกัส" : "สรุปบันทึก";
        manager.createNotificationChannel(new NotificationChannel(channel, channelName, NotificationManager.IMPORTANCE_DEFAULT));
        int icon = context.getResources().getIdentifier("ic_reminder", "drawable", context.getPackageName());
        if (icon == 0) icon = android.R.drawable.ic_popup_reminder;
        PendingIntent open = openRoute(context, item);
        Notification.Builder notice = new Notification.Builder(context, channel)
            .setSmallIcon(icon).setContentTitle(item.title).setContentText(item.body)
            .setColor(Color.rgb(37, 77, 219)).setContentIntent(open).setAutoCancel(true)
            .setVisibility(Notification.VISIBILITY_PRIVATE).setOnlyAlertOnce(false);
        if ("task".equals(item.kind)) {
            notice.addAction(new Notification.Action.Builder(null, "เริ่มโฟกัส", open).build());
        } else if ("habit".equals(item.kind)) {
            notice.addAction(new Notification.Action.Builder(null, "บันทึก +1",
                action(context, LOG_HABIT, item, snooze)).build());
        }
        if (item.routine()) notice.addAction(new Notification.Action.Builder(null, "เลื่อน 10 นาที",
            action(context, SNOOZE, item, snooze)).build());
        try {
            manager.notify(item.key, 1, notice.build());
        } catch (SecurityException denied) {
            Log.i("PordeeAlerts", "Notification permission is unavailable");
        }
    }

    private static PendingIntent openRoute(Context context, Item item) {
        Intent intent = AppRoutes.open(context, item.route).setAction(Intent.ACTION_VIEW)
            .setData(new Uri.Builder().scheme("pordee-alert").authority("open")
                .appendPath(item.key).build())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        return PendingIntent.getActivity(context, 0, intent, IMMUTABLE);
    }

    private static PendingIntent action(Context context, String action, Item item, long snooze) {
        Intent intent = new Intent(context, Alerts.class).setAction(action)
            .setData(new Uri.Builder().scheme("pordee-alert").authority("action")
                .appendPath(item.key).appendPath(String.valueOf(snooze)).build())
            .putExtra("key", item.key).putExtra("id", item.id)
            .putExtra("at", item.time).putExtra("generation", snooze);
        return PendingIntent.getBroadcast(context, 0, intent, IMMUTABLE);
    }

    private static void handleAction(Context context, SharedPreferences prefs, Intent intent) {
        String key = intent.getStringExtra("key"), id = intent.getStringExtra("id");
        if (key == null || id == null) return;
        String acted = "acted/" + key + "/" + intent.getLongExtra("generation", 0);
        if (prefs.contains(acted)) return;
        long now = System.currentTimeMillis();
        boolean defer = SNOOZE.equals(intent.getAction());
        if (!defer) {
            ZoneId zone = ZoneId.systemDefault();
            LocalDate today = dateAt(now, zone);
            JSONObject data = Store.read(context);
            JSONArray habits = array(data, "habits");
            if (today.equals(dateAt(intent.getLongExtra("at", 0), zone))) {
                for (int i = 0; i < habits.length(); i++) {
                    JSONObject habit = habits.optJSONObject(i);
                    if (habit == null || !id.equals(habit.optString("id"))) continue;
                    if (logged(habit, array(data, "events"), today, zone) < Math.max(1, habit.optDouble("goal", 1))) {
                        Store.event(context, "habit", id, habit.optString("title"), 1, "", "",
                            habit.optString("unit", "ครั้ง"), now, habit.optString("icon"));
                    }
                    break;
                }
            }
        }
        SharedPreferences.Editor editor = prefs.edit().putLong(acted, now);
        if (defer) editor.putLong("later/" + key, now + 10 * MINUTE);
        editor.apply();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (manager != null) manager.cancel(key, 1);
    }

    private static JSONArray array(JSONObject obj, String key) {
        JSONArray result = obj.optJSONArray(key);
        return result == null ? new JSONArray() : result;
    }
    private static LocalDate parseDay(String str) {
        try { return LocalDate.parse(str); } catch (DateTimeParseException e) { return null; }
    }
    private static LocalTime parseTime(String str) {
        try { return LocalTime.parse(str); } catch (DateTimeParseException e) { return null; }
    }
    private static LocalDate dateAt(long millis, ZoneId zone) {
        return Instant.ofEpochMilli(millis).atZone(zone).toLocalDate();
    }
    private static long epoch(LocalDate day, LocalTime time, ZoneId zone) {
        return day.atTime(time).atZone(zone).toInstant().toEpochMilli();
    }
    private static long quietUntil(long millis, LocalTime start, LocalTime end, ZoneId zone) {
        if (start == null || end == null || start.equals(end)) return millis;
        java.time.ZonedDateTime local = Instant.ofEpochMilli(millis).atZone(zone);
        LocalTime time = local.toLocalTime();
        LocalDate day = local.toLocalDate();
        if (start.isBefore(end))
            return !time.isBefore(start) && time.isBefore(end) ? epoch(day, end, zone) : millis;
        if (!time.isBefore(start)) return epoch(day.plusDays(1), end, zone);
        return time.isBefore(end) ? epoch(day, end, zone) : millis;
    }
    private static double logged(JSONObject habit, JSONArray events, LocalDate day, ZoneId zone) {
        boolean weekly = "week".equals(habit.optString("period"));
        LocalDate start = weekly ? day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)) : day;
        long from = epoch(start, LocalTime.MIDNIGHT, zone);
        long until = epoch(start.plusDays(weekly ? 7 : 1), LocalTime.MIDNIGHT, zone);
        String id = habit.optString("id");
        double count = 0;
        for (int i = 0; i < events.length(); i++) {
            JSONObject e = events.optJSONObject(i);
            if (e == null || !"habit".equals(e.optString("type")) || !id.equals(e.optString("id"))) continue;
            long at = e.optLong("at");
            if (at >= from && at < until) count += e.optDouble("value", 1);
        }
        return count;
    }
}
