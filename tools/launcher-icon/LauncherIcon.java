import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * The Android launcher icon, made from the iOS TEST icon (DECISIONS
 * 2026-10-07, "Android gets the same TEST icon").
 *
 * An adaptive icon is a 108 dp square of which a launcher shows at most the
 * middle 72 dp, cut to its own shape -- a circle on a Pixel, a squircle on
 * many others -- and only a 66 dp circle is promised to survive every mask.
 * So the iOS artwork is not copied edge to edge: it is drawn ARTWORK_DP wide
 * in the middle, which keeps "ZoU" and "TEST" well inside that circle, and
 * its yellow and its red band are carried out to the edges so that no mask
 * finds a gap.
 *
 * Run from the repository root, in the container:
 *
 *     java tools/launcher-icon/LauncherIcon.java [preview directory]
 *
 * It writes ic_launcher_foreground.png into the five mipmap directories and,
 * given a directory, the result under a circle, a squircle and a rounded
 * square, and the artwork against the zones, to look at before committing.
 * When the real icon arrives, it replaces this file's output, and this file
 * goes with it.
 */
public final class LauncherIcon {
    private static final String SOURCE = "iosApp/iosApp/Assets.xcassets/AppIcon.appiconset/AppIcon-test-1024.png";
    private static final String RES = "androidApp/src/main/res";

    private static final int CANVAS_DP = 108;
    private static final int VIEWPORT_DP = 72;
    private static final int SAFE_ZONE_DP = 66;
    private static final int ARTWORK_DP = 64;

    // Composed once at 16 px per dp -- 64 dp of artwork is then the 1024 px
    // source exactly -- and scaled down to each density from there.
    private static final int MASTER_PX_PER_DP = 16;
    private static final String[] DENSITY_NAMES = {"mdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi"};
    private static final double[] DENSITY_FACTORS = {1.0, 1.5, 2.0, 3.0, 4.0};

    private LauncherIcon() {
    }

    public static void main(String[] args) throws IOException {
        BufferedImage source = ImageIO.read(new File(SOURCE));
        BufferedImage canvas = compose(source);
        BufferedImage largest = null;
        for (int i = 0; i < DENSITY_NAMES.length; i++) {
            BufferedImage icon = scaled(canvas, (int) Math.round(CANVAS_DP * DENSITY_FACTORS[i]));
            File out = new File(RES + "/mipmap-" + DENSITY_NAMES[i] + "/ic_launcher_foreground.png");
            out.getParentFile().mkdirs();
            ImageIO.write(icon, "png", out);
            System.out.println(out + "  " + icon.getWidth() + " px");
            largest = icon;
        }
        if (args.length > 0) {
            File directory = new File(args[0]);
            directory.mkdirs();
            ImageIO.write(masks(largest), "png", new File(directory, "launcher-icon-masks.png"));
            ImageIO.write(zones(largest), "png", new File(directory, "launcher-icon-zones.png"));
            System.out.println("previews in " + directory);
        }
    }

    /** The whole 108 dp canvas: yellow, the band carried across, the artwork in the middle. */
    private static BufferedImage compose(BufferedImage source) {
        int size = source.getWidth();
        if (size != source.getHeight() || (source.getRGB(0, 0) >>> 24) != 0xFF) {
            throw new IllegalStateException("expected an opaque square source, " + SOURCE);
        }
        // The band, read off the left edge, where nothing is drawn over it.
        int column = 4;
        Color yellow = new Color(source.getRGB(column, column));
        int first = -1;
        int last = -1;
        for (int y = 0; y < size; y++) {
            if (distance(new Color(source.getRGB(column, y)), yellow) > 0) {
                if (first < 0) {
                    first = y;
                }
                last = y;
            }
        }
        if (first < 0) {
            throw new IllegalStateException("no band found in " + SOURCE);
        }
        Color red = new Color(source.getRGB(column, (first + last) / 2));
        double bandTop = first + 1 - coverage(new Color(source.getRGB(column, first)), yellow, red);
        double bandBottom = last + coverage(new Color(source.getRGB(column, last)), yellow, red);
        System.out.printf("yellow #%06X, red #%06X, band %.2f..%.2f of %d px%n",
            yellow.getRGB() & 0xFFFFFF, red.getRGB() & 0xFFFFFF, bandTop, bandBottom, size);

        int master = CANVAS_DP * MASTER_PX_PER_DP;
        double scale = (double) ARTWORK_DP * MASTER_PX_PER_DP / size;
        double offset = (master - size * scale) / 2;
        BufferedImage canvas = new BufferedImage(master, master, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = canvas.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setColor(yellow);
        g.fillRect(0, 0, master, master);
        g.setColor(red);
        g.fill(new Rectangle2D.Double(0, offset + bandTop * scale, master, (bandBottom - bandTop) * scale));
        g.drawImage(source, new AffineTransform(scale, 0, 0, scale, offset, offset), null);
        g.dispose();
        return canvas;
    }

    private static int distance(Color a, Color b) {
        return Math.abs(a.getRed() - b.getRed()) + Math.abs(a.getGreen() - b.getGreen())
            + Math.abs(a.getBlue() - b.getBlue());
    }

    /** How much of a pixel the band covers, from where its colour lies between the two. */
    private static double coverage(Color pixel, Color yellow, Color red) {
        return Math.min(1.0, (double) distance(pixel, yellow) / distance(red, yellow));
    }

    private static BufferedImage scaled(BufferedImage image, int size) {
        BufferedImage out = new BufferedImage(size, size, image.getType());
        Graphics2D g = out.createGraphics();
        g.drawImage(image.getScaledInstance(size, size, Image.SCALE_AREA_AVERAGING), 0, 0, null);
        g.dispose();
        return out;
    }

    // ---- previews, never shipped ------------------------------------------

    /** The icon under three launcher masks, on light and dark, and at home-screen size. */
    private static BufferedImage masks(BufferedImage icon) {
        int pxPerDp = icon.getWidth() / CANVAS_DP;
        int viewport = VIEWPORT_DP * pxPerDp;
        Shape[] shapes = {
            new Ellipse2D.Double(0, 0, viewport, viewport),
            squircle(viewport),
            new RoundRectangle2D.Double(0, 0, viewport, viewport, viewport * 0.16, viewport * 0.16),
        };
        int pad = 24;
        int cell = viewport + 2 * pad;
        int small = 48 * 3;
        BufferedImage sheet = new BufferedImage(cell * shapes.length, cell * 2 + small + 2 * pad,
            BufferedImage.TYPE_INT_RGB);
        Graphics2D g = sheet.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setColor(new Color(0xE8EAED));
        g.fillRect(0, 0, sheet.getWidth(), cell);
        g.setColor(new Color(0x202124));
        g.fillRect(0, cell, sheet.getWidth(), sheet.getHeight() - cell);
        for (int i = 0; i < shapes.length; i++) {
            BufferedImage masked = masked(icon, shapes[i], viewport);
            g.drawImage(masked, i * cell + pad, pad, null);
            g.drawImage(masked, i * cell + pad, cell + pad, null);
            g.drawImage(masked.getScaledInstance(small, small, Image.SCALE_AREA_AVERAGING),
                i * cell + (cell - small) / 2, 2 * cell + pad, null);
        }
        g.dispose();
        return sheet;
    }

    private static BufferedImage masked(BufferedImage icon, Shape shape, int viewport) {
        int inset = (icon.getWidth() - viewport) / 2;
        BufferedImage out = new BufferedImage(viewport, viewport, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setClip(shape);
        g.drawImage(icon, -inset, -inset, null);
        g.dispose();
        return out;
    }

    /** AOSP's squircle mask, "M50,0 C10,0 0,10 0,50 0,90 10,100 50,100 90,100 100,90 100,50 100,10 90,0 50,0 Z". */
    private static Shape squircle(int size) {
        double k = size / 100.0;
        Path2D.Double path = new Path2D.Double();
        path.moveTo(50 * k, 0);
        path.curveTo(10 * k, 0, 0, 10 * k, 0, 50 * k);
        path.curveTo(0, 90 * k, 10 * k, 100 * k, 50 * k, 100 * k);
        path.curveTo(90 * k, 100 * k, 100 * k, 90 * k, 100 * k, 50 * k);
        path.curveTo(100 * k, 10 * k, 90 * k, 0, 50 * k, 0);
        path.closePath();
        return path;
    }

    /** The whole canvas with the 72 dp viewport and the 66 dp safe circle drawn on it. */
    private static BufferedImage zones(BufferedImage icon) {
        int pxPerDp = icon.getWidth() / CANVAS_DP;
        BufferedImage out = new BufferedImage(icon.getWidth(), icon.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(icon, 0, 0, null);
        g.setStroke(new BasicStroke(2f));
        g.setColor(new Color(0x1A73E8));
        double viewport = VIEWPORT_DP * pxPerDp;
        double viewportInset = (icon.getWidth() - viewport) / 2;
        g.draw(new Rectangle2D.Double(viewportInset, viewportInset, viewport, viewport));
        g.setColor(new Color(0x00C853));
        double safe = SAFE_ZONE_DP * pxPerDp;
        double safeInset = (icon.getWidth() - safe) / 2;
        g.draw(new Ellipse2D.Double(safeInset, safeInset, safe, safe));
        g.dispose();
        return out;
    }
}
