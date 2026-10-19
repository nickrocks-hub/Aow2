package com.nickrocks.aow2bot.ai;

import java.util.Locale;

/**
 * Learns which playing style wins, across battles (UCB1 bandit).
 * Each battle it picks the profile with the best "win rate + curiosity bonus"; untried profiles go first.
 * Results are kept on the phone through the {@link Store} the app passes in.
 */
public final class Learner {
    public interface Store {
        int get(String key);
        void put(String key, int value);
    }

    private final Store store;

    public Learner(Store store) { this.store = store; }

    public Profile choose() {
        int total = 0;
        for (Profile p : Profile.ALL) total += plays(p);
        Profile best = Profile.ALL[0];
        double bestScore = -1;
        for (Profile p : Profile.ALL) {
            int n = plays(p);
            if (n == 0) return p;
            double score = (wins(p) + 0.5 * draws(p)) / (double) n + Math.sqrt(2 * Math.log(Math.max(1, total)) / n);
            if (score > bestScore) { bestScore = score; best = p; }
        }
        return best;
    }

    public void record(Profile p, Brain.Outcome o) {
        store.put("plays_" + p.name, plays(p) + 1);
        if (o == Brain.Outcome.WIN) store.put("wins_" + p.name, wins(p) + 1);
        if (o == Brain.Outcome.UNKNOWN) store.put("draws_" + p.name, draws(p) + 1);
    }

    public int plays(Profile p) { return store.get("plays_" + p.name); }
    public int wins(Profile p) { return store.get("wins_" + p.name); }
    int draws(Profile p) { return store.get("draws_" + p.name); }

    public static Profile byName(String name) {
        for (Profile p : Profile.ALL) if (p.name.equals(name)) return p;
        return null;
    }

    public String table() {
        StringBuilder sb = new StringBuilder();
        for (Profile p : Profile.ALL) {
            int n = plays(p), w = wins(p);
            sb.append(String.format(Locale.US, "%-9s %d wins / %d battles%s%n", p.name, w, n,
                    n == 0 ? "" : String.format(Locale.US, " (%d%%)", Math.round(100f * w / n))));
        }
        return sb.toString().trim();
    }
}
