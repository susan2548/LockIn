package th.studypace.app;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.content.res.ColorStateList;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.inputmethod.InputMethodManager;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.content.pm.PackageManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.Locale;
import java.util.function.IntConsumer;

/** Interruption page above the selected app. The system navigation stays accessible. */
final class GuardPage extends FrameLayout {
    private static final int MIDNIGHT = 0xff101c3a;
    private static final int PAPER = 0xfff9faff;
    private static final int BLUE = 0xffb9ceff;
    private static final int MUTED = 0xffc0cce2;
    private final TextView remaining;
    private final boolean activeFocus;
    private ScrollView durationPane;
    private Button durationTrigger;

    GuardPage(Context context, String pkg, String appName, String task, boolean inFocus, int minutes,
              IntConsumer backToTask, IntConsumer allow, Runnable goHome) {
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
        String[] prompts = {"รอบนี้ คุณอยากทำอะไรให้เสร็จ?", "พักนิ้ว แล้วกลับไปทำสิ่งที่ตั้งใจ", "ให้เวลาอีกนิดกับสิ่งที่สำคัญ"};
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
        remaining = label(inFocus ? "" : "เลือกระยะเวลาที่เหมาะกับคุณสำหรับรอบนี้", 15, MUTED, false);
        column.addView(remaining);
        spacer(column, 28, 1);

        addButton(column, inFocus ? "กลับไปทำต่อ" : "เลือกเวลาโฟกัส", true,
                () -> { if (inFocus) backToTask.accept(0); else chooseDuration(true, 5, backToTask); });
        spacer(column, 10, 0);
        addButton(column, "เลือกเวลาพักดู", false, () -> chooseDuration(false, minutes, allow));
        spacer(column, 6, 0);
        Button home = button("กลับหน้าหลักโทรศัพท์", goHome);
        home.setTextColor(MUTED);
        home.setBackground(new RippleDrawable(ColorStateList.valueOf(0x225975a0), null, null));
        column.addView(home, new LinearLayout.LayoutParams(-1, -2));
    }

    private void chooseDuration(boolean forFocus, int initial, IntConsumer commit) {
        if (durationPane != null) return;
        View focused = findFocus();
        durationTrigger = focused instanceof Button ? (Button) focused : null;
        getChildAt(0).setVisibility(GONE);
        Context ctx = getContext();
        durationPane = new ScrollView(ctx);
        durationPane.setFillViewport(true);
        addView(durationPane, new FrameLayout.LayoutParams(-1, -1));
        LinearLayout body = new LinearLayout(ctx);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setGravity(Gravity.CENTER_HORIZONTAL);
        body.setFocusableInTouchMode(true);
        body.setPadding(dp(24), dp(24), dp(24), dp(20));
        durationPane.addView(body);
        TextView title = label(forFocus ? "เวลาโฟกัส" : "เวลาพักดู", 27, PAPER, true);
        body.addView(title);
        String description = forFocus ? "เลือกเวลาสำหรับรอบที่กำลังจะเริ่ม"
                : activeFocus ? "พักได้เฉพาะแอปนี้ จนถึงเวลาที่เลือกหรือจนรอบโฟกัสจบ" : "ใช้เวลาพักนี้ร่วมกันระหว่างแอปที่เลือกคั่น";
        body.addView(label(description, 15, MUTED, false));
        spacer(body, 18, 1);
        int limit = forFocus ? 240 : 120;
        body.addView(label("กำหนดเวลาเอง (นาที)", 15, MUTED, false));
        EditText amount = new EditText(ctx);
        amount.setSingleLine(true);
        amount.setInputType(InputType.TYPE_CLASS_NUMBER);
        amount.setFilters(new InputFilter[]{new InputFilter.LengthFilter(3)});
        amount.setSelectAllOnFocus(true);
        amount.setContentDescription("ระยะเวลาเป็นนาที");
        amount.setGravity(Gravity.CENTER);
        amount.setTextSize(40);
        amount.setTextColor(MIDNIGHT);
        amount.setText(String.valueOf(Math.max(1, Math.min(initial, limit))));
        GradientDrawable surface = new GradientDrawable();
        surface.setCornerRadius(dp(18));
        surface.setColor(0xffe5edff);
        amount.setBackground(surface);
        spacer(body, 7, 0);
        body.addView(amount, new LinearLayout.LayoutParams(dp(150), dp(76)));
        spacer(body, 15, 0);
        LinearLayout choices = new LinearLayout(ctx);
        body.addView(choices, new LinearLayout.LayoutParams(-1, -2));
        int[] presets = forFocus ? new int[]{5,15,25,50} : new int[]{5,10,15,30};
        Button[] chips = new Button[presets.length];
        for (int i = 0; i < presets.length; i++) {
            int value = presets[i];
            Button chip = button(String.valueOf(value), () -> {
                amount.setText(String.valueOf(value));
                amount.setSelection(amount.length());
                body.requestFocus();
                hideKeyboard(amount);
            });
            chips[i] = chip;
            chip.setContentDescription(value + " นาที");
            LinearLayout.LayoutParams cell = new LinearLayout.LayoutParams(0, dp(48), 1);
            if (i > 0) cell.leftMargin = dp(6);
            choices.addView(chip, cell);
        }
        spacer(body, 8, 0);
        TextView hint = label("เลือกได้ 1–" + limit + " นาที", 14, MUTED, false);
        body.addView(hint);
        spacer(body, 20, 1);
        Button confirm = addButton(body, forFocus ? "เริ่มโฟกัส" : "เริ่มพักดู", true, () -> {
            int minutes = readMinutes(amount);
            if (minutes < 1 || minutes > limit) return;
            hideKeyboard(amount);
            commit.accept(minutes);
        });
        spacer(body, 8, 0);
        addButton(body, "ย้อนกลับ", false, () -> dismissDuration());
        Runnable sync = () -> {
            int value = readMinutes(amount);
            boolean valid = value >= 1 && value <= limit;
            confirm.setEnabled(valid);
            confirm.setAlpha(valid ? 1f : .5f);
            confirm.setText((forFocus ? "เริ่มโฟกัส" : "เริ่มพักดู") + (valid ? " " + value + " นาที" : ""));
            for (int i = 0; i < chips.length; i++) {
                boolean checked = value == presets[i];
                chips[i].setSelected(checked);
                styleButton(chips[i], checked);
            }
        };
        amount.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            public void onTextChanged(CharSequence s, int start, int before, int count) { sync.run(); }
            public void afterTextChanged(Editable s) {}
        });
        body.requestFocus();
        sync.run();
        title.sendAccessibilityEvent(android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED);
    }

    private int readMinutes(EditText input) {
        try { return Integer.parseInt(input.getText().toString()); }
        catch (NumberFormatException invalid) { return 0; }
    }

    private void hideKeyboard(View anchor) {
        InputMethodManager keyboard = getContext().getSystemService(InputMethodManager.class);
        if (keyboard != null) keyboard.hideSoftInputFromWindow(anchor.getWindowToken(), 0);
    }

    boolean dismissDuration() {
        if (durationPane == null) return false;
        hideKeyboard(durationPane);
        removeView(durationPane);
        durationPane = null;
        getChildAt(0).setVisibility(VISIBLE);
        if (durationTrigger != null) durationTrigger.requestFocus();
        durationTrigger = null;
        return true;
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

    private Button addButton(LinearLayout column, String title, boolean primary, Runnable action) {
        Button button = button(title, action);
        styleButton(button, primary);
        column.addView(button, new LinearLayout.LayoutParams(-1, -2));
        return button;
    }

    private void styleButton(Button button, boolean primary) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(primary ? BLUE : Color.TRANSPARENT);
        shape.setCornerRadius(dp(16));
        if (!primary) shape.setStroke(dp(1), 0xff62749d);
        button.setTextColor(primary ? MIDNIGHT : PAPER);
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x445975a0), shape, null));
    }

    private int dp(float value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
