package com.nickrocks.aow2bot;

import android.content.Context;
import android.media.AudioAttributes;
import android.speech.tts.TextToSpeech;
import android.util.Log;

import java.util.Locale;
import java.util.Set;

/**
 * The commentator's voice: the phone's own text-to-speech (works offline with downloaded voices).
 * Picks a male English voice when the phone has one; otherwise lowers the pitch of the default voice.
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

    Voice(Context ctx) {
        tts = new TextToSpeech(ctx.getApplicationContext(), status -> {
            if (status != TextToSpeech.SUCCESS) { Log.w(TAG, "TTS init failed: " + status); return; }
            setup();
            ready = true;
            if (pendingTest != null) { speak(pendingTest, true); pendingTest = null; }
        });
    }

    private void setup() {
        tts.setLanguage(Locale.US);
        tts.setAudioAttributes(new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        android.speech.tts.Voice male = findMale();
        if (male != null) {
            tts.setVoice(male);
            voiceName = male.getName();
            tts.setPitch(0.92f);
        } else {
            tts.setPitch(0.72f);    // no male voice installed: deepen the default one
            voiceName = "default (deepened)";
        }
        tts.setSpeechRate(1.08f);   // casters talk fast
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
            if (v.getFeatures() != null && v.getFeatures().contains("notInstalled")) score -= 300;
            if (score > bestScore) { bestScore = score; best = v; }
        }
        return best;
    }

    String voiceName() { return voiceName; }

    /** Speaks a line. interrupt=true cuts off whatever is playing; otherwise busy = skip. */
    void speak(String text, boolean interrupt) {
        if (text == null || tts == null) return;
        if (!ready) { if (interrupt) pendingTest = text; return; }
        if (!interrupt && tts.isSpeaking()) return;
        tts.speak(text, interrupt ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD, null, "c" + System.nanoTime());
    }

    void shutdown() {
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }
}
