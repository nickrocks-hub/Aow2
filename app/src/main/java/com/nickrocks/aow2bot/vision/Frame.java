package com.nickrocks.aow2bot.vision;

/**
 * A screen frame as packed ARGB ints. No Android dependencies, so it can be tested on a computer.
 * All positions in this app are written in "layout" pixels: the 2520x1080 landscape screen the
 * Age of War 2 recording was made on. They are scaled to the real frame size here.
 */
public final class Frame {
    public static final float LAYOUT_W = 2520f, LAYOUT_H = 1080f;

    public final int w, h;
    public final int[] px;
    public final float sx, sy;

    public Frame(int w, int h, int[] px) {
        this.w = w; this.h = h; this.px = px;
        this.sx = w / LAYOUT_W; this.sy = h / LAYOUT_H;
    }

    public boolean isLandscape() { return w > h; }

    public int X(float layoutX) { return clamp(Math.round(layoutX * sx), 0, w - 1); }
    public int Y(float layoutY) { return clamp(Math.round(layoutY * sy), 0, h - 1); }

    /** Pixel at a layout position. */
    public int at(float lx, float ly) { return px[Y(ly) * w + X(lx)]; }

    public static int r(int p) { return (p >> 16) & 0xff; }
    public static int g(int p) { return (p >> 8) & 0xff; }
    public static int b(int p) { return p & 0xff; }

    public static int clamp(int v, int lo, int hi) { return v < lo ? lo : (v > hi ? hi : v); }

    /** Mean R,G,B of a layout rectangle. */
    public int[] meanRgb(float x0, float y0, float x1, float y1) {
        int ax = X(x0), bx = Math.max(ax + 1, X(x1)), ay = Y(y0), by = Math.max(ay + 1, Y(y1));
        long sr = 0, sg = 0, sb = 0; int n = 0;
        for (int y = ay; y < by; y++) for (int x = ax; x < bx; x++) {
            int p = px[y * w + x]; sr += r(p); sg += g(p); sb += b(p); n++;
        }
        return new int[]{(int) (sr / n), (int) (sg / n), (int) (sb / n)};
    }
}
