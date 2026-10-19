import com.nickrocks.aow2bot.vision.*;
import java.util.*;
/** usage: GlyphDump <img> x0 y0 x1 y1 mask(H|W) minH maxH */
public class GlyphDump {
    public static void main(String[] a) throws Exception {
        Frame f = Img.load(a[0]);
        Ocr.Mask m = a[5].equals("W") ? Ocr.WHITE : Ocr.HUD_TEXT;
        for (Ocr.Glyph g : Ocr.glyphs(f, Float.parseFloat(a[1]), Float.parseFloat(a[2]), Float.parseFloat(a[3]), Float.parseFloat(a[4]), m, Float.parseFloat(a[6]), Float.parseFloat(a[7]))) {
            List<float[]> ds = new ArrayList<>();
            StringBuilder sb = new StringBuilder();
            Templates.T[] ts = Templates.ALL.clone();
            Arrays.sort(ts, Comparator.comparingDouble(t -> Ocr.distance(g, t)));
            for (int i = 0; i < 3; i++) sb.append(ts[i].ch).append(String.format(":%.2f ", Ocr.distance(g, ts[i])));
            System.out.printf("x %d-%d y %d-%d nw %d  %s%n", g.x0, g.x1, g.y0, g.y1, g.nw, sb);
            if (a.length > 8) for (int y = 0; y < Ocr.GH; y++) { StringBuilder r = new StringBuilder(); for (int x = 0; x < Ocr.GW; x++) r.append(g.grid[y*Ocr.GW+x] > 0.5 ? '#' : g.grid[y*Ocr.GW+x] > 0.2 ? '+' : '.'); System.out.println("   " + r); }
        }
    }
}
