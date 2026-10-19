package com.nickrocks.aow2bot;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;
import java.util.Set;

/**
 * The commentator's voice: the phone's own text-to-speech (works offline with downloaded voices).
 * Picks the best male English (US first) voice the phone has; otherwise lowers the default voice.
 * Lines are spoken phrase by phrase with pauses and small pitch/pace changes so it sounds less robotic.
 */
final class Voice {
    private static final String TAG = "AoW2Bot.Voice";

    /** Google TTS voice ids that are male (en-US, en-GB, en-AU, en-IN). */
    private static final String[] MALE_IDS = {
            "en-us-x-iom", "en-us-x-tpd", "en-us-x-iol",
            "en-gb-x-gbd", "en-gb-x-rjs", "en-gb-x-gbb", "en-au-x-aud", "en-au-x-aub", "en-in-x-end"};

    private TextToSpeech tts;
    private boolean ready = false;
    private String voiceName = "default";
    private String pendingTest;
    private final Context ctx;
    private boolean failed = false;
    private float basePitch = 0.9f;
    private final java.util.Random rnd = new java.util.Random();

    Voice(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        tts = new TextToSpeech(this.ctx, status -> {
            if (status != TextToSpeech.SUCCESS) {
                Log.w(TAG, "TTS init failed: " + status);
                failed = true;
                toast("No text-to-speech engine found. Install \"Speech Services by Google\" from the Play Store.");
                return;
            }
            int lang = tts.setLanguage(Locale.US);
            if (lang == TextToSpeech.LANG_MISSING_DATA || lang == TextToSpeech.LANG_NOT_SUPPORTED)
                toast("English voice data missing: Settings > Text-to-speech > install English (US).");
            setup();
            ready = true;
            if (pendingTest != null) { speak(pendingTest, true); pendingTest = null; }
        });
    }

    private void setup() {
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)   // media volume: still plays when the phone is on silent
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        android.speech.tts.Voice male = findMale();
        if (male != null) {
            tts.setVoice(male);
            voiceName = male.getName();
            basePitch = 0.9f;
        } else {
            basePitch = 0.78f;      // no male voice installed: deepen the default one
            voiceName = "default (deepened)";
        }
        Log.i(TAG, "voice: " + voiceName);
    }

    private android.speech.tts.Voice findMale() {
        Set<android.speech.tts.Voice> voices;
        try { voices = tts.getVoices(); } catch (Exception e) { return null; }
        if (voices == null) return null;
        android.speech.tts.Voice best = null; int bestScore = -1;
        for (android.speech.tts.Voice v : voices) {
            String n = v.getName().toLowerCase(Locale.US);
            if (!n.startsWith("en")) continue;
            int score = -1;
            for (int i = 0; i < MALE_IDS.length; i++) if (n.startsWith(MALE_IDS[i])) { score = 100 - i; break; }
            if (score < 0 && (n.contains("male") && !n.contains("female"))) score = 50;
            if (score < 0) continue;
            if (!v.isNetworkConnectionRequired()) score += 200;     // stay offline when we can
            score += v.getQuality() / 5;                             // very high quality = +100
            if (n.startsWith("en-us")) score += 30;                  // American voices suit a cowboy
            if (v.getFeatures() != null && v.getFeatures().contains("notInstalled")) score -= 300;
            if (score > bestScore) { bestScore = score; best = v; }
        }
        return best;
    }

    String voiceName() { return voiceName; }

    boolean failed() { return failed; }

    private void toast(String m) {
        new android.os.Handler(android.os.Looper.getMainLooper()).post(() ->
                android.widget.Toast.makeText(ctx, m, android.widget.Toast.LENGTH_LONG).show());
    }

    /**
     * Speaks a line like a person would: phrase by phrase, with small pauses, a slow drawl,
     * a little lift on exclamations and a slower, lower "..." beat.
     * interrupt=true cuts off whatever is playing; otherwise, if busy, the line is skipped.
     */
    void speak(String text, boolean interrupt) {
        if (text == null || tts == null || failed) return;
        if (!ready) { if (interrupt) pendingTest = text; return; }
        if (!interrupt && tts.isSpeaking()) return;
        if (interrupt) tts.stop();
        java.util.List<String> parts = phrases(text);
        for (int i = 0; i < parts.size(); i++) {
            String p = parts.get(i).trim();
            if (p.isEmpty()) continue;
            boolean excited = p.endsWith("!");
            boolean trailing = p.endsWith("...") || p.endsWith("\u2026");
            float jitter = 1f + (rnd.nextFloat() - 0.5f) * 0.06f;
            tts.setPitch(basePitch * jitter * (excited ? 1.07f : trailing ? 0.95f : 1f));
            tts.setSpeechRate((excited ? 1.0f : trailing ? 0.82f : 0.9f) * (1f + (rnd.nextFloat() - 0.5f) * 0.05f));
            tts.speak(p.replace("...", ","), TextToSpeech.QUEUE_ADD, null, "c" + System.nanoTime());
            long pause = trailing ? 420 : (p.endsWith(".") || p.endsWith("!") || p.endsWith("?")) ? 220 : 90;
            if (i < parts.size() - 1) tts.playSilentUtterance(pause, TextToSpeech.QUEUE_ADD, "s" + System.nanoTime());
        }
    }

    /** Splits after . ! ? ... and commas, keeping the punctuation. */
    static java.util.List<String> phrases(String text) {
        java.util.List<String> out = new java.util.ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            cur.append(c);
            boolean end = c == '!' || c == '?' || (c == '.' && !(i + 1 < text.length() && text.charAt(i + 1) == '.'));
            boolean comma = c == ',' && cur.length() > 18;
            if ((end || comma) && (i + 1 >= text.length() || text.charAt(i + 1) == ' ')) {
                out.add(cur.toString());
                cur.setLength(0);
            }
        }
        if (cur.length() > 0) out.add(cur.toString());
        return out;
    }

    void shutdown() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }
}
