package com.nickrocks.aow2bot;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.nickrocks.aow2bot.ai.Profile;

public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1;
    private static final int REQ_NOTIF = 2;

    private TextView statusView, learnView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SharedPreferences prefs = Prefs.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(16);
        root.setPadding(pad, pad, pad, pad);

        TextView intro = new TextView(this);
        intro.setTextSize(15);
        intro.setText("AoW2 Bot plays Age of War 2 for you.\n\n"
                + "1. Turn on \"AoW2 Bot\" in Accessibility. A small panel appears at the top of the screen.\n"
                + "2. Start screen capture and allow it (choose \"Entire screen\").\n"
                + "3. Open Age of War 2, pick a difficulty, and press ▶ Start on the panel.\n\n"
                + "The bot reads the screen (gold, army, health, XP, spell) and taps the game's own buttons: "
                + "units, spell, evolve, towers and upgrades. It only plays while Age of War 2 is in front and "
                + "does nothing on menus, result screens or ads.\n"
                + "⏸ = hold. ■ STOP = end everything. Snap = save a screenshot.\n"
                + "Logs: Download/AoW2Bot. Screenshots: Pictures/AoW2Bot.");
        root.addView(intro);

        statusView = new TextView(this);
        statusView.setTextSize(15);
        statusView.setPadding(0, dp(12), 0, dp(12));
        root.addView(statusView);

        root.addView(button("1. Open Accessibility settings", v ->
                startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        root.addView(button("2. Start screen capture", v -> requestCapture()));

        // ---- Strategy ----
        root.addView(header("Strategy"));
        RadioGroup group = new RadioGroup(this);
        String current = prefs.getString(Prefs.KEY_PROFILE, "Auto");
        String[] names = {"Auto", Profile.BALANCED.name, Profile.TURTLE.name, Profile.RUSH.name};
        String[] labels = {
                "Auto-learn (tries each style, then favours the one that wins most)",
                "Balanced: towers + upgrades once the army is 6 strong",
                "Turtle: towers and turret upgrades first, ranged-heavy army",
                "Rush: big army first, few upgrades"};
        for (int i = 0; i < names.length; i++) {
            RadioButton rb = new RadioButton(this);
            rb.setText(labels[i]);
            rb.setId(1000 + i);
            final String n = names[i];
            rb.setOnClickListener(v -> prefs.edit().putString(Prefs.KEY_PROFILE, n).apply());
            group.addView(rb);
            if (n.equals(current)) rb.setChecked(true);
        }
        root.addView(group);

        CheckBox spell = new CheckBox(this);
        spell.setText("Use the spell (meteor) when the base is under pressure");
        spell.setChecked(prefs.getBoolean(Prefs.KEY_SPELL, true));
        spell.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(Prefs.KEY_SPELL, on).apply());
        root.addView(spell);

        CheckBox buy = new CheckBox(this);
        buy.setText("Buy towers and upgrades");
        buy.setChecked(prefs.getBoolean(Prefs.KEY_BUY, true));
        buy.setOnCheckedChangeListener((b, on) -> prefs.edit().putBoolean(Prefs.KEY_BUY, on).apply());
        root.addView(buy);

        // ---- Learning ----
        root.addView(header("What the bot has learned"));
        learnView = new TextView(this);
        learnView.setTextSize(14);
        learnView.setTypeface(android.graphics.Typeface.MONOSPACE);
        root.addView(learnView);
        root.addView(button("Reset learning", v -> { Prefs.resetLearning(this); refresh(); }));

        Button stop = button("STOP everything", v -> {
            CaptureService.stop(this);
            BotService.stopEverything();
            refresh();
        });
        stop.setBackgroundColor(0xFFD93025);
        stop.setTextColor(0xFFFFFFFF);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) stop.getLayoutParams();
        lp.topMargin = dp(24);
        root.addView(stop);

        TextView ver = new TextView(this);
        ver.setText("Version " + BuildConfig.VERSION_NAME);
        ver.setPadding(0, dp(12), 0, 0);
        root.addView(ver);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        statusView.setText("Tap service: " + (BotService.isRunning() ? "ON" : "off")
                + "\nScreen capture: " + (CaptureService.isRunning() ? "ON" : "off"));
        learnView.setText(Prefs.learner(this).table());
    }

    private void requestCapture() {
        if (CaptureService.isRunning()) {
            Toast.makeText(this, "Capture already running", Toast.LENGTH_SHORT).show();
            return;
        }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) {
            Toast.makeText(this, "Screen capture not allowed", Toast.LENGTH_SHORT).show();
            return;
        }
        Intent svc = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data);
        startForegroundService(svc);
        statusView.postDelayed(this::refresh, 800);
    }

    private TextView header(String t) {
        TextView h = new TextView(this);
        h.setText(t);
        h.setTextSize(17);
        h.setTypeface(null, android.graphics.Typeface.BOLD);
        h.setPadding(0, dp(18), 0, dp(4));
        return h;
    }

    private Button button(String text, android.view.View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return b;
    }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
