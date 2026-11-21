package th.studypace.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.content.pm.PackageManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Interruption page above the selected app. The system navigation stays accessible. */
final class GuardPage extends FrameLayout {
    private static final int MIDNIGHT = 0xff101c3a;
    private static final int PAPER = 0xfff9faff;
    private static final int BLUE = 0xffb9ceff;
    private static final int MUTED = 0xffc0cce2;
    private final TextView remaining;
    private final boolean activeFocus;

    GuardPage(Context context, String pkg, String appName, String task, boolean inFocus, int minutes,
              Runnable backToTask, Runnable allow, Runnable goHome) {
        super(context);
        activeFocus = inFocus;
        setBackgroundColor(MIDNIGHT);
        setClickable(true);
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setVerticalScrollBarEnabled(false);
        addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setGravity(Gravity.CENTER_HORIZONTAL);
        column.setPadding(dp(26), dp(30), dp(26), dp(20));
        scroll.addView(column, new ScrollView.LayoutParams(-1, -2));

        TextView brand = label("Lock In", 25, PAPER, true);
        brand.setGravity(Gravity.START);
        column.addView(brand, new LinearLayout.LayoutParams(-1, -2));
        spacer(column, 20, 1);

        ImageView art = new ImageView(context);
        art.setImageResource(R.drawable.lockin_art);
        art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        GradientDrawable mask = new GradientDrawable();
        mask.setCornerRadius(dp(24));
        mask.setColor(PAPER);
        art.setBackground(mask);
        art.setClipToOutline(true);
        column.addView(art, new LinearLayout.LayoutParams(dp(106), dp(106)));
        spacer(column, 16, 0);
        LinearLayout appLine = new LinearLayout(context);
        appLine.setGravity(Gravity.CENTER);
        ImageView appIcon = new ImageView(context);
        try {
            appIcon.setImageDrawable(context.getPackageManager().getApplicationIcon(pkg));
            appLine.addView(appIcon, new LinearLayout.LayoutParams(dp(24), dp(24)));
        } catch (PackageManager.NameNotFoundException ignored) {}
        TextView contextLabel = label("พักก่อนเข้า " + appName, 15, MUTED, false);
        contextLabel.setPadding(dp(8), 0, 0, 0);
        appLine.addView(contextLabel);
        column.addView(appLine);
        String[] prompts = {"อีกห้านาที คุณอยากทำอะไรเสร็จ?", "พักนิ้ว แล้วกลับไปทำสิ่งที่ตั้งใจ", "ให้เวลาอีกนิดกับสิ่งที่สำคัญ"};
        int prompt = (int) ((System.currentTimeMillis() / 60000L) % prompts.length);
        TextView heading = label(inFocus ? "กลับไปต่อจากที่ค้างไว้ไหม?" : prompts[prompt], 26, PAPER, true);
        heading.setPadding(0, dp(12), 0, dp(6));
        column.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        if (!task.isEmpty()) {
            TextView goal = label("สิ่งที่ตั้งใจไว้ · " + task, 16, PAPER, false);
            goal.setMaxLines(3);
            goal.setEllipsize(android.text.TextUtils.TruncateAt.END);
            column.addView(goal);
        }
        remaining = label(inFocus ? "" : "เริ่มเพียงห้านาที แล้วค่อยเลือกว่าจะทำต่อไหม", 15, MUTED, false);
        column.addView(remaining);
        spacer(column, 28, 1);

        addButton(column, inFocus ? "กลับไปทำต่อ" : (task.isEmpty() ? "เริ่มโฟกัส 5 นาที" : "เริ่มทำ 5 นาที"),
                true, backToTask);
        spacer(column, 10, 0);
        addButton(column, inFocus ? "เปิด " + appName + " ชั่วคราว 5 นาที" : "พักดู " + minutes + " นาที",
                false, allow);
        spacer(column, 6, 0);
        Button home = button("กลับหน้าหลักโทรศัพท์", goHome);
        home.setTextColor(MUTED);
        home.setBackground(new RippleDrawable(ColorStateList.valueOf(0x225975a0), null, null));
        column.addView(home, new LinearLayout.LayoutParams(-1, -2));
    }

    void updateTime(long millis) {
        if (!activeFocus) return;
        long seconds = Math.max(0, (millis + 999) / 1000);
        remaining.setText(String.format(Locale.US, "เหลือเวลา %d:%02d นาที", seconds / 60, seconds % 60));
    }

    private void spacer(LinearLayout parent, int height, int weight) {
        parent.addView(new View(getContext()), new LinearLayout.LayoutParams(1, dp(height), weight));
    }

    private TextView label(String message, int size, int color, boolean bold) {
        TextView t = new TextView(getContext());
        t.setText(message);
        t.setTextColor(color);
        t.setTextSize(size);
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return t;
    }

    private Button button(String title, Runnable click) {
        Button button = new Button(getContext());
        button.setText(title);
        button.setTextSize(16);
        button.setAllCaps(false);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(12), dp(10), dp(12), dp(10));
        button.setMinHeight(dp(52));
        button.setStateListAnimator(null);
        button.setOnClickListener(v -> click.run());
        return button;
    }

    private void addButton(LinearLayout column, String title, boolean primary, Runnable action) {
        Button button = button(title, action);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(primary ? BLUE : Color.TRANSPARENT);
        shape.setCornerRadius(dp(16));
        if (!primary) shape.setStroke(dp(1), 0xff62749d);
        button.setTextColor(primary ? MIDNIGHT : PAPER);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x445975a0), shape, null));
        column.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
