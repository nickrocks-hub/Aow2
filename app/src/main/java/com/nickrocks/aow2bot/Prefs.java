package com.nickrocks.aow2bot;

import android.content.Context;
import android.content.SharedPreferences;

import com.nickrocks.aow2bot.ai.Learner;

/** Settings and learning results, stored on the phone. */
final class Prefs {
    static final String FILE = "aow2bot";
    static final String KEY_PROFILE = "profile";        // "Auto" or a profile name
    static final String KEY_SPELL = "useSpell";
    static final String KEY_BUY = "buyUpgrades";
    static final String KEY_VOICE = "commentary";

    static SharedPreferences get(Context c) { return c.getSharedPreferences(FILE, Context.MODE_PRIVATE); }

    static Learner learner(Context c) {
        SharedPreferences p = c.getSharedPreferences("learning", Context.MODE_PRIVATE);
        return new Learner(new Learner.Store() {
            @Override public int get(String key) { return p.getInt(key, 0); }
            @Override public void put(String key, int value) { p.edit().putInt(key, value).apply(); }
        });
    }

    static void resetLearning(Context c) {
        c.getSharedPreferences("learning", Context.MODE_PRIVATE).edit().clear().apply();
    }
}
