package com.nickrocks.aow2bot.ai;

import com.nickrocks.aow2bot.vision.GameState;
import com.nickrocks.aow2bot.vision.Hud;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The bot's decision maker. Fed one {@link GameState} per frame, it answers with at most one tap.
 * No Android code here, so it can be replayed on a computer.
 *
 * Every frame, in order:
 *  1. tap "Ready!" at the start of a battle;
 *  2. keep a multi-step purchase going (upgrade panel / tower menu) until it finishes or times out;
 *  3. evolve as soon as the XP bar reaches the marker;
 *  4. cast the spell when the base is under pressure (or on a big fight);
 *  5. under attack: spend everything on units;
 *  6. otherwise keep the army topped up, saving for the next item of the plan once the army is big enough.
 */
public final class Brain {
    public static final long TAP_GAP_MS = 260;

    public static final class Action {
        public final float x, y; public final String why;
        Action(float x, float y, String why) { this.x = x; this.y = y; this.why = why; }
        @Override public String toString() { return why + " @" + (int) x + "," + (int) y; }
    }

    public enum Outcome { WIN, LOSS, UNKNOWN }

    public final Profile profile;
    /** Switches from the app's settings. */
    public boolean useSpell = true, buyUpgrades = true;
    private String line = "";
    private final List<String> events = new ArrayList<>();

    // ---- Smoothed readings ----
    private int gold = -1, rawGoldPrev = -2;
    private int pop = -1, popMax = 10, rawPopPrev = -2;
    private final int[] cost = {-1, -1, -1, -1}, costSeen = {-1, -1, -1, -1};
    private long lastAgeUpAt = 0;
    private final ArrayDeque<long[]> hpHist = new ArrayDeque<>();   // {time, myHp*1000}
    private float lastMyHp = -1, lastEnemyHp = -1;
    private long lastBattleAt = 0, battleStartAt = 0;
    private boolean inGame = false;
    private int age = 1;

    // ---- Bookkeeping ----
    private long lastTapAt = 0, lastEvolveAt = 0, lastSpellAt = 0, lastReadyAt = 0, lastCloseAt = 0;
    private final long[] unitBlockedUntil = new long[4];
    private final float[] unitBought = new float[4];
    private final Map<String, Integer> bought = new HashMap<>();
    private final Map<String, Long> skipUntil = new HashMap<>();
    private final Map<String, Integer> knownPrice = new HashMap<>();
    private int pendingUnit = -1, pendingGold = -1, pendingPop = -1; private long pendingAt = 0;
    private int unitsBought = 0, purchases = 0, spells = 0, evolves = 0;

    // ---- Multi-step purchase ----
    private enum Step { OPEN, TAB, BUY, VERIFY, CLOSE }
    private Profile.Purchase macro;
    private Step step;
    private long stepAt, macroStartAt;
    private int macroGold, macroPrice;
    private int tries;
    private boolean slotTapped;

    public Brain(Profile profile) { this.profile = profile; }

    public String status() { return line; }
    public int age() { return age; }

    /** Log lines produced since the last call (purchases, failures, battle start/end). */
    public List<String> drainEvents() { List<String> e = new ArrayList<>(events); events.clear(); return e; }

    public String stats() {
        return String.format(Locale.US, "units %d, purchases %d, spells %d, evolves %d", unitsBought, purchases, spells, evolves);
    }

    // =====================================================================

    public Action decide(GameState s, long now) {
        if (!s.inBattle()) {
            if (s.readyButton && now - lastReadyAt > 1500 && now - lastTapAt > TAP_GAP_MS) {
                lastReadyAt = now; return tap(Hud.READY_X, Hud.READY_Y, "Ready!", now);
            }
            line = "Waiting for the battle screen (" + s.screen + ")";
            return null;
        }
        if (!inGame) {
            inGame = true; battleStartAt = now;
            events.add("{\"event\":\"battle_start\",\"profile\":\"" + profile.name + "\"}");
        }
        lastBattleAt = now;
        smooth(s, now);
        checkPendingUnit(now);
        if (now - lastTapAt < TAP_GAP_MS) return null;

        // 1. Ready!
        if (s.readyButton && now - lastReadyAt > 1500) { lastReadyAt = now; return tap(Hud.READY_X, Hud.READY_Y, "Ready!", now); }

        // 2. Ongoing purchase
        if (macro != null) {
            Action a = runMacro(s, now);
            if (a != null || macro != null) return a;
        }

        // Leftover menus we didn't open (or that stayed open): close them.
        if (s.panelOpen && now - lastCloseAt > 1200) { lastCloseAt = now; return tap(s.panelCloseX, s.panelCloseY, "close panel", now); }
        if (s.buildMode && now - lastCloseAt > 1200) { lastCloseAt = now; return tap(Hud.HAMMER_X, Hud.HAMMER_Y, "leave build mode", now); }
        if (s.upgradeMode && !s.panelOpen && now - lastCloseAt > 1200) { lastCloseAt = now; return tap(Hud.UPGRADE_X, Hud.UPGRADE_Y, "leave upgrade menu", now); }

        boolean underAttack = hpDropping(now);
        // Units near the base only mean danger when we have almost no army of our own there.
        boolean danger = underAttack || (pop >= 0 && pop <= 2 && s.nearBase >= 3) || (lastMyHp >= 0 && lastMyHp < 0.5f && s.nearBase >= 2);

        // 3. Evolve
        if ((s.evolveGlow || s.xp >= 0.985f) && now - lastEvolveAt > 4000) {
            lastEvolveAt = now;
            return tap(Hud.EVOLVE_X, Hud.EVOLVE_Y, "evolve / age up", now);
        }

        // 4. Spell
        if (useSpell && s.spellReady && now - lastSpellAt > 4000) {
            boolean worth = danger || (!profile.spellDefensive && s.fieldBusy >= 16) || s.fieldBusy >= 26;
            if (worth) { lastSpellAt = now; spells++; events.add(ev("spell", "busy " + s.fieldBusy + ", near " + s.nearBase)); return tap(Hud.SPELL_X, Hud.SPELL_Y, "cast spell", now); }
        }

        if (gold < 0) { line = "Reading gold…"; return null; }
        boolean roomForUnits = pop < 0 || pop < popMax;

        // 5. Danger: units first, strongest we can afford.
        if (danger && roomForUnits) {
            int u = pickUnit(s, now, gold, true);
            if (u >= 0) return buyUnit(u, now, "defend");
        }

        // 6. Plan purchase once the army is big enough.
        Profile.Purchase next = buyUpgrades ? nextPurchase(now) : null;
        int nextPrice = next == null ? 0 : priceOf(next);
        boolean armyOk = pop >= 0 && (pop >= Math.min(profile.minArmy, popMax) || !roomForUnits);
        if (next != null && !danger && armyOk && gold >= nextPrice) {
            startMacro(next, now);
            Action a = runMacro(s, now);
            if (a != null) return a;
        }

        if (roomForUnits) {
            int reserve = (next != null && armyOk && !danger) ? nextPrice : 0;
            int u = pickUnit(s, now, gold - reserve, false);
            if (u >= 0) return buyUnit(u, now, reserve > 0 ? "army (saving " + reserve + " for " + next + ")" : "army");
        }
        line = String.format(Locale.US, "Waiting · gold %d · pop %s/%d · next: %s", gold, pop < 0 ? "?" : String.valueOf(pop), popMax,
                next == null ? "nothing" : next + " (" + nextPrice + ")");
        return null;
    }

    /** Called when the battle screen has been gone for a while: decides the result. */
    public Outcome endOfBattle() {
        inGame = false;
        Outcome o = Outcome.UNKNOWN;
        if (lastEnemyHp >= 0 && lastEnemyHp <= 0.06f) o = Outcome.WIN;
        else if (lastMyHp >= 0 && lastMyHp <= 0.06f) o = Outcome.LOSS;
        events.add(String.format(Locale.US, "{\"event\":\"battle_end\",\"outcome\":\"%s\",\"hp\":%.2f,\"enemyHp\":%.2f,\"age\":%d,\"secs\":%d,\"stats\":\"%s\"}",
                o, lastMyHp, lastEnemyHp, age, (lastBattleAt - battleStartAt) / 1000, stats()));
        return o;
    }

    public boolean inGame() { return inGame; }
    public long lastBattleAt() { return lastBattleAt; }

    // =====================================================================

    private void smooth(GameState s, long now) {
        // Accept a gold value when it repeats, or when it is close to the last accepted one.
        if (s.gold >= 0) {
            if (s.gold == rawGoldPrev || (gold >= 0 && Math.abs(s.gold - gold) <= 40)) gold = s.gold;
            rawGoldPrev = s.gold;
        }
        if (s.pop >= 0) {
            if (s.pop == rawPopPrev || (pop >= 0 && Math.abs(s.pop - pop) <= 1)) { pop = s.pop; popMax = Math.max(1, s.popMax); }
            rawPopPrev = s.pop;
        }
        // Unit prices: accept a new price when it is read twice in a row. A big jump means a new age.
        for (int i = 0; i < 4; i++) if (s.unitCost[i] > 0) {
            if (s.unitCost[i] == costSeen[i] && s.unitCost[i] != cost[i]) {
                if (cost[i] > 0 && s.unitCost[i] > cost[i] * 1.8f && now - lastAgeUpAt > 5000) {
                    age++; lastAgeUpAt = now; evolves++;
                    events.add(ev("age_up", "unit " + (i + 1) + " now costs " + s.unitCost[i]));
                    resetForNewAge();
                }
                cost[i] = s.unitCost[i];
            }
            costSeen[i] = s.unitCost[i];
        }
        if (s.myHp >= 0) { lastMyHp = s.myHp; hpHist.addLast(new long[]{now, Math.round(s.myHp * 1000)}); }
        if (s.enemyHp >= 0) lastEnemyHp = s.enemyHp;
        while (!hpHist.isEmpty() && now - hpHist.peekFirst()[0] > 8000) hpHist.removeFirst();
    }

    private void resetForNewAge() {
        knownPrice.clear();
        skipUntil.clear();
    }

    private boolean hpDropping(long now) {
        if (hpHist.size() < 2) return false;
        long max = 0;
        for (long[] h : hpHist) if (now - h[0] <= 6000) max = Math.max(max, h[1]);
        return max - hpHist.peekLast()[1] >= 15;   // lost 1.5% of base HP in the last 6 s
    }

    // ---- Units ----

    private int pickUnit(GameState s, long now, int budget, boolean strongest) {
        int best = -1; float bestScore = -1e9f;
        float total = 0; for (float b : unitBought) total += b;
        float wsum = 0; for (int i = 0; i < 4; i++) if (usable(s, i, now)) wsum += profile.unitWeights[i];
        for (int i = 0; i < 4; i++) {
            if (!usable(s, i, now)) continue;
            int c = cost[i] > 0 ? cost[i] : guessCost(i);
            if (c > budget) continue;
            float score;
            if (strongest) score = c;
            else score = profile.unitWeights[i] / Math.max(1e-3f, wsum) - (total <= 0 ? 0 : unitBought[i] / total);
            if (score > bestScore) { bestScore = score; best = i; }
        }
        return best;
    }

    private boolean usable(GameState s, int i, long now) {
        return now >= unitBlockedUntil[i] && (s.unitAvailable[i] || cost[i] > 0 && i < 3);
    }

    private int guessCost(int i) { return new int[]{100, 125, 200, 400}[i] * age; }

    private Action buyUnit(int u, long now, String why) {
        pendingUnit = u; pendingGold = gold; pendingPop = pop; pendingAt = now;
        unitBought[u] += 1;
        for (int i = 0; i < 4; i++) unitBought[i] *= 0.97f;   // slowly forget old purchases
        unitsBought++;
        return tap(Hud.UNIT_X[u], Hud.UNIT_Y, "unit " + (u + 1) + " · " + why, now);
    }

    /** If a unit tap didn't take gold or population, block that unit for a few seconds. */
    private void checkPendingUnit(long now) {
        if (pendingUnit < 0) return;
        boolean ok = (gold >= 0 && pendingGold >= 0 && gold < pendingGold - 20) || (pop > pendingPop && pendingPop >= 0);
        if (ok) { pendingUnit = -1; return; }
        if (now - pendingAt > 1600) {
            unitBlockedUntil[pendingUnit] = now + 6000;
            unitBought[pendingUnit] = Math.max(0, unitBought[pendingUnit] - 1);
            unitsBought--;
            pendingUnit = -1;
        }
    }

    // ---- Plan ----

    private Profile.Purchase nextPurchase(long now) {
        for (Profile.Purchase p : profile.plan) {
            int n = bought.getOrDefault(p.key(), 0);
            if (n >= p.maxTimes) continue;
            Long until = skipUntil.get(p.key());
            if (until != null && now < until) continue;
            return p;
        }
        return null;
    }

    private int priceOf(Profile.Purchase p) {
        Integer k = knownPrice.get(p.key());
        return k != null ? k : p.guessPrice * (age >= 2 ? 3 * (age - 1) : 1);
    }

    private void startMacro(Profile.Purchase p, long now) {
        macro = p; step = Step.OPEN; stepAt = now; macroStartAt = now; macroGold = gold; macroPrice = -1; tries = 0; slotTapped = false;
    }

    private void endMacro(String result, long skipMs, long now) {
        if (macro != null) {
            events.add(ev("purchase", macro + ": " + result));
            if (skipMs > 0) skipUntil.put(macro.key(), now + skipMs);
        }
        macro = null; step = null;
    }

    private Action runMacro(GameState s, long now) {
        long inStep = now - stepAt;
        if (now - macroStartAt > 9000) {          // safety net: never get stuck in a menu
            Profile.Purchase m = macro;
            endMacro("timed out", 20000, now);
            if (s.panelOpen) return tap(s.panelCloseX, s.panelCloseY, "close (timeout " + m + ")", now);
            if (s.buildMode) return tap(Hud.HAMMER_X, Hud.HAMMER_Y, "leave build (timeout)", now);
            return null;
        }
        boolean upgrade = macro.kind == Profile.Purchase.Kind.UPGRADE;
        switch (step) {
            case OPEN: {
                boolean open = upgrade ? s.panelOpen : s.buildMode;
                if (open) { next(upgrade ? Step.TAB : Step.BUY, now); return upgrade ? tapTab(now) : null; }
                if (tries > 0 && inStep < 1500) return null;
                if (tries >= 3) { endMacro("menu didn't open", 20000, now); return closeMenus(s, now); }
                tries++; stepAt = now;
                line = "Opening " + (upgrade ? "upgrades" : "build menu") + " for " + macro;
                return upgrade ? tap(Hud.UPGRADE_X, Hud.UPGRADE_Y, "open upgrades", now) : tap(Hud.HAMMER_X, Hud.HAMMER_Y, "open build menu", now);
            }
            case TAB:
                if (inStep < 450) return null;
                next(Step.BUY, now);
                return null;
            case BUY: {
                if (inStep < 350) return null;
                GameState.Button b = chooseButton(s);
                if (b == null && !upgrade && !slotTapped && inStep > 600) {
                    // Build mode without a popup: tap the tower spot on the base (seen at this position in the recording).
                    slotTapped = true; stepAt = now;
                    return tap(Hud.SLOT_X, Hud.SLOT_Y, "tap tower spot", now);
                }
                if (b == null) {
                    if (inStep < 1500) return null;
                    // Nothing to buy here: maxed out, too expensive (grey button), or a menu we don't know yet.
                    endMacro(upgrade ? "no buy button (maxed or too expensive?)" : "no buy button in build menu", 60000, now);
                    return closeMenus(s, now);
                }
                if (b.price > 0) knownPrice.put(macro.key(), b.price);
                if (b.price < 0 && inStep < 900) return null;     // give the price a moment to read
                int price = b.price > 0 ? b.price : priceOf(macro);
                if (gold < price) {
                    endMacro("need " + price + ", have " + gold, b.price > 0 ? 0 : 15000, now);
                    return closeMenus(s, now);
                }
                macroGold = gold; macroPrice = price;
                next(Step.VERIFY, now);
                return tap(b.x, b.y, "buy " + macro + " (" + price + ")", now);
            }
            case VERIFY: {
                if (gold >= 0 && gold <= macroGold - macroPrice * 0.7f) {
                    bought.merge(macro.key(), 1, Integer::sum);
                    purchases++;
                    endMacro("bought for " + macroPrice, 0, now);
                    return closeMenus(s, now);
                }
                if (inStep > 1800) {
                    endMacro("tap didn't take gold", 30000, now);
                    return closeMenus(s, now);
                }
                return null;
            }
            default:
                endMacro("?", 0, now);
                return null;
        }
    }

    private Action tapTab(long now) {
        return tap(Hud.TAB_X[macro.tab], Hud.TAB_Y, "tab " + Hud.TAB_NAMES[macro.tab], now);
    }

    /** Picks the buy button for the current purchase: upgrade rows top to bottom; in build menus the dearest affordable one. */
    private GameState.Button chooseButton(GameState s) {
        List<GameState.Button> bs = new ArrayList<>(s.greenButtons);
        if (bs.isEmpty()) return null;
        bs.sort((a, b) -> Float.compare(a.y, b.y));
        if (macro.kind == Profile.Purchase.Kind.UPGRADE) {
            if (macro.row < bs.size()) return bs.get(macro.row);
            return null;
        }
        GameState.Button best = null;
        for (GameState.Button b : bs) {
            if (b.price > 0 && b.price <= gold && (best == null || best.price < b.price)) best = b;
        }
        if (best == null) best = bs.get(0);
        return best;
    }

    private Action closeMenus(GameState s, long now) {
        lastCloseAt = now;
        if (s.panelOpen) return tap(s.panelCloseX, s.panelCloseY, "close panel", now);
        if (s.buildMode) return tap(Hud.HAMMER_X, Hud.HAMMER_Y, "leave build mode", now);
        if (s.upgradeMode) return tap(Hud.UPGRADE_X, Hud.UPGRADE_Y, "leave upgrade menu", now);
        return null;
    }

    private void next(Step st, long now) { step = st; stepAt = now; }

    private Action tap(float x, float y, String why, long now) {
        lastTapAt = now;
        line = why;
        return new Action(x, y, why);
    }

    private static String ev(String kind, String text) {
        return "{\"event\":\"" + kind + "\",\"text\":\"" + text.replace("\"", "'") + "\"}";
    }
}
