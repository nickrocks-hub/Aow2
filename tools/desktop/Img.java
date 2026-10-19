import com.nickrocks.aow2bot.vision.Frame;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/** Loads an image file as a Frame (desktop tools only). */
final class Img {
    static Frame load(String path) throws Exception {
        BufferedImage im = ImageIO.read(new File(path));
        int w = im.getWidth(), h = im.getHeight();
        int[] px = im.getRGB(0, 0, w, h, null, 0, w);
        return new Frame(w, h, px);
    }
}
