package com.nickrocks.aow2bot;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.nickrocks.aow2bot.ai.Learner;
import com.nickrocks.aow2bot.ai.Profile;

import java.util.ArrayList;
import java.util.List;

/** Setup screen: a dark, game-styled page built in code (no layout files or libraries). */
public class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 1;
    private static final int REQ_NOTIF = 2;

    // Palette
    static final int BG = 0xFF111317, CARD = 0xFF1C1F26, CARD_HI = 0xFF262A33, LINE = 0xFF2E333D;
    static final int TEXT = 0xFFF2F2F2, MUTED = 0xFF9AA0AA, ACCENT = 0xFFFFA726, ACCENT_DARK = 0xFFE65100;
    static final int GOOD = 0xFF43A047, BAD = 0xFFE53935;

    private TextView accPill, capPill;
    private Voice voice;
    private LinearLayout learnRows;
    private final List<View> styleCards = new ArrayList<>();
    private final List<String> styleNames = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(ACCENT_DARK);
        getWindow().setNavigationBarColor(BG);
        SharedPreferences prefs = Prefs.get(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        root.addView(hero());

        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(4), dp(16), dp(24));
        root.addView(body);

        // ---- Setup ----
        LinearLayout setup = card(body, "Get ready", "Two switches, then start from the game.");
        accPill = pill();
        capPill = pill();
        setup.addView(stepRow("1", "Tap service", "Turn on \"AoW2 Bot\" in Accessibility. A small panel appears at the top.", accPill,
                "Open settings", v -> startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))));
        setup.addView(divider());
        setup.addView(stepRow("2", "Screen capture", "Lets the bot see the game. Choose \"Entire screen\".", capPill,
                "Start", v -> requestCapture()));
        setup.addView(divider());
        setup.addView(stepRow("3", "Play", "Open Age of War 2, pick a difficulty, press ▶ Start on the panel.", null, null, null));

        // ---- Strategy ----
        LinearLayout strat = card(body, "Strategy", "How the AI spends its gold.");
        String current = prefs.getString(Prefs.KEY_PROFILE, "Auto");
        addStyle(strat, prefs, current, "Auto", "🧠", "Auto-learn",
                "Tries every style, then favours the one that wins most against your difficulty.");
        addStyle(strat, prefs, current, Profile.BALANCED.name, "⚖️", "Balanced",
                "Keeps 6+ units, then towers, income and upgrades.");
        addStyle(strat, prefs, current, Profile.TURTLE.name, "🛡️", "Turtle",
                "Towers and turret upgrades first, ranged-heavy army.");
        addStyle(strat, prefs, current, Profile.RUSH.name, "⚔️", "Rush",
                "Biggest army possible, only a few upgrades.");

        LinearLayout opts = card(body, "Options", null);
        opts.addView(toggle("Use the meteor spell", "When the base is under pressure or a big fight is on",
                prefs.getBoolean(Prefs.KEY_SPELL, true), on -> prefs.edit().putBoolean(Prefs.KEY_SPELL, on).apply()));
        opts.addView(divider());
        opts.addView(toggle("\uD83C\uDF99\uFE0F Funny AI commentary", "A male cowboy announcer reacts to the battle (works offline)",
                prefs.getBoolean(Prefs.KEY_VOICE, true), on -> prefs.edit().putBoolean(Prefs.KEY_VOICE, on).apply()));
        TextView test = linkButton("\u25B6  Hear the commentator");
        test.setPadding(0, 0, 0, dp(8));
        test.setOnClickListener(v -> {
            if (voice == null) voice = new Voice(this);
            voice.speak(com.nickrocks.aow2bot.ai.Commentator.sample(), true);
            v.postDelayed(() -> { if (voice != null && !voice.failed()) toast("Voice: " + voice.voiceName() + "  (turn up media volume)"); }, 1500);
        });
        opts.addView(test);
        opts.addView(divider());
        opts.addView(toggle("Buy towers and upgrades", "Off = units, spell and evolving only",
                prefs.getBoolean(Prefs.KEY_BUY, true), on -> prefs.edit().putBoolean(Prefs.KEY_BUY, on).apply()));

        // ---- Learning ----
        LinearLayout learn = card(body, "Battle record", "Wins and losses per style. Auto-learn uses this.");
        learnRows = new LinearLayout(this);
        learnRows.setOrientation(LinearLayout.VERTICAL);
        learn.addView(learnRows);
        TextView reset = linkButton("Reset record");
        reset.setOnClickListener(v -> { Prefs.resetLearning(this); refresh(); toast("Record cleared"); });
        learn.addView(reset);

        // ---- Stop + footer ----
        TextView stop = bigButton("■  Stop everything", BAD);
        stop.setOnClickListener(v -> { CaptureService.stop(this); BotService.stopEverything(); refresh(); });
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        sl.topMargin = dp(16);
        body.addView(stop, sl);

        TextView foot = text("Logs: Download/AoW2Bot  ·  Screenshots: Pictures/AoW2Bot\nVersion " + BuildConfig.VERSION_NAME
                + "  ·  Everything stays on this phone", 12, MUTED, false);
        foot.setGravity(Gravity.CENTER);
        foot.setPadding(0, dp(16), 0, 0);
        body.addView(foot);

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        sv.setFillViewport(true);
        sv.addView(root);
        setContentView(sv);

        if (Build.VERSION.SDK_INT >= 33) requestPermissions(new String[]{"android.permission.POST_NOTIFICATIONS"}, REQ_NOTIF);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
    }

    private void refresh() {
        setPill(accPill, BotService.isRunning());
        setPill(capPill, CaptureService.isRunning());
        learnRows.removeAllViews();
        Learner l = Prefs.learner(this);
        for (Profile p : Profile.ALL) learnRows.addView(recordRow(p.name, l.wins(p), l.plays(p)));
    }

    // ---------- Pieces ----------

    private View hero() {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(20), dp(28), dp(20), dp(28));
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{ACCENT_DARK, 0xFFB71C1C, 0xFF3E1A12});
        g.setCornerRadii(new float[]{0, 0, 0, 0, dp(28), dp(28), dp(28), dp(28)});
        h.setBackground(g);

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        h.addView(icon, new LinearLayout.LayoutParams(dp(64), dp(64)));

        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(16), 0, 0, 0);
        TextView title = text("AoW2 Bot", 28, Color.WHITE, true);
        title.setTypeface(Typeface.create("sans-serif-black", Typeface.NORMAL));
        t.addView(title);
        t.addView(text("A built-in AI that plays Age of War 2 for you", 14, 0xFFFFE0B2, false));
        h.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(8);
        h.setLayoutParams(lp);
        return h;
    }

    private LinearLayout card(LinearLayout parent, String title, String sub) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        c.setBackground(rounded(CARD, dp(18), LINE));
        TextView t = text(title, 18, TEXT, true);
        c.addView(t);
        if (sub != null) {
            TextView s = text(sub, 13, MUTED, false);
            s.setPadding(0, dp(2), 0, dp(8));
            c.addView(s);
        } else {
            t.setPadding(0, 0, 0, dp(6));
        }
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(12);
        parent.addView(c, lp);
        return c;
    }

    private View stepRow(String num, String title, String desc, TextView pill, String action, View.OnClickListener l) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));

        TextView n = text(num, 15, Color.BLACK, true);
        n.setGravity(Gravity.CENTER);
        n.setBackground(rounded(ACCENT, dp(14), 0));
        row.addView(n, new LinearLayout.LayoutParams(dp(28), dp(28)));

        LinearLayout mid = new LinearLayout(this);
        mid.setOrientation(LinearLayout.VERTICAL);
        mid.setPadding(dp(12), 0, dp(8), 0);
        LinearLayout titleLine = new LinearLayout(this);
        titleLine.setGravity(Gravity.CENTER_VERTICAL);
        titleLine.addView(text(title, 15, TEXT, true));
        if (pill != null) {
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            pl.leftMargin = dp(8);
            titleLine.addView(pill, pl);
        }
        mid.addView(titleLine);
        mid.addView(text(desc, 13, MUTED, false));
        row.addView(mid, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (action != null) {
            TextView b = text(action, 14, Color.BLACK, true);
            b.setGravity(Gravity.CENTER);
            b.setPadding(dp(14), dp(8), dp(14), dp(8));
            b.setBackground(rounded(ACCENT, dp(20), 0));
            b.setOnClickListener(l);
            row.addView(b);
        }
        return row;
    }

    private TextView pill() {
        TextView p = text("", 11, Color.WHITE, true);
        p.setPadding(dp(8), dp(2), dp(8), dp(2));
        return p;
    }

    private void setPill(TextView p, boolean on) {
        p.setText(on ? "ON" : "OFF");
        p.setBackground(rounded(on ? GOOD : 0xFF4A4F59, dp(10), 0));
    }

    private void addStyle(LinearLayout parent, SharedPreferences prefs, String current,
                          String name, String emoji, String title, String desc) {
        LinearLayout c = new LinearLayout(this);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setPadding(dp(12), dp(12), dp(12), dp(12));
        TextView e = text(emoji, 24, TEXT, false);
        c.addView(e);
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setPadding(dp(12), 0, 0, 0);
        t.addView(text(title, 15, TEXT, true));
        t.addView(text(desc, 13, MUTED, false));
        c.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        c.setOnClickListener(v -> {
            prefs.edit().putString(Prefs.KEY_PROFILE, name).apply();
            highlightStyles(name);
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(6);
        parent.addView(c, lp);
        styleCards.add(c);
        styleNames.add(name);
        highlightStyles(current);
    }

    private void highlightStyles(String selected) {
        for (int i = 0; i < styleCards.size(); i++) {
            boolean on = styleNames.get(i).equals(selected);
            styleCards.get(i).setBackground(on ? rounded(0xFF3A2A16, dp(14), ACCENT) : rounded(CARD_HI, dp(14), 0));
        }
    }

    interface OnToggle { void set(boolean on); }

    private View toggle(String title, String desc, boolean on, OnToggle cb) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(text(title, 15, TEXT, true));
        t.addView(text(desc, 13, MUTED, false));
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        Switch sw = new Switch(this);
        sw.setChecked(on);
        int[][] states = {{android.R.attr.state_checked}, {}};
        sw.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, 0xFFBDBDBD}));
        sw.setTrackTintList(new ColorStateList(states, new int[]{0x88FFA726, 0xFF4A4F59}));
        sw.setOnCheckedChangeListener((b, v) -> cb.set(v));
        row.addView(sw);
        row.setOnClickListener(v -> sw.toggle());
        return row;
    }

    private View recordRow(String name, int wins, int plays) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, dp(6), 0, dp(6));
        LinearLayout top = new LinearLayout(this);
        top.addView(text(name, 14, TEXT, true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        String rate = plays == 0 ? "not tried yet" : wins + " W  ·  " + (plays - wins) + " L  ·  " + Math.round(100f * wins / plays) + "%";
        top.addView(text(rate, 13, plays == 0 ? MUTED : TEXT, false));
        row.addView(top);

        // Win-rate bar
        FrameLayout bar = new FrameLayout(this);
        bar.setBackground(rounded(0xFF2C313A, dp(4), 0));
        View fill = new View(this);
        fill.setBackground(rounded(wins * 2 >= plays && plays > 0 ? GOOD : ACCENT, dp(4), 0));
        bar.addView(fill, new FrameLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(8));
        bl.topMargin = dp(6);
        row.addView(bar, bl);
        final float share = plays == 0 ? 0 : (float) wins / plays;
        bar.post(() -> {
            ViewGroup.LayoutParams p = fill.getLayoutParams();
            p.width = Math.max(plays > 0 ? dp(4) : 0, Math.round(bar.getWidth() * share));
            fill.setLayoutParams(p);
        });
        return row;
    }

    private View divider() {
        View d = new View(this);
        d.setBackgroundColor(LINE);
        d.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));
        return d;
    }

    private TextView linkButton(String label) {
        TextView t = text(label, 14, ACCENT, true);
        t.setPadding(0, dp(10), 0, dp(2));
        return t;
    }

    private TextView bigButton(String label, int color) {
        TextView b = text(label, 16, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setBackground(rounded(color, dp(16), 0));
        return b;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable rounded(int color, float radius, int stroke) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(radius);
        if (stroke != 0) d.setStroke(3, stroke);
        return d;
    }

    // ---------- Capture ----------

    private void requestCapture() {
        if (CaptureService.isRunning()) { toast("Screen capture is already on"); return; }
        MediaProjectionManager mpm = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        startActivityForResult(mpm.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode != RESULT_OK || data == null) { toast("Screen capture not allowed"); return; }
        Intent svc = new Intent(this, CaptureService.class)
                .setAction(CaptureService.ACTION_START)
                .putExtra(CaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(CaptureService.EXTRA_DATA, data);
        startForegroundService(svc);
        accPill.postDelayed(this::refresh, 800);
    }

    @Override
    protected void onDestroy() {
        if (voice != null) voice.shutdown();
        super.onDestroy();
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }
}
