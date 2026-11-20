package th.studypace.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;

/** Interruption page above the selected app. The system navigation stays accessible. */
final class GuardPage extends FrameLayout {
    private static final int FOREST = 0xff183c35;
    private static final int PAPER = 0xfff8f5ea;
    private static final int PEACH = 0xfff7b782;
    private static final int MUTED = 0xffb5cdc0;
    private final TextView remaining;
    private final boolean activeFocus;

    GuardPage(Context context, String appName, String task, boolean inFocus, int minutes,
              Runnable backToTask, Runnable allow, Runnable goHome) {
        super(context);
        activeFocus = inFocus;
        setBackgroundColor(FOREST);
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

        TextView brand = label("พอดี", 21, PAPER, true);
        brand.setGravity(Gravity.START);
        column.addView(brand, new LinearLayout.LayoutParams(-1, -2));
        spacer(column, 20, 1);

        View sun = new View(context) {
            private final Paint pen = new Paint(Paint.ANTI_ALIAS_FLAG);
            @Override protected void onDraw(Canvas canvas) {
                super.onDraw(canvas);
                float cx = getWidth() / 2f, horizon = getHeight() * 0.67f, r = dp(30);
                pen.setColor(PEACH);
                canvas.save();
                canvas.clipRect(0, 0, getWidth(), horizon);
                canvas.drawCircle(cx, horizon, r, pen);
                canvas.restore();
                pen.setColor(MUTED);
                pen.setStrokeWidth(dp(1.2f));
                canvas.drawLine(cx - dp(55), horizon + dp(5), cx + dp(55), horizon + dp(5), pen);
                canvas.drawLine(cx - dp(34), horizon + dp(12), cx + dp(34), horizon + dp(12), pen);
            }
        };
        sun.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        column.addView(sun, new LinearLayout.LayoutParams(-1, dp(80)));
        spacer(column, 12, 0);
        TextView contextLabel = label(inFocus ? "กำลังโฟกัส" : "ก่อนเปิด " + appName, 15, MUTED, false);
        column.addView(contextLabel);
        TextView heading = label(inFocus ? (task.isEmpty() ? "กลับไปโฟกัสต่อ" : task)
                        : (task.isEmpty() ? "อยากพักไถสักครู่ไหม" : task),
                30, PAPER, true);
        heading.setMaxLines(4);
        heading.setEllipsize(android.text.TextUtils.TruncateAt.END);
        heading.setPadding(0, dp(8), 0, dp(4));
        column.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        remaining = label(inFocus ? "" : (task.isEmpty() ? "ตั้งเวลาพักร่วมกันสำหรับแอปที่เลือก"
                : "งานที่คุณตั้งใจทำวันนี้"), 16, MUTED, false);
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
        home.setBackground(new RippleDrawable(ColorStateList.valueOf(0x227ba697), null, null));
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
        shape.setColor(primary ? PEACH : Color.TRANSPARENT);
        shape.setCornerRadius(dp(16));
        if (!primary) shape.setStroke(dp(1), 0xff709084);
        button.setTextColor(primary ? FOREST : PAPER);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x44709084), shape, null));
        column.addView(button, new LinearLayout.LayoutParams(-1, -2));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
