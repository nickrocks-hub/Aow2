import com.nickrocks.aow2bot.vision.*;
import java.io.File;
import java.util.Arrays;

/** Prints what the bot reads from every .jpg/.png in a folder (sorted by name). usage: HudProbe <dir> [step] */
public class HudProbe {
    public static void main(String[] a) throws Exception {
        int step = a.length > 1 ? Integer.parseInt(a[1]) : 1;
        File[] files = new File(a[0]).listFiles((d, n) -> n.endsWith(".jpg") || n.endsWith(".png"));
        Arrays.sort(files);
        long total = 0; int n = 0;
        for (int i = 0; i < files.length; i += step) {
            Frame f = Img.load(files[i].getPath());
            long t0 = System.nanoTime();
            GameState s = Hud.read(f);
            total += System.nanoTime() - t0; n++;
            System.out.printf("%s %s%n", files[i].getName(), s.summary().replace("\n", "\n    "));
        }
        System.out.printf("avg read %.1f ms%n", total / 1e6 / Math.max(1, n));
    }
}
