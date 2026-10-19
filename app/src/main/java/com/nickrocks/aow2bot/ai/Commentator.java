package com.nickrocks.aow2bot.ai;

import com.nickrocks.aow2bot.vision.GameState;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * An old cowboy rodeo announcer who wandered into the Stone Age.
 * Turns what the bot sees and does into short spoken lines. No Android code: the app speaks them.
 */
public final class Commentator {
    public enum Topic { START, SPELL, EVOLVE, TOWER, UPGRADE, HURT, BASE_LOW, HIT_ENEMY, ENEMY_LOW, WIPED, RICH, BROKE, FILLER, WIN, LOSS }

    private static final Map<Topic, String[]> LINES = new EnumMap<>(Topic.class);
    static {
        LINES.put(Topic.START, new String[]{
                "Well howdy, folks! Pull up a hay bale, 'cause we got ourselves a stone age showdown.",
                "Yee-haw! The robot's saddled up, and them cavemen don't know what's comin'.",
                "Evenin', partner. Tonight's forecast... clubs. With a mighty fine chance of more clubs.",
                "Alright, y'all. Saddle up them cavemen. We're either evolvin' tonight, or we're goin' extinct.",
                "Welcome back to Age of War, the only rodeo where the referee... is a volcano.",
                "Hold onto your hats, folks. This here robot's fixin' to start a war."});
        LINES.put(Topic.SPELL, new String[]{
                "Meteor comin' in hot! Somebody tell them dinosaurs, they've seen this one before.",
                "Well, would ya look at that. He's callin' down rocks from the good Lord's own sky.",
                "Special delivery from up yonder! No signature required, partner.",
                "Yee-haw! It's rainin' rocks! Best find yourself a sturdy hat.",
                "That ain't weather, folks. That there's a strategy.",
                "Kaboom! I reckon them fellers just learned what astronomy is."});
        LINES.put(Topic.EVOLVE, new String[]{
                "We're evolvin', y'all! Somebody fetch Mister Darwin, he's gonna wanna see this.",
                "New age, partner! Goodbye sticks... hello slightly fancier sticks.",
                "Well, I'll be. Skipped ten thousand years of history with one little tap.",
                "Evolution's done. Our boys got better weapons, and I reckon better manners too.",
                "Look at that! These cavemen just discovered progress. Ain't that somethin'."});
        LINES.put(Topic.TOWER, new String[]{
                "A tower! Finally, a place to sit a spell and throw things at folks.",
                "Turret's built. Don't move, don't sleep... just sits there judgin' ya. Like my grandpappy.",
                "New lookout tower, folks. Our side of the ranch just went up in value.",
                "Tower's up! Our base done hired itself a bouncer."});
        LINES.put(Topic.UPGRADE, new String[]{
                "Upgrade bought! Our boys been liftin' hay bales, and it shows.",
                "Spendin' gold on upgrades. Now that's what I call responsible ranchin'.",
                "Ooh, an upgrade. That's the cave version of new boots, partner.",
                "Gold well spent, I reckon. Then again, I'm a commentator, not a banker."});
        LINES.put(Topic.HURT, new String[]{
                "Whoa there! They're hittin' our base. That's our homestead, y'all!",
                "We're takin' damage! Somebody do somethin'! ...Oh wait. That's me.",
                "Hey now! Get your grubby paws off our cave, we just swept it!",
                "They're knockin' on the door, folks, and it ain't the neighbors bringin' pie.",
                "Incoming! Send in the boys! Send in everybody!"});
        LINES.put(Topic.BASE_LOW, new String[]{
                "Base is hangin' on by a thread, folks. This is fine. Nothin' is fine.",
                "We're in a heap of trouble. If you got a lucky horseshoe, now's the time to rub it.",
                "Red alert, partner! Our base is held together with spit and prayer.",
                "Easy now... easy. Deep breaths. Well, I don't breathe. But you get the idea."});
        LINES.put(Topic.HIT_ENEMY, new String[]{
                "Direct hit on the enemy base! Knock knock! Who's there? Trouble, that's who.",
                "We're chippin' away at their fort like a woodpecker on a fencepost.",
                "Their base is hurtin'! Somebody's insurance is goin' up, I tell ya.",
                "Yee-haw! Right in the architecture!"});
        LINES.put(Topic.ENEMY_LOW, new String[]{
                "Their base is crumblin'! Smell that, folks? That's victory. ...Or the volcano.",
                "They're near done for! Somebody start warmin' up the fiddle.",
                "Their fort's barely standin'. I'd feel bad for 'em, but I'm a robot."});
        LINES.put(Topic.WIPED, new String[]{
                "Our whole posse's gone! Where'd everybody go? Was it somethin' I said?",
                "And the army has been... sent to the big ranch in the sky. Rebuildin'.",
                "Total wipeout, folks. Well... cavemen come cheap. I hope."});
        LINES.put(Topic.RICH, new String[]{
                "We're rollin' in gold! Like a prospector who hit the mother lode.",
                "Would ya look at all that money. Somebody's savin' up for somethin' big.",
                "Gold's pilin' up like hay in August. My advice? Buy more cavemen."});
        LINES.put(Topic.BROKE, new String[]{
                "We're flat broke, partner. This army's runnin' on beans and hope.",
                "Not a single coin left. Reckon we gotta sell the volcano.",
                "Empty pockets, folks. The boys are workin' for a handshake now."});
        LINES.put(Topic.FILLER, new String[]{
                "Fun fact, folks. In the stone age, a war was just two fellers and a disagreement.",
                "I'd like to point out I'm doin' all of this without any thumbs.",
                "Is it just me... or is that volcano lookin' at us funny?",
                "Somewhere out there, a history teacher is cryin' into his coffee.",
                "Them cavemen just keep on marchin'. Nobody told 'em about lunch breaks.",
                "If you're just tunin' in, there's fightin'. A whole lotta fightin'.",
                "Strategy update, folks. Hit 'em more than they hit us. Revolutionary, I know.",
                "Back home we'd settle this with a good old fashioned square dance. But here we are."});
        LINES.put(Topic.WIN, new String[]{
                "Yee-haw! Victory! The robot wins! Y'all may now applaud.",
                "And that's the ball game, folks! Their base is rubble, and our boys are line dancin'.",
                "We did it, partner! Put that one in the history books. Well... the cave paintin's.",
                "Winner winner, mammoth dinner! Yee-haw!"});
        LINES.put(Topic.LOSS, new String[]{
                "Well, shoot. We lost. I'd blame the lag, but there ain't no lag. Blamin' it anyway.",
                "Defeat, folks. Our base got... respectfully flattened. I'm learnin' from this, I promise.",
                "That didn't go to plan, partner. Note to self: the other fellers got rocks too.",
                "We lost, y'all. Next time we're tryin' a new strategy. And maybe a bigger hat."});
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
