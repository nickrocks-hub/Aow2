import com.nickrocks.aow2bot.ai.*;
import com.nickrocks.aow2bot.vision.*;

/** Runs the brain over the recording frames (4 per second) and prints what it would tap. usage: Replay <dir> [profile] */
public class Replay {
    public static void main(String[] a) throws Exception {
        Profile p = a.length > 1 ? Learner.byName(a[1]) : Profile.BALANCED;
        Brain brain = new Brain(p);
        long t = 1_000_000;
        for (int i = 1; i <= 156; i++) {
            GameState s = Hud.read(Img.load(String.format("%s/g_%03d.jpg", a[0], i)));
            t += 250;
            Brain.Action act = brain.decide(s, t);
            for (String e : brain.drainEvents()) System.out.println("     " + e);
            System.out.printf("%03d %-60s %s%n", i, act == null ? "-" : act.toString(), brain.status());
        }
        System.out.println(brain.endOfBattle() + " " + brain.drainEvents());
    }
}
