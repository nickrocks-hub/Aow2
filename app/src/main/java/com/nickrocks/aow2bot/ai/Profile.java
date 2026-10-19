package com.nickrocks.aow2bot.ai;

/**
 * A playing style. The learner picks one per battle and keeps score of how each one does.
 * Purchases are tried in order; anything maxed out or failing is skipped for a while.
 */
public final class Profile {
    public final String name;
    /** Army size (population) to keep before spending on upgrades/turrets. */
    public final int minArmy;
    /** Relative mix of the four unit slots (melee, support, anti-armor, heavy). */
    public final float[] unitWeights;
    /** Purchase plan: {@link Purchase} items in priority order. */
    public final Purchase[] plan;
    /** Cast the spell only when the base is threatened (true) or on any big fight (false). */
    public final boolean spellDefensive;

    Profile(String name, int minArmy, float[] unitWeights, boolean spellDefensive, Purchase... plan) {
        this.name = name; this.minArmy = minArmy; this.unitWeights = unitWeights;
        this.spellDefensive = spellDefensive; this.plan = plan;
    }

    /** Something to buy outside the unit bar. tab/row refer to the upgrade panel; TOWER uses the hammer. */
    public static final class Purchase {
        public enum Kind { UPGRADE, TOWER }
        public final Kind kind; public final int tab, row; public final int guessPrice; public final int maxTimes;
        Purchase(Kind kind, int tab, int row, int guessPrice, int maxTimes) {
            this.kind = kind; this.tab = tab; this.row = row; this.guessPrice = guessPrice; this.maxTimes = maxTimes;
        }
        public String key() { return kind == Kind.TOWER ? "tower" : "upg" + tab + "." + row; }
        @Override public String toString() {
            if (kind == Kind.TOWER) return "tower/turret";
            String[] rows = {"damage", "armor/range"};
            return com.nickrocks.aow2bot.vision.Hud.TAB_NAMES[tab] + (tab >= 5 ? "" : " " + rows[row]);
        }
    }

    static Purchase upg(int tab, int row, int guess, int times) { return new Purchase(Purchase.Kind.UPGRADE, tab, row, guess, times); }
    static Purchase tower(int times) { return new Purchase(Purchase.Kind.TOWER, -1, -1, 250, times); }

    // Upgrade tabs: 0 infantry, 1 support, 2 anti-armor, 3 armor, 4 turrets, 5 income, 6 building.
    public static final Profile RUSH = new Profile("Rush", 9,
            new float[]{3, 2, 2, 2}, false,
            upg(6, 0, 400, 1), upg(5, 0, 500, 2), upg(0, 0, 300, 3), upg(0, 1, 300, 3),
            upg(1, 0, 300, 3), upg(2, 0, 400, 3), upg(3, 0, 400, 3));

    public static final Profile BALANCED = new Profile("Balanced", 6,
            new float[]{3, 3, 2, 2}, true,
            tower(1), upg(5, 0, 500, 1), upg(6, 0, 400, 1), upg(0, 1, 300, 2), upg(1, 0, 300, 2),
            tower(3), upg(4, 0, 300, 3), upg(0, 0, 300, 3), upg(2, 1, 300, 2), upg(5, 0, 500, 3),
            upg(1, 1, 300, 3), upg(2, 0, 400, 3), upg(3, 0, 400, 3), upg(3, 1, 300, 3), upg(4, 1, 300, 3));

    public static final Profile TURTLE = new Profile("Turtle", 4,
            new float[]{2, 4, 2, 2}, true,
            tower(2), upg(4, 0, 300, 2), upg(5, 0, 500, 2), upg(4, 1, 300, 2), upg(6, 0, 400, 1),
            tower(4), upg(1, 0, 300, 3), upg(1, 1, 300, 3), upg(4, 0, 300, 3), upg(0, 1, 300, 3),
            upg(0, 0, 300, 3), upg(5, 0, 500, 3), upg(2, 0, 400, 3), upg(3, 0, 400, 3));

    public static final Profile[] ALL = {BALANCED, TURTLE, RUSH};
}
