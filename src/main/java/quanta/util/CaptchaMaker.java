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
    public static final int CAPTCHA_NUM_CHARS = 4;

    private static Font fontKnown = null;

    /* writes captcha image out to stream */
    public static byte[] makeCaptcha(String captchaCode) {
        /* set some parameters */
        int len = captchaCode.length();
        int imgWidth = len * CAPTCHA_CHAR_SIZE;
        int imgHeight = CAPTCHA_CHAR_SIZE * 3;

        /* setup image to draw into */
        BufferedImage outBufferedImage = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = outBufferedImage.createGraphics();

        /* configure drawing */
        g.setColor(Color.white);
        g.fillRect(0, 0, imgWidth, imgHeight);
        g.setColor(Color.black);

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

                /*
                 * if our font is big enough break out (+5 is just a hack cuz it looks like there is always room for
                 * slightly bigger characters
                 */
                if (g.getFontMetrics().getHeight() >= CAPTCHA_CHAR_SIZE + 7) {
                    break;
                }
                fontPoints += 2;
            }
        }
        FontMetrics fm = g.getFontMetrics();
        Rectangle2D sb = fontKnown.getStringBounds(captchaCode, g.getFontRenderContext());
        int charWidth = ((int) sb.getWidth()) / captchaCode.length();
        int charHeight = fm.getHeight();

        char[] chars = captchaCode.toCharArray();
        int charBase = charHeight * 2 - charHeight / 2;

        int advance = charWidth + 3;
        int x = charWidth / 2, y, i;

        g.setStroke(stroke);
        g.setColor(Color.black);
        int lineIdx = 0;

        /* now draw the captcha characters into the image */
        for (i = 0; i < len; i++, x += advance) {
            y = charBase - rand.nextInt(charHeight);

            /* oscillate back and forth rotation */
            double angle = rand.nextFloat() * .50 * ((i % 2 == 0) ? 1 : -1);
            double shearX = rand.nextFloat() / 4;
            double shearY = rand.nextFloat() / 4;

            /* rotate & sheer */
            g.rotate(angle, x, y);
            g.shear(shearX, shearY);
            g.drawChars(chars, i, 1, x, y);

            /* unrotate & sheer (we don't want a cumulative effect) */
            g.shear(-shearX, -shearY);
            g.rotate(-angle, x, y);

            // note lineIdx remainder controls back and forth tilt angle of line
            int delta = (lineIdx % 2 == 0) ? rand.nextInt(imgHeight) : -rand.nextInt(imgHeight);

            g.drawLine(0, y - delta, imgWidth, y + delta);
            lineIdx++;
        }

        // draw three more lines just to add more difficulty.
        for (int idx = 0; idx < 3; idx++) {
            y = charBase - rand.nextInt(charHeight);
            int delta = (idx % 2 == 0) ? rand.nextInt(imgHeight) : -rand.nextInt(imgHeight);
            g.drawLine(0, y - delta, imgWidth, y + delta);
        }

        ByteArrayOutputStream tmp = null;
        try {
            BufferedImage scaledImage = ImageUtil.scaleImage(outBufferedImage, imgWidth);
            tmp = new ByteArrayOutputStream();
            try {
                ImageIO.write(scaledImage, "png", tmp);
            } catch (IOException e) {
                throw ExUtil.wrapEx(e);
            }
        } finally {
            StreamUtil.close(tmp);
        }
        return tmp.toByteArray();
    }
}
