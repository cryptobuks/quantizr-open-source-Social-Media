package quanta.util;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Random;
import javax.imageio.ImageIO;

/**
 * Implements the captcha image as seen on the Signup Page. I would normally use some existing
 * framework for something like this but I wrote this a long time ago and it works well, so I always
 * kept it. It's just too simple for me to have found fault with.
 */
public class CaptchaMaker {

    private static final Random rand = new Random();
    private static final BasicStroke stroke = new BasicStroke(3.0f);

    /* returns a random string of at least 5 numeric characters */
    public static String createCaptchaString() {
        StringBuilder captcha = new StringBuilder();

        while (captcha.length() < CAPTCHA_NUM_CHARS) {
            /* add random character between 0 and 9 */
            captcha.append(String.valueOf(rand.nextInt(10)));
        }
        return captcha.toString();
    }

    public static final int CAPTCHA_MAX_REQUESTS = 5;
    public static final int CAPTCHA_CHAR_SIZE = 40;
    public static final int CAPTCHA_NUM_CHARS = 5;

    private static Font fontKnown = null;

    /* writes captcha image out to stream */
    public static byte[] makeCaptcha(String captcha) {
        /* set some parameters */
        int len = captcha.length();
        int imgWidth = len * CAPTCHA_CHAR_SIZE - CAPTCHA_CHAR_SIZE / 2;
        int imgHeight = CAPTCHA_CHAR_SIZE * 2;

        /* setup image to draw into */
        BufferedImage outBufferedImage = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = outBufferedImage.createGraphics();

        g.setColor(Color.black);
        g.fillRect(0, 0, imgWidth, imgHeight);
        g.setColor(Color.green);

        /*
         * due to wierdness on various comptuers the rendering size of any given font is not known and we
         * must iterate to find the appropriate font to match our image size
         */
        if (fontKnown != null) {
            g.setFont(fontKnown);
        } else {
            int fontPoints = 12;

            while (fontPoints < 50) {
                g.setFont(fontKnown = new Font("Courier New", Font.BOLD, fontPoints));
                if (g.getFontMetrics().getHeight() >= CAPTCHA_CHAR_SIZE) {
                    break;
                }
                fontPoints += 1;
            }
        }
        FontMetrics fm = g.getFontMetrics();
        Rectangle2D sb = fontKnown.getStringBounds(captcha, g.getFontRenderContext());
        int charWidth = ((int) sb.getWidth()) / captcha.length();
        int charHeight = fm.getHeight();

        char[] chars = captcha.toCharArray();
        int charBase = charHeight + charHeight / 3;
        int advance = charWidth + charWidth / 2;
        int x = charWidth;
        g.setStroke(stroke);

        /* now draw the captcha characters into the image */
        for (int i = 0; i < len; i++, x += advance) {
            g.drawChars(chars, i, 1, x, charBase);
        }

        ByteArrayOutputStream tmp = null;
        try {
            BufferedImage scaledImage = ImageUtil.scaleImage(outBufferedImage, imgWidth);
            tmp = new ByteArrayOutputStream();
            ImageIO.write(scaledImage, "png", tmp);
        } catch (IOException e) {
            throw ExUtil.wrapEx(e);
        } finally {
            StreamUtil.close(tmp);
        }
        return tmp.toByteArray();
    }
}
