package com.nickrocks.aow2bot.vision;

import java.util.ArrayList;
import java.util.List;

/**
 * Tiny number reader for the game's rounded bold font (gold, population, prices).
 * Splits the text into glyphs by empty columns, scales each glyph to a 14x16 grid by its
 * height (so big and small text both work) and picks the closest stored template.
 * Templates were measured from the Age of War 2 screen recording (see tools/desktop/MakeTemplates).
 */
public final class Ocr {
    public interface Mask { boolean on(int p); }

    /** Orange/white/light-blue text on the HUD's black boxes. Sky (red channel < 100) is excluded. */
    public static final Mask HUD_TEXT = p -> {
        int r = Frame.r(p), g = Frame.g(p), b = Frame.b(p);
        return r > 110 && r + g + b > 330;
    };
    /** White text (prices on orange or green buttons). */
    public static final Mask WHITE = p -> {
        int r = Frame.r(p), g = Frame.g(p), b = Frame.b(p);
        return r > 190 && g > 185 && b > 165 && Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b)) < 60;
    };

    public static final int GW = 14, GH = 16;

    public static final class Glyph {
        public final int x0, x1, y0, y1;   // device pixels, x1/y1 exclusive
        public final int nw;               // normalised width in grid cells
        public final float[] grid;
        Glyph(int x0, int x1, int y0, int y1, int nw, float[] grid) {
            this.x0 = x0; this.x1 = x1; this.y0 = y0; this.y1 = y1; this.nw = nw; this.grid = grid;
        }
        public int height() { return y1 - y0; }
    }

    public static final class Result {
        public final String text;     // null when any glyph could not be read
        public final float worst;     // largest template distance among the glyphs
        public final int glyphs;
        Result(String text, float worst, int glyphs) { this.text = text; this.worst = worst; this.glyphs = glyphs; }
        public int intValue() {
            if (text == null || text.isEmpty()) return -1;
            int v = 0;
            for (int i = 0; i < text.length(); i++) {
                char c = text.charAt(i);
                if (c < '0' || c > '9') return -1;
                v = v * 10 + (c - '0');
                if (v > 99_999_999) return -1;
            }
            return v;
        }
        @Override public String toString() { return text == null ? "?" : text; }
    }

    /**
     * Finds glyphs in a layout rectangle. Glyphs shorter than minH layout pixels are ignored
     * (coin edges, dust), as are glyphs taller than maxH (icons).
     */
    public static List<Glyph> glyphs(Frame f, float lx0, float ly0, float lx1, float ly1,
                                     Mask m, float minH, float maxH) {
        int x0 = f.X(lx0), x1 = f.X(lx1), y0 = f.Y(ly0), y1 = f.Y(ly1);
        int W = x1 - x0, H = y1 - y0;
        List<Glyph> out = new ArrayList<>();
        if (W <= 0 || H <= 0) return out;
        boolean[] on = new boolean[W * H];
        int[] col = new int[W];
        for (int y = 0; y < H; y++) {
            int row = (y0 + y) * f.w + x0;
            for (int x = 0; x < W; x++) {
                if (m.on(f.px[row + x])) { on[y * W + x] = true; col[x]++; }
            }
        }
        int dMin = Math.max(2, Math.round(minH * f.sy)), dMax = Math.round(maxH * f.sy);
        int x = 0;
        while (x < W) {
            if (col[x] == 0) { x++; continue; }
            int s = x;
            while (x < W && col[x] > 0) x++;
            int e = x;
            int top = H, bot = -1;
            for (int yy = 0; yy < H; yy++) {
                for (int xx = s; xx < e; xx++) {
                    if (on[yy * W + xx]) { if (yy < top) top = yy; bot = yy; break; }
                }
            }
            int gh = bot - top + 1;
            if (bot < 0 || gh < dMin || gh > dMax) continue;
            out.add(normalise(on, W, s, e, top, bot + 1, x0, y0));
        }
        return out;
    }

    private static Glyph normalise(boolean[] on, int W, int s, int e, int top, int bot, int ox, int oy) {
        int gw = e - s, gh = bot - top;
        float scale = (float) GH / gh;
        int nw = Math.max(1, Math.min(GW, Math.round(gw * scale)));
        float[] grid = new float[GW * GH];
        for (int gy = 0; gy < GH; gy++) {
            int sy0 = top + gy * gh / GH, sy1 = Math.max(sy0 + 1, top + (gy + 1) * gh / GH);
            for (int gx = 0; gx < nw; gx++) {
                int sx0 = s + gx * gw / nw, sx1 = Math.max(sx0 + 1, s + (gx + 1) * gw / nw);
                int n = 0, c = 0;
                for (int yy = sy0; yy < sy1; yy++) for (int xx = sx0; xx < sx1; xx++) { n++; if (on[yy * W + xx]) c++; }
                grid[gy * GW + gx] = (float) c / n;
            }
        }
        return new Glyph(ox + s, ox + e, oy + top, oy + bot, nw, grid);
    }

    /** Distance between a glyph and a template (0 = identical). */
    public static float distance(Glyph g, Templates.T t) {
        float d = 0;
        for (int i = 0; i < GW * GH; i++) d += Math.abs(g.grid[i] - t.grid[i]);
        return d / (GW * GH) + 0.025f * Math.abs(g.nw - t.nw);
    }

    /** Reads a run of characters (digits and '/'). */
    public static Result read(Frame f, float lx0, float ly0, float lx1, float ly1,
                              Mask m, float minH, float maxH, float maxDist) {
        return read(f, lx0, ly0, lx1, ly1, m, minH, maxH, maxDist, 0.6f);
    }

    /** As above; glyphs shorter than minRel x the tallest glyph are ignored as noise. */
    public static Result read(Frame f, float lx0, float ly0, float lx1, float ly1,
                              Mask m, float minH, float maxH, float maxDist, float minRel) {
        List<Glyph> all = glyphs(f, lx0, ly0, lx1, ly1, m, minH, maxH);
        // Drop slivers (coin highlights, panel borders) and anything much shorter than the text.
        int tallest = 0;
        for (Glyph g : all) if (g.nw > 4) tallest = Math.max(tallest, g.height());
        List<Glyph> gs = new ArrayList<>();
        for (Glyph g : all) if (g.nw > 4 && g.height() >= tallest * minRel) gs.add(g);
        StringBuilder sb = new StringBuilder();
        float worst = 0;
        for (Glyph g : gs) {
            Templates.T best = null; float bd = Float.MAX_VALUE;
            for (Templates.T t : Templates.ALL) {
                float d = distance(g, t);
                if (d < bd) { bd = d; best = t; }
            }
            worst = Math.max(worst, bd);
            if (best == null || bd > maxDist) return new Result(null, worst, gs.size());
            sb.append(best.ch);
        }
        return new Result(sb.toString(), worst, gs.size());
    }
}
