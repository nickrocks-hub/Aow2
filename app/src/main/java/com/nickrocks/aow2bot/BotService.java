package com.nickrocks.aow2bot;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.nickrocks.aow2bot.ai.Brain;
import com.nickrocks.aow2bot.ai.Commentator;
import com.nickrocks.aow2bot.ai.Learner;
import com.nickrocks.aow2bot.ai.Profile;
import com.nickrocks.aow2bot.vision.Frame;
import com.nickrocks.aow2bot.vision.GameState;
import com.nickrocks.aow2bot.vision.Hud;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Accessibility service: the floating panel and the play loop.
 *
 * Safety rules:
 *  - Taps only Age of War 2's own battle controls (unit bar, spell/evolve icons, hammer, upgrade
 *    button and the menus they open, the tower spot, "Ready!").
 *  - Plays only while the app that was in front when you pressed Start is still in front.
 *  - Does nothing on screens that aren't a battle (menus, results, ads, settings).
 *  - ⏸ Pause holds everything; ■ STOP ends capture and turns the service off.
 */
public class BotService extends AccessibilityService {
    private static final String TAG = "AoW2Bot.Bot";
    private static final long LOOP_MS = 120;
    private static final long BATTLE_GONE_MS = 5000;     // battle screen gone this long = battle over

    private static volatile BotService instance;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private WindowManager wm;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelParams;
    private TextView status;
    private Button startButton;
    private boolean stopped = false;
    private String foregroundApp = "?";

    // Play state (main thread)
    private boolean running = false, paused = false;
    private String gamePkg;
    private Recorder rec;
    private int readId = 0;
    private long lastStateLogAt = 0, lastSnapAt = 0;
    private int wins = 0, losses = 0, taps = 0;
    private volatile String line1 = "", line2 = "";

    // Worker-thread state
    private Brain brain;
    private Learner learner;
    private String profileMode = "Auto";
    private final Commentator commentator = new Commentator();
    private volatile boolean voiceOn = true;
    private Voice voice;

    public static boolean isRunning() { return instance != null; }

    public static void stopEverything() {
        BotService s = instance;
        if (s != null) s.stopAll();
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        stopped = false;
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        voice = new Voice(this);
        showPanel();
        handler.post(statusTick);
    }

    private final Runnable statusTick = new Runnable() {
        @Override
        public void run() {
            if (status != null) {
                String head;
                if (running) head = "⚔ " + (paused ? "PAUSED" : "PLAYING") + "  ·  W " + wins + " / L " + losses + "  ·  " + taps + " taps";
                else head = "⚔ AoW2 Bot  ·  capture " + (CaptureService.isRunning() ? "ON" : "off");
                status.setText(head + (line1.isEmpty() ? "" : "\n" + line1) + (line2.isEmpty() ? "" : "\n" + line2));
            }
            if (!stopped) handler.postDelayed(this, 400);
        }
    };

    // ---------- Panel ----------

    private void showPanel() {
        panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(5);
        panel.setPadding(pad, pad, pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setColors(new int[]{0xE61C1F26, 0xE6111317});
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0xCCFFA726);
        panel.setBackground(bg);

        status = new TextView(this);
        status.setTextColor(Color.WHITE);
        status.setTextSize(10);
        status.setMaxWidth(dp(330));
        panel.addView(status);

        LinearLayout controls = new LinearLayout(this);
        startButton = makeButton("▶ Start", 0xFF43A047, v -> startOrResume());
        controls.addView(startButton);
        controls.addView(makeButton("⏸", 0xFF4A4F59, v -> pause("Paused by you")));
        controls.addView(makeButton("■ STOP", 0xFFE53935, v -> stopAll()));
        controls.addView(makeButton("📷", 0xFF4A4F59, v -> snap()));
        panel.addView(controls);

        panelParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        // Top middle, over the sky/volcano: the bot never reads or taps there.
        panelParams.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        panelParams.y = dp(2);

        status.setOnTouchListener(new View.OnTouchListener() {
            float downX, downY; int startX, startY;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = e.getRawX(); downY = e.getRawY();
                        startX = panelParams.x; startY = panelParams.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        panelParams.x = startX + (int) (e.getRawX() - downX);
                        panelParams.y = startY + (int) (e.getRawY() - downY);
                        if (panel != null) wm.updateViewLayout(panel, panelParams);
                        return true;
                }
                return false;
            }
        });
        wm.addView(panel, panelParams);
        updateButtons();
    }

    private void updateButtons() {
        if (startButton == null) return;
        startButton.setText(running ? (paused ? "▶ Resume" : "▶ Playing") : "▶ Start");
    }

    private Button makeButton(String label, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextColor(Color.WHITE);
        b.setTextSize(11);
        b.setAllCaps(false);
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(8));
        b.setBackground(d);
        b.setMinWidth(0); b.setMinimumWidth(0); b.setMinHeight(0); b.setMinimumHeight(0);
        b.setPadding(dp(9), dp(4), dp(9), dp(4));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(3), dp(3), dp(3), 0);
        b.setLayoutParams(lp);
        b.setOnClickListener(l);
        return b;
    }

    // ---------- Foreground app ----------

    private String currentPackage() {
        try {
            AccessibilityNodeInfo root = getRootInActiveWindow();
            if (root != null && root.getPackageName() != null) {
                String p = root.getPackageName().toString();
                if (!p.equals(getPackageName())) return p;
            }
        } catch (Exception ignored) { }
        return foregroundApp;
    }

    // ---------- Play ----------

    private void startOrResume() {
        if (running) { if (paused) resume(); return; }
        if (!CaptureService.isRunning()) { toast("Start screen capture in the AoW2 Bot app first."); return; }
        String pkg = currentPackage();
        if (pkg == null || pkg.equals("?") || pkg.equals(getPackageName())) {
            toast("Open Age of War 2 first, then press Start.");
            return;
        }
        gamePkg = pkg;
        SharedPreferences p = Prefs.get(this);
        profileMode = p.getString(Prefs.KEY_PROFILE, "Auto");
        final boolean useSpell = p.getBoolean(Prefs.KEY_SPELL, true), buy = p.getBoolean(Prefs.KEY_BUY, true);
        voiceOn = p.getBoolean(Prefs.KEY_VOICE, true);
        rec = new Recorder(this);
        running = true; paused = false; taps = 0;
        line1 = "Starting… log: " + rec.where(); line2 = "";
        worker.execute(() -> {
            learner = Prefs.learner(this);
            newBrain(useSpell, buy);
        });
        rec.event("\"event\":\"start\",\"app\":" + Recorder.q(gamePkg) + ",\"version\":\"" + BuildConfig.VERSION_NAME
                + "\",\"mode\":" + Recorder.q(profileMode) + ",\"spell\":" + useSpell + ",\"upgrades\":" + buy);
        updateButtons();
        handler.postDelayed(this::step, 300);
    }

    /** Worker thread: a fresh brain for the next battle, with the profile the learner (or you) picked. */
    private void newBrain(boolean useSpell, boolean buy) {
        Profile prof = "Auto".equals(profileMode) ? learner.choose() : Learner.byName(profileMode);
        if (prof == null) prof = Profile.BALANCED;
        brain = new Brain(prof);
        brain.useSpell = useSpell;
        brain.buyUpgrades = buy;
    }

    private void pause(String why) {
        if (!running || paused) return;
        paused = true;
        line1 = why;
        if (rec != null) rec.event("\"event\":\"pause\",\"why\":" + Recorder.q(why));
        updateButtons();
        toast(why);
    }

    private void resume() {
        String pkg = currentPackage();
        if (gamePkg != null && !gamePkg.equals(pkg)) { toast("Open Age of War 2 first, then Resume."); return; }
        paused = false;
        line1 = "Resuming…";
        if (rec != null) rec.event("\"event\":\"resume\"");
        updateButtons();
        handler.postDelayed(this::step, 200);
    }

    private void end(String why) {
        if (!running) return;
        // Stopped during or right after a battle: still count a result we can see.
        worker.execute(() -> {
            if (brain == null || !brain.inGame()) return;
            Decision d = new Decision();
            finishBattle(d);
            Recorder r = rec;
            handler.post(() -> {
                if (r != null) for (String e : d.events) r.json(e);
                if (d.outcome == Brain.Outcome.WIN) wins++;
                else if (d.outcome == Brain.Outcome.LOSS) losses++;
                if (d.outcome != Brain.Outcome.UNKNOWN) toast("Battle result saved: " + d.outcome);
                if (voiceOn && voice != null && d.outcome != Brain.Outcome.UNKNOWN)
                    voice.speak(commentator.react(null, d.events, d.outcome, System.currentTimeMillis()), true);
            });
        });
        if (rec != null) rec.event("\"event\":\"end\",\"why\":" + Recorder.q(why) + ",\"wins\":" + wins + ",\"losses\":" + losses);
        running = false; paused = false;
        line1 = "Stopped: " + why; line2 = rec != null ? "Log: " + rec.where() : "";
        updateButtons();
    }

    /** One round: grab a frame, read it and decide on the worker, then tap on the main thread. */
    private void step() {
        if (!running || paused || stopped) return;
        if (!CaptureService.isRunning()) { end("screen capture stopped"); return; }
        String pkg = currentPackage();
        if (gamePkg != null && !gamePkg.equals(pkg)) { pause("Age of War 2 is not in front (" + pkg + ")"); return; }

        final int id = ++readId;
        final boolean[] answered = {false};
        CaptureService.requestFrame(frame -> worker.execute(() -> {
            Decision d = new Decision();
            try {
                think(frame, d);
            } catch (Exception e) {
                Log.e(TAG, "think failed", e);
                d.error = e.toString();
            }
            handler.post(() -> {
                if (id != readId || answered[0]) return;
                answered[0] = true;
                act(frame, d);
            });
        }));
        handler.postDelayed(() -> {
            if (id == readId && !answered[0] && running && !paused) {
                answered[0] = true;
                CaptureService.requestFrame(null);
                line1 = "No new frame (screen still?)";
                handler.postDelayed(this::step, LOOP_MS);
            }
        }, 1500);
    }

    /** What the worker hands back to the main thread. */
    private static final class Decision {
        GameState state;
        Brain.Action action;
        String status = "", profile = "", error;
        final List<String> events = new ArrayList<>();
        Brain.Outcome outcome;
        boolean snap;
        String say;
    }

    private long popMissingSince = 0, lastAutoSnap = 0;

    /** Worker thread: records the result, updates learning and gets a brain for the next battle. */
    private void finishBattle(Decision d) {
        Brain.Outcome o = brain.endOfBattle();
        d.events.addAll(brain.drainEvents());
        if (o != Brain.Outcome.UNKNOWN) learner.record(brain.profile, o);
        d.outcome = o;
        d.events.add("{\"event\":\"learning\",\"table\":" + Recorder.q(learner.table()) + "}");
        newBrain(brain.useSpell, brain.buyUpgrades);
    }

    /** Worker thread only (all Brain/Learner access happens here). */
    private void think(Frame frame, Decision d) {
        if (brain == null) return;
        long now = System.currentTimeMillis();
        GameState s = Hud.read(frame);
        d.state = s;
        boolean gone = !s.inBattle() && brain.inGame() && now - brain.lastBattleAt() > BATTLE_GONE_MS;
        boolean decided = s.inBattle() && brain.decided(now);
        if (gone || decided) {
            finishBattle(d);
            if (decided) brain.waitForFreshBattle();   // the result screen may still show the HUD
        }
        if (s.inBattle() && s.pop < 0) { if (popMissingSince == 0) popMissingSince = now; }
        else popMissingSince = 0;
        if (popMissingSince > 0 && now - popMissingSince > 15000 && now - lastAutoSnap > 120000) {
            lastAutoSnap = now; d.snap = true;
            d.events.add("{\"event\":\"note\",\"text\":\"army size unreadable for 15 s, screenshot saved\"}");
        }
        d.action = brain.decide(s, now);
        for (String e : brain.drainEvents()) {
            d.events.add(e);
            if (e.contains("didn't open") || e.contains("no buy button") || e.contains("didn't take")) d.snap = true;
        }
        d.status = brain.status();
        d.profile = brain.profile.name;
        if (voiceOn) d.say = commentator.react(s, d.events, d.outcome, now);
    }

    private void act(Frame frame, Decision d) {
        if (!running || paused || stopped) return;
        long now = System.currentTimeMillis();
        for (String e : d.events) rec.json(e);
        if (d.outcome != null) {
            if (d.outcome == Brain.Outcome.WIN) wins++;
            else if (d.outcome == Brain.Outcome.LOSS) losses++;
            toast(d.outcome == Brain.Outcome.WIN ? "Battle won!" : d.outcome == Brain.Outcome.LOSS ? "Battle lost — noted, trying another style next" : "Battle over");
        }
        if (d.say != null && voice != null) {
            voice.speak(d.say, d.outcome != null);
            rec.event("\"event\":\"say\",\"text\":" + Recorder.q(d.say));
        }
        if (d.snap && now - lastSnapAt > 30000) { lastSnapAt = now; CaptureService.requestSaveFrame(); }
        GameState s = d.state;
        if (d.error != null) line1 = "Error: " + d.error;
        else line1 = "[" + d.profile + "] " + d.status;
        line2 = s == null ? "" : (s.inBattle() ? s.summary().split("\n")[0] : "Not a battle screen: " + s.screen);

        if (d.action != null) {
            tap(d.action.x * frame.sx, d.action.y * frame.sy);
            taps++;
            rec.event("\"event\":\"tap\",\"what\":" + Recorder.q(d.action.why) + "," + s.json());
        } else if (s != null && s.inBattle() && now - lastStateLogAt > 5000) {
            lastStateLogAt = now;
            rec.event("\"event\":\"state\",\"status\":" + Recorder.q(d.status) + "," + s.json());
        }
        handler.postDelayed(this::step, LOOP_MS);
    }

    // ---------- Taps ----------

    private void tap(float x, float y) {
        Path path = new Path();
        path.moveTo(x, y);
        GestureDescription g = new GestureDescription.Builder()
                .addStroke(new GestureDescription.StrokeDescription(path, 0, 50))
                .build();
        boolean accepted = dispatchGesture(g, null, handler);
        if (!accepted) Log.w(TAG, "tap rejected");
    }

    private void snap() {
        if (!CaptureService.isRunning()) { toast("Capture is off. Start it from the app first."); return; }
        CaptureService.requestSaveFrame();
    }

    // ---------- Stop ----------

    private void stopAll() {
        if (running) end("stopped by you");
        stopped = true;
        handler.removeCallbacksAndMessages(null);
        CaptureService.requestFrame(null);
        CaptureService.stop(this);
        removePanel();
        toast("AoW2 Bot stopped");
        disableSelf();
    }

    private void removePanel() {
        if (panel != null) {
            try { wm.removeView(panel); } catch (Exception ignored) { }
            panel = null;
        }
    }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_SHORT).show(); }

    private int dp(int v) { return Math.round(v * getResources().getDisplayMetrics().density); }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        if (event.getEventType() != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return;
        CharSequence pkg = event.getPackageName();
        if (pkg == null) return;
        String p = pkg.toString();
        if (p.equals(getPackageName()) || p.equals("com.android.systemui")) return;
        foregroundApp = p;
        if (running && !paused && gamePkg != null && !gamePkg.equals(p)) {
            handler.post(() -> pause("Age of War 2 is not in front (" + p + ")"));
        }
    }

    @Override public void onInterrupt() { }

    @Override
    public boolean onUnbind(Intent intent) {
        cleanup();
        return super.onUnbind(intent);
    }

    @Override
    public void onDestroy() {
        cleanup();
        worker.shutdownNow();
        super.onDestroy();
    }

    private void cleanup() {
        stopped = true;
        running = false;
        handler.removeCallbacksAndMessages(null);
        CaptureService.requestFrame(null);
        removePanel();
        if (voice != null) { voice.shutdown(); voice = null; }
        instance = null;
    }
}
