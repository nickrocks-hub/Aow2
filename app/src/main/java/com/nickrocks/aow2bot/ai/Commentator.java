package com.nickrocks.aow2bot.ai;

import com.nickrocks.aow2bot.vision.GameState;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An over-excited sports caster who has watched far too much Age of War.
 * Turns what the bot sees and does into short spoken lines. No Android code: the app speaks them.
 */
public final class Commentator {
    public enum Topic { START, SPELL, EVOLVE, TOWER, UPGRADE, HURT, BASE_LOW, HIT_ENEMY, ENEMY_LOW, WIPED, RICH, BROKE, FILLER, WIN, LOSS }

    private static final Map<Topic, String[]> LINES = new EnumMap<>(Topic.class);
    static {
        LINES.put(Topic.START, new String[]{
                "Ladies and gentlemen, welcome to the Stone Age. Today's forecast: clubs, with a chance of more clubs.",
                "The robot has entered the arena. The humans are nervous. As they should be.",
                "Good evening, I'm your commentator, and I have absolutely no idea what a dinosaur costs. Let's find out!",
                "Alright, cavemen, line up. Tonight we evolve or we go extinct. No pressure.",
                "Another battle! My circuits are tingling. That might be excitement. Or a short circuit.",
                "Welcome back to Age of War, the only sport where the referee is a volcano."});
        LINES.put(Topic.SPELL, new String[]{
                "Meteor incoming! Someone call the dinosaurs, they've seen this one before.",
                "And he calls down a rock from space! Subtle. Very subtle.",
                "Special delivery from the sky! No signature required.",
                "Meteor shower! Bring an umbrella. A very, very strong umbrella.",
                "That's not weather, that's a strategy.",
                "Boom! The enemy just learned what astronomy is."});
        LINES.put(Topic.EVOLVE, new String[]{
                "We're evolving! Somebody tell Darwin, he's going to want to see this.",
                "New age unlocked! Goodbye sticks, hello slightly fancier sticks.",
                "Evolution complete. Our soldiers now have opinions. And better weapons.",
                "Look at that! We skipped like ten thousand years of history in one tap.",
                "Upgrade time! The cavemen have discovered... progress!"});
        LINES.put(Topic.TOWER, new String[]{
                "A tower! Finally, a place to sit down and throw things at people.",
                "Turret built. It doesn't move, it doesn't sleep, it just judges you.",
                "New defensive structure! Real estate prices on our side of the map just went up.",
                "Tower is up! Our base now has a bouncer."});
        LINES.put(Topic.UPGRADE, new String[]{
                "Upgrade purchased! Our soldiers have been to the gym.",
                "Spending gold on upgrades. Responsible! I'm almost proud.",
                "Ooh, an upgrade. That's the cave equivalent of new sneakers.",
                "Gold well spent. Probably. I'm a commentator, not an accountant."});
        LINES.put(Topic.HURT, new String[]{
                "Ouch! They're hitting our base. That's our house!",
                "We're taking damage! Somebody do something! Oh wait, that's me.",
                "The base is getting knocked on, and it is not a friendly visit.",
                "Hey! Hands off the cave! We just cleaned it!",
                "Incoming damage! Deploying panic... I mean, soldiers."});
        LINES.put(Topic.BASE_LOW, new String[]{
                "Base health is low! This is fine. Everything is fine. Nothing is fine.",
                "We're in trouble, folks. If you have a lucky rock, now's the time to rub it.",
                "Red alert! Our base is held together by hope and mud.",
                "Okay, okay, deep breaths. Robots don't breathe. Deep processing, then."});
        LINES.put(Topic.HIT_ENEMY, new String[]{
                "Direct hit on the enemy base! Knock knock! Who's there? Pain.",
                "We're chipping away at their base like a toddler with a cookie.",
                "Their base is taking damage! Somebody's insurance premium is going up.",
                "Yes! Hit them right in the architecture!"});
        LINES.put(Topic.ENEMY_LOW, new String[]{
                "The enemy base is crumbling! Smell that? That's victory. Or the volcano.",
                "They're almost done! Start warming up the victory dance.",
                "Their base is barely standing. I'd feel bad, but I'm a robot."});
        LINES.put(Topic.WIPED, new String[]{
                "Our whole army is gone! Where did everybody go? Was it something I said?",
                "And the army has been... deleted. Rebuilding. Again.",
                "Total wipeout. Well, cavemen are cheap. Probably."});
        LINES.put(Topic.RICH, new String[]{
                "We're swimming in gold! Like a dragon, but with worse dental care.",
                "Look at all this money. Somebody's saving up for something big.",
                "Gold is piling up. My financial advisor says: buy more cavemen."});
        LINES.put(Topic.BROKE, new String[]{
                "We are completely broke. The army is running on vibes now.",
                "Zero gold. Time to sell the volcano.",
                "Wallet empty. Cavemen are working for exposure now."});
        LINES.put(Topic.FILLER, new String[]{
                "Fun fact: in the Stone Age, a war was just two guys and a disagreement.",
                "I want to point out that I'm doing all of this without thumbs.",
                "Is it just me, or does that volcano look like it's judging us?",
                "Somewhere, a historian is crying.",
                "The cavemen march on. Nobody told them about lunch breaks.",
                "If you're just joining us: there's fighting. Lots of fighting.",
                "Strategy update: hit them more than they hit us. Revolutionary, I know.",
                "Thanks for watching. Don't forget to like, subscribe, and bring a club."});
        LINES.put(Topic.WIN, new String[]{
                "Victory! The robot wins! Humans, you may now applaud.",
                "And that's the game! Their base is rubble, and our cavemen are dancing. Badly.",
                "We did it! Put that one in the history books. Well, the cave paintings.",
                "Winner winner, mammoth dinner!"});
        LINES.put(Topic.LOSS, new String[]{
                "And we lost. I'd like to blame the lag. There is no lag. I'm blaming it anyway.",
                "Defeat. Our base has been... respectfully demolished. I'm learning from this, I promise.",
                "Well, that didn't go to plan. Note to self: the enemy also has rocks.",
                "We lost, folks. Next time I'm trying a different strategy. And possibly a helmet."});
    }

    private static final long GAP_MS = 7000;           // between any two lines
    private static final long TOPIC_COOLDOWN_MS = 25000;

    private final Random rnd = new Random();
    private final Map<Topic, Long> lastTopic = new EnumMap<>(Topic.class);
    private final Map<Topic, Integer> lastIndex = new EnumMap<>(Topic.class);
    private long lastLineAt = 0, lastFillerAt = 0;
    private float prevMy = -1, prevEnemy = -1;
    private long myHitAt = 0, enemyHitAt = 0;
    private boolean saidBaseLow, saidEnemyLow;
    private int maxPop = 0;

    /** Starts a fresh battle (resets the "already said it" flags). */
    public void newBattle() {
        saidBaseLow = false; saidEnemyLow = false; maxPop = 0; prevMy = -1; prevEnemy = -1;
    }

    /**
     * One call per frame. Returns a line to speak now, or null.
     * Results (outcome != null) always speak, interrupting anything else.
     */
    public String react(GameState s, List<String> events, Brain.Outcome outcome, long now) {
        if (outcome == Brain.Outcome.WIN) return say(Topic.WIN, now, true);
        if (outcome == Brain.Outcome.LOSS) return say(Topic.LOSS, now, true);

        Topic t = null;
        for (String e : events) {
            if (e.contains("\"battle_start\"")) { newBattle(); t = pick(t, Topic.START); }
            else if (e.contains("\"spell\"")) t = pick(t, Topic.SPELL);
            else if (e.contains("\"age_up\"")) t = pick(t, Topic.EVOLVE);
            else if (e.contains("\"purchase\"") && e.contains("bought")) t = pick(t, e.contains("tower") ? Topic.TOWER : Topic.UPGRADE);
        }
        if (s != null && s.inBattle()) {
            if (s.myHp >= 0) {
                if (prevMy >= 0 && s.myHp < prevMy - 0.015f) myHitAt = now;
                prevMy = s.myHp;
                if (!saidBaseLow && s.myHp > 0.03f && s.myHp < 0.3f) { saidBaseLow = true; t = pick(t, Topic.BASE_LOW); }
            }
            if (s.enemyHp >= 0) {
                if (prevEnemy >= 0 && s.enemyHp < prevEnemy - 0.015f) enemyHitAt = now;
                prevEnemy = s.enemyHp;
                if (!saidEnemyLow && s.enemyHp > 0.03f && s.enemyHp < 0.3f) { saidEnemyLow = true; t = pick(t, Topic.ENEMY_LOW); }
            }
            if (now - myHitAt < 1500) t = pick(t, Topic.HURT);
            if (now - enemyHitAt < 1500) t = pick(t, Topic.HIT_ENEMY);
            if (s.pop >= 0) {
                if (s.pop == 0 && maxPop >= 5) t = pick(t, Topic.WIPED);
                maxPop = Math.max(maxPop, s.pop);
            }
            if (s.gold >= 3000) t = pick(t, Topic.RICH);
            if (s.gold >= 0 && s.gold < 20) t = pick(t, Topic.BROKE);
            if (t == null && now - lastLineAt > 25000 && now - lastFillerAt > 40000) { t = Topic.FILLER; }
        }
        if (t == null) return null;
        String line = say(t, now, false);
        if (line != null && t == Topic.FILLER) lastFillerAt = now;
        if (line != null && t == Topic.WIPED) maxPop = 0;
        return line;
    }

    /** Keeps the most important topic of the frame. */
    private static Topic pick(Topic current, Topic candidate) {
        if (current == null) return candidate;
        return rank(candidate) > rank(current) ? candidate : current;
    }

    private static int rank(Topic t) {
        switch (t) {
            case START: case BASE_LOW: case ENEMY_LOW: case WIPED: return 5;
            case EVOLVE: case SPELL: return 4;
            case TOWER: case UPGRADE: return 3;
            case HURT: case HIT_ENEMY: return 2;
            default: return 1;
        }
    }

    private String say(Topic t, long now, boolean force) {
        if (!force) {
            if (now - lastLineAt < GAP_MS) return null;
            Long last = lastTopic.get(t);
            if (last != null && now - last < TOPIC_COOLDOWN_MS && rank(t) < 5) return null;
        }
        String[] options = LINES.get(t);
        int prev = lastIndex.getOrDefault(t, -1);
        int i = rnd.nextInt(options.length);
        if (options.length > 1 && i == prev) i = (i + 1) % options.length;
        lastIndex.put(t, i);
        lastTopic.put(t, now);
        lastLineAt = now;
        return options[i];
    }

    /** A random line for the "test voice" button. */
    public static String sample() {
        String[] f = LINES.get(Topic.START);
        return f[new Random().nextInt(f.length)];
    }
}
