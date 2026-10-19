import com.nickrocks.aow2bot.vision.*;

/** Prints gold / population / unit prices read from each frame. usage: OcrProbe <dir> [step] */
public class OcrProbe {
    public static void main(String[] a) throws Exception {
        int step = a.length > 1 ? Integer.parseInt(a[1]) : 1;
        for (int i = 1; i <= 156; i += step) {
            Frame f = Img.load(String.format("%s/g_%03d.jpg", a[0], i));
            Ocr.Result gold = Ocr.read(f, 470, 160, 572, 189, Ocr.HUD_TEXT, 15, 30, 0.2f);
            Ocr.Result pop = Ocr.read(f, 485, 217, 568, 250, Ocr.HUD_TEXT, 12, 34, 0.2f);
            StringBuilder costs = new StringBuilder();
            for (int k = 0; k < 4; k++) {
                float cx = new float[]{74, 206, 342, 476}[k];
                Ocr.Result c = Ocr.read(f, cx - 11, 1030, cx + 40, 1056, Ocr.WHITE, 10, 30, 0.2f);
                costs.append(c).append(String.format("(%.2f) ", c.worst));
            }
            System.out.printf("%03d gold=%s(%.2f) pop=%s(%.2f) costs=%s%n", i, gold, gold.worst, pop, pop.worst, costs);
        }
    }
}
