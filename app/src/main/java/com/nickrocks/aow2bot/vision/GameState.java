package com.nickrocks.aow2bot.vision;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Everything the bot reads from one Age of War 2 frame. -1 / null means "could not read". */
public final class GameState {
    public enum Screen { BATTLE, NOT_LANDSCAPE, OTHER }

    public Screen screen = Screen.OTHER;
    public long time;

    public int gold = -1;
    public int pop = -1, popMax = -1;
    public float myHp = -1, enemyHp = -1;        // 0..1
    public float xp = -1;                        // 0..1, 1 = evolve threshold (cyan marker)
    public boolean spellReady, evolveGlow;
    public boolean readyButton;                  // "Ready!" at the start of a battle
    public boolean buildMode, upgradeMode;       // hammer / upgrade button switched on (blue)
    public boolean panelOpen;                    // upgrade panel (red X at its right edge)
    public float panelCloseX = -1, panelCloseY = -1;

    public final int[] unitCost = {-1, -1, -1, -1};
    public final boolean[] unitAvailable = new boolean[4];

    /** Battlefield occupancy: one entry per 40-layout-px column from x=520 to 2520 (50 columns). */
    public final float[] field = new float[Hud.FIELD_COLS];
    public int fieldBusy, nearBase;

    public final List<Button> greenButtons = new ArrayList<>();

    /** A green buy button found on screen, with its price if readable. */
    public static final class Button {
        public final float x, y, w, h; public final int price;
        public Button(float x, float y, float w, float h, int price) { this.x = x; this.y = y; this.w = w; this.h = h; this.price = price; }
        @Override public String toString() { return String.format(Locale.US, "[%d,%d %s]", (int) x, (int) y, price < 0 ? "?" : String.valueOf(price)); }
    }

    public boolean inBattle() { return screen == Screen.BATTLE; }

    public String summary() {
        if (screen != Screen.BATTLE) return "Screen: " + screen;
        StringBuilder sb = new StringBuilder();
        sb.append("Gold ").append(gold < 0 ? "?" : gold)
          .append("  Pop ").append(pop < 0 ? "?" : pop).append('/').append(popMax < 0 ? "?" : popMax)
          .append(String.format(Locale.US, "  HP %s vs %s", pct(myHp), pct(enemyHp)))
          .append("  XP ").append(pct(xp));
        if (spellReady) sb.append("  SPELL");
        if (evolveGlow) sb.append("  EVOLVE");
        if (readyButton) sb.append("  [Ready!]");
        if (buildMode) sb.append("  build");
        if (upgradeMode) sb.append("  upgrade");
        if (panelOpen) sb.append("  panel");
        sb.append("\nUnits ");
        for (int i = 0; i < 4; i++) {
            sb.append(i + 1).append(':').append(unitAvailable[i] ? (unitCost[i] < 0 ? "?" : String.valueOf(unitCost[i])) : "locked").append(' ');
        }
        sb.append(" Field ").append(fieldBusy).append(" busy, ").append(nearBase).append(" near base");
        if (!greenButtons.isEmpty()) sb.append("\nBuy buttons ").append(greenButtons);
        return sb.toString();
    }

    static String pct(float v) { return v < 0 ? "?" : Math.round(v * 100) + "%"; }

    public String json() {
        StringBuilder sb = new StringBuilder();
        sb.append("\"screen\":\"").append(screen).append('"');
        if (screen != Screen.BATTLE) return sb.toString();
        sb.append(",\"gold\":").append(gold).append(",\"pop\":").append(pop).append(",\"popMax\":").append(popMax)
          .append(String.format(Locale.US, ",\"hp\":%.2f,\"enemyHp\":%.2f,\"xp\":%.2f", myHp, enemyHp, xp))
          .append(",\"spell\":").append(spellReady).append(",\"evolveGlow\":").append(evolveGlow)
          .append(",\"build\":").append(buildMode).append(",\"upg\":").append(upgradeMode).append(",\"panel\":").append(panelOpen)
          .append(",\"costs\":[");
        for (int i = 0; i < 4; i++) sb.append(i > 0 ? "," : "").append(unitAvailable[i] ? unitCost[i] : -2);
        sb.append("],\"busy\":").append(fieldBusy).append(",\"near\":").append(nearBase);
        if (!greenButtons.isEmpty()) {
            sb.append(",\"buy\":[");
            for (int i = 0; i < greenButtons.size(); i++) {
                Button b = greenButtons.get(i);
                sb.append(i > 0 ? "," : "").append(String.format(Locale.US, "[%d,%d,%d]", (int) b.x, (int) b.y, b.price));
            }
            sb.append(']');
        }
        return sb.toString();
    }
}
