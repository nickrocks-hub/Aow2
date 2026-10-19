package com.nickrocks.aow2bot.vision;

/**
 * Reads the Age of War 2 battle screen. Positions are layout pixels on a 2520x1080 landscape
 * screen (measured from the recording) and are scaled to the real frame by {@link Frame}.
 */
public final class Hud {
    // ---- Tap targets (layout pixels) ----
    public static final float[] UNIT_X = {74, 206, 342, 476};
    public static final float UNIT_Y = 975;
    public static final float SPELL_X = 77, SPELL_Y = 215;
    public static final float EVOLVE_X = 272, EVOLVE_Y = 215;
    public static final float HAMMER_X = 2310, HAMMER_Y = 1004;
    public static final float UPGRADE_X = 2444, UPGRADE_Y = 1004;
    public static final float READY_X = 1258, READY_Y = 676;
    /** First tower spot on the base (shown when the hammer is on). */
    public static final float SLOT_X = 550, SLOT_Y = 780;
    /** Upgrade panel tabs, left to right: infantry, support, anti-armor, armor, turrets, income, building. */
    public static final float[] TAB_X = {814, 962, 1110, 1260, 1408, 1556, 1704};
    public static final float TAB_Y = 360;
    public static final String[] TAB_NAMES = {"infantry", "support", "anti-armor", "armor", "turrets", "income", "building"};

    public static final int FIELD_COLS = 50;
    static final float FIELD_X0 = 520, FIELD_COL_W = 40, FIELD_Y0 = 740, FIELD_Y1 = 890;

    private Hud() { }

    public static GameState read(Frame f) {
        GameState s = new GameState();
        s.time = System.currentTimeMillis();
        if (!f.isLandscape()) { s.screen = GameState.Screen.NOT_LANDSCAPE; return s; }

        boolean boxes = darkFraction(f, 456, 165, 486, 185) > 0.6f && darkFraction(f, 456, 222, 486, 244) > 0.6f;
        int hammer = f.at(2262, 962), upg = f.at(2398, 962);
        boolean buttons = (orange(hammer) || blue(hammer)) && (orange(upg) || blue(upg));
        if (!boxes || !buttons) {
            s.screen = GameState.Screen.OTHER;
            // First battle shows a dimmed tutorial overlay with "Ready!" on top of the HUD.
            s.readyButton = boxes && readyButton(f);
            return s;
        }
        s.screen = GameState.Screen.BATTLE;
        s.buildMode = blue(hammer);
        s.upgradeMode = blue(upg);

        Ocr.Result gold = Ocr.read(f, 470, 160, 572, 189, Ocr.HUD_TEXT, 15, 30, 0.2f);
        s.gold = gold.intValue();
        Ocr.Result pop = Ocr.read(f, 485, 217, 568, 250, Ocr.HUD_TEXT, 12, 34, 0.2f);
        if (pop.text != null) {
            int slash = pop.text.indexOf('/');
            if (slash > 0 && slash < pop.text.length() - 1 && pop.text.indexOf('/', slash + 1) < 0) {
                s.pop = parse(pop.text.substring(0, slash));
                s.popMax = parse(pop.text.substring(slash + 1));
                if (s.popMax <= 0 || s.pop > s.popMax) { s.pop = -1; s.popMax = -1; }
            }
        }

        s.myHp = greenFraction(f, 15, 565, 104);
        s.enemyHp = greenFraction(f, 1955, 2505, 104);
        s.xp = xpFill(f);
        s.spellReady = glow(f, new float[][]{{5, 220}, {8, 250}, {75, 150}, {146, 220}});
        s.evolveGlow = glow(f, new float[][]{{203, 220}, {206, 250}, {272, 150}, {342, 220}});

        s.readyButton = readyButton(f);
        float[] x = closeButton(f, 1830, 685);
        if (x == null) x = closeButton(f, 1830, 580);
        if (x != null) { s.panelOpen = true; s.panelCloseX = x[0]; s.panelCloseY = x[1]; }

        for (int i = 0; i < 4; i++) {
            float cx = UNIT_X[i];
            Ocr.Result c = Ocr.read(f, cx - 11, 1030, cx + 40, 1056, Ocr.WHITE, 10, 30, 0.24f, 0.8f);
            s.unitAvailable[i] = c.glyphs >= 2;
            int v = c.intValue();
            s.unitCost[i] = v >= 10 ? v : -1;
        }

        if (!s.panelOpen && !s.buildMode) readField(f, s);
        findGreenButtons(f, s, 560, 250, 2000, 950);
        return s;
    }

    static int parse(String t) {
        if (t.isEmpty() || t.length() > 4) return -1;
        int v = 0;
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c < '0' || c > '9') return -1;
            v = v * 10 + (c - '0');
        }
        return v;
    }

    // ---- Colour tests ----
    static boolean orange(int p) { int r = Frame.r(p), g = Frame.g(p), b = Frame.b(p); return r > 200 && g > 90 && g < 225 && b < 80; }
    static boolean blue(int p) { int r = Frame.r(p), b = Frame.b(p); return b > 90 && b > r + 50; }
    static boolean green(int p) { int r = Frame.r(p), g = Frame.g(p), b = Frame.b(p); return g > 130 && g > r + 40 && g > b + 30; }
    static boolean lime(int p) { int r = Frame.r(p), g = Frame.g(p), b = Frame.b(p); return g > 190 && r > 80 && r < 185 && b < 130 && g - r > 35; }
    static boolean red(int p) { int r = Frame.r(p), g = Frame.g(p), b = Frame.b(p); return r > 110 && g < 70 && b < 70; }

    static float darkFraction(Frame f, float x0, float y0, float x1, float y1) {
        int n = 0, d = 0;
        for (int y = f.Y(y0); y < f.Y(y1); y += 2) for (int x = f.X(x0); x < f.X(x1); x += 2) {
            int p = f.px[y * f.w + x]; n++;
            if (Frame.r(p) + Frame.g(p) + Frame.b(p) < 130) d++;
        }
        return n == 0 ? 0 : (float) d / n;
    }

    /** Share of a health bar that is green; -1 when the bar isn't there at all. */
    static float greenFraction(Frame f, float x0, float x1, float ly) {
        int n = 0, gcount = 0, dark = 0;
        int y = f.Y(ly);
        for (int x = f.X(x0); x < f.X(x1); x++) {
            int p = f.px[y * f.w + x]; n++;
            if (green(p)) gcount++;
            else if (Frame.r(p) + Frame.g(p) + Frame.b(p) < 200 || Frame.r(p) > Frame.b(p) + 40) dark++;
        }
        if (n == 0 || gcount + dark < n * 0.8f) return -1;
        return (float) gcount / n;
    }

    /** Blue fill of the experience bar, as a share of the way to the cyan evolve marker. */
    static float xpFill(Frame f) {
        int y = f.Y(293), x0 = f.X(16), x1 = f.X(328);
        int last = -1, gap = 0;
        for (int x = x0; x < x1; x++) {
            int p = f.px[y * f.w + x];
            boolean fill = Frame.b(p) > 170 && Frame.r(p) < 110 && Frame.g(p) < 190;
            if (fill) { last = x; gap = 0; }
            else if (++gap > f.X(20)) { if (last >= 0 || x > f.X(40)) break; }
        }
        if (last < 0) return 0;
        return Math.min(1f, (float) (last - x0 + 1) / (x1 - x0));
    }

    /** A pale glow ring around a spell icon means it is ready. */
    static boolean glow(Frame f, float[][] pts) {
        int lit = 0;
        for (float[] q : pts) {
            int p = f.at(q[0], q[1]);
            if (Frame.r(p) > 118 && Frame.g(p) > 185 && Frame.b(p) > 195) lit++;
        }
        return lit >= 2;
    }

    static boolean readyButton(Frame f) {
        int n = 0, or = 0, wh = 0;
        for (int y = f.Y(640); y < f.Y(715); y += 3) for (int x = f.X(1150); x < f.X(1370); x += 3) {
            int p = f.px[y * f.w + x]; n++;
            if (orange(p) || (Frame.r(p) > 220 && Frame.g(p) > 120 && Frame.b(p) < 60)) or++;
            if (Frame.r(p) > 235 && Frame.g(p) > 235 && Frame.b(p) > 225) wh++;
        }
        return or > n * 0.35f && wh > n * 0.08f;
    }

    /** Red round close button with a white X, searched near an expected spot. */
    static float[] closeButton(Frame f, float cx, float cy) {
        for (float dy = -12; dy <= 12; dy += 6) for (float dx = -12; dx <= 12; dx += 6) {
            float x = cx + dx, y = cy + dy;
            int ok = 0;
            if (red(f.at(x - 17, y))) ok++;
            if (red(f.at(x + 17, y))) ok++;
            if (red(f.at(x, y - 17))) ok++;
            if (red(f.at(x, y + 17))) ok++;
            int c = f.at(x, y);
            boolean white = Frame.r(c) > 200 && Frame.g(c) > 200 && Frame.b(c) > 200;
            if (ok >= 3 && white) return new float[]{x, y};
        }
        return null;
    }

    static void readField(Frame f, GameState s) {
        // Ground colour = median of the band (most of it is bare ground).
        int[] hr = new int[256], hg = new int[256], hb = new int[256]; int n = 0;
        int step = Math.max(1, f.X(6) - f.X(0));
        for (int y = f.Y(FIELD_Y0); y < f.Y(FIELD_Y1); y += step) for (int x = f.X(FIELD_X0); x < f.w; x += step) {
            int p = f.px[y * f.w + x]; hr[Frame.r(p)]++; hg[Frame.g(p)]++; hb[Frame.b(p)]++; n++;
        }
        int gr = med(hr, n), gg = med(hg, n), gb = med(hb, n);
        s.fieldBusy = 0; s.nearBase = 0;
        for (int c = 0; c < FIELD_COLS; c++) {
            int x0 = f.X(FIELD_X0 + c * FIELD_COL_W), x1 = f.X(FIELD_X0 + (c + 1) * FIELD_COL_W);
            int tot = 0, obj = 0;
            for (int y = f.Y(FIELD_Y0); y < f.Y(FIELD_Y1); y += step) for (int x = x0; x < x1; x += step) {
                int p = f.px[y * f.w + x]; tot++;
                if (Math.abs(Frame.r(p) - gr) + Math.abs(Frame.g(p) - gg) + Math.abs(Frame.b(p) - gb) > 90) obj++;
            }
            float v = tot == 0 ? 0 : (float) obj / tot;
            s.field[c] = v;
            if (v > 0.15f) { s.fieldBusy++; if (c < 12) s.nearBase++; }
        }
    }

    static int med(int[] h, int n) { int acc = 0; for (int i = 0; i < 256; i++) { acc += h[i]; if (acc * 2 >= n) return i; } return 0; }

    /** Finds bright green buy buttons (upgrade rows, tower/turret popups) and reads their prices. */
    static void findGreenButtons(Frame f, GameState s, float lx0, float ly0, float lx1, float ly1) {
        final float cell = 8;
        int cw = (int) ((lx1 - lx0) / cell), ch = (int) ((ly1 - ly0) / cell);
        boolean[] on = new boolean[cw * ch];
        for (int j = 0; j < ch; j++) for (int i = 0; i < cw; i++) on[j * cw + i] = lime(f.at(lx0 + i * cell, ly0 + j * cell));
        boolean[] seen = new boolean[cw * ch];
        int[] queue = new int[cw * ch];
        for (int start = 0; start < on.length; start++) {
            if (!on[start] || seen[start]) continue;
            int head = 0, tail = 0, minI = cw, maxI = 0, minJ = ch, maxJ = 0, count = 0;
            queue[tail++] = start; seen[start] = true;
            while (head < tail) {
                int k = queue[head++], i = k % cw, j = k / cw; count++;
                minI = Math.min(minI, i); maxI = Math.max(maxI, i); minJ = Math.min(minJ, j); maxJ = Math.max(maxJ, j);
                // 8-neighbourhood with a 1-cell bridge, so white price digits don't split the button.
                for (int dj = -2; dj <= 2; dj++) for (int di = -2; di <= 2; di++) {
                    int ni = i + di, nj = j + dj;
                    if (ni < 0 || nj < 0 || ni >= cw || nj >= ch) continue;
                    int nk = nj * cw + ni;
                    if (on[nk] && !seen[nk]) { seen[nk] = true; queue[tail++] = nk; }
                }
            }
            float bw = (maxI - minI + 1) * cell, bh = (maxJ - minJ + 1) * cell;
            if (bw < 140 || bh < 40 || bw > 500 || bh > 160 || count < 60) continue;
            float bx = lx0 + minI * cell, by = ly0 + minJ * cell;
            Ocr.Result price = Ocr.read(f, bx + bw * 0.3f, by + 6, bx + bw - 6, by + bh - 14, Ocr.WHITE, 18, 70, 0.24f, 0.8f);
            s.greenButtons.add(new GameState.Button(bx + bw / 2, by + bh / 2, bw, bh, price.intValue()));
        }
    }
}
