import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Simplifies an image by merging similar pixel colors into one shared color
 * (color quantization). This reduces the number of distinct colors and makes
 * the PNG file much smaller.
 *
 * Usage:
 *   java ImageSimplifier <input> <output.png> [tolerance]
 *
 * tolerance = allowed color distance (0-255, default 32).
 *   small : many colors, barely visible difference, small savings
 *   large : few colors, poster effect, big savings
 */
public class ImageSimplifier {

    /** Weights for the color distance (the eye is most sensitive to green). */
    private static final double WEIGHT_RED = 0.30;
    private static final double WEIGHT_GREEN = 0.59;
    private static final double WEIGHT_BLUE = 0.11;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Usage: java ImageSimplifier <input> <output.png> [tolerance]");
            System.out.println("Example: java ImageSimplifier photo.jpg small.png 32");
            return;
        }

        File input = new File(args[0]);
        File output = new File(args[1]);
        if (!input.isFile()) {
            System.out.println("Error: The file '" + input + "' was not found.");
            return;
        }

        int tolerance = 32;
        if (args.length > 2) {
            try {
                tolerance = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                System.out.println("Error: The tolerance must be a whole number, e.g. 32.");
                return;
            }
            if (tolerance < 0 || tolerance > 255) {
                System.out.println("Error: The tolerance must be between 0 and 255.");
                return;
            }
        }

        BufferedImage original = ImageIO.read(input);
        if (original == null) {
            System.out.println("Error: '" + input + "' could not be read as an image.");
            return;
        }

        BufferedImage rgb = toRgb(original);
        int colorsBefore = countColors(rgb);

        // 1. Build a palette from similar colors
        List<Integer> palette = buildPalette(rgb, tolerance);

        // 2. Map every pixel to its matching palette color
        BufferedImage simplified = applyPalette(rgb, palette);

        // 3. Save: with <= 256 colors as an indexed PNG (saves the most)
        if (palette.size() <= 256) {
            simplified = toIndexed(simplified, palette);
        }
        ImageIO.write(simplified, "png", output);

        // 4. Report
        long sizeBefore = input.length();
        long sizeAfter = output.length();
        System.out.println("Image:     " + rgb.getWidth() + " x " + rgb.getHeight() + " pixels");
        System.out.println("Tolerance: " + tolerance);
        System.out.println("Colors:    " + colorsBefore + "  ->  " + palette.size());
        System.out.printf("File size: %,d bytes  ->  %,d bytes  (%.1f %% saved)%n",
                sizeBefore, sizeAfter,
                100.0 * (sizeBefore - sizeAfter) / sizeBefore);
        System.out.println("Saved to:  " + output.getPath());
        if (sizeAfter > sizeBefore) {
            System.out.println("Note: The new file is larger than the original. JPG photos");
            System.out.println("are often already heavily compressed. Try a higher tolerance, e.g. 48 or 64.");
        }
    }

    /**
     * Builds the color palette, most frequent colors first. A new color is
     * only added to the palette if it is further away from all existing
     * palette colors than the tolerance.
     */
    private static List<Integer> buildPalette(BufferedImage image, int tolerance) {
        Map<Integer, Integer> frequency = new HashMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y) & 0xFFFFFF;
                frequency.merge(color, 1, Integer::sum);
            }
        }

        List<Integer> sorted = new ArrayList<>(frequency.keySet());
        sorted.sort((a, b) -> frequency.get(b) - frequency.get(a));

        // Tolerance 0: every color is kept, no comparing needed.
        // (Before, every color was compared with every other one: with 40,000
        // colors that was over a billion comparisons and about 25 seconds.)
        if (tolerance == 0) {
            return sorted;
        }

        double limit = (double) tolerance * tolerance;
        List<Integer> palette = new ArrayList<>();
        for (int color : sorted) {
            boolean matchesExisting = false;
            for (int p : palette) {
                if (squaredDistance(color, p) <= limit) {
                    matchesExisting = true;
                    break;
                }
            }
            if (!matchesExisting) {
                palette.add(color);
            }
        }
        return palette;
    }

    /** Replaces every pixel with the closest palette color. */
    private static BufferedImage applyPalette(BufferedImage image, List<Integer> palette) {
        BufferedImage result = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);

        // Cache so the same color is not looked up more than once.
        // Colors that are already in the palette stay unchanged.
        Map<Integer, Integer> cache = new HashMap<>();
        for (int p : palette) {
            cache.put(p, p);
        }

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int color = image.getRGB(x, y) & 0xFFFFFF;
                Integer mapped = cache.get(color);
                if (mapped == null) {
                    mapped = closestColor(color, palette);
                    cache.put(color, mapped);
                }
                result.setRGB(x, y, mapped);
            }
        }
        return result;
    }

    /** Finds the palette color with the smallest distance. */
    private static int closestColor(int color, List<Integer> palette) {
        int best = palette.get(0);
        double bestDistance = Double.MAX_VALUE;
        for (int p : palette) {
            double distance = squaredDistance(color, p);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = p;
                if (distance == 0) {
                    break;
                }
            }
        }
        return best;
    }

    /** Weighted squared color distance between two RGB values. */
    private static double squaredDistance(int color1, int color2) {
        int dr = ((color1 >> 16) & 0xFF) - ((color2 >> 16) & 0xFF);
        int dg = ((color1 >> 8) & 0xFF) - ((color2 >> 8) & 0xFF);
        int db = (color1 & 0xFF) - (color2 & 0xFF);
        return WEIGHT_RED * dr * dr + WEIGHT_GREEN * dg * dg + WEIGHT_BLUE * db * db;
    }

    /** Counts the distinct colors in the image. */
    private static int countColors(BufferedImage image) {
        Map<Integer, Boolean> seen = new HashMap<>();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                seen.put(image.getRGB(x, y) & 0xFFFFFF, Boolean.TRUE);
            }
        }
        return seen.size();
    }

    /** Converts any input format into a uniform RGB image. */
    private static BufferedImage toRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) {
            return image;
        }
        BufferedImage rgb = new BufferedImage(
                image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(image, 0, 0, java.awt.Color.WHITE, null);
        return rgb;
    }

    /**
     * Stores the image with 8 bits per pixel instead of 24 bits. This is
     * where the actual space is saved: only one palette index per pixel.
     */
    private static BufferedImage toIndexed(BufferedImage image, List<Integer> palette) {
        int n = palette.size();
        byte[] r = new byte[n];
        byte[] g = new byte[n];
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            int color = palette.get(i);
            r[i] = (byte) ((color >> 16) & 0xFF);
            g[i] = (byte) ((color >> 8) & 0xFF);
            b[i] = (byte) (color & 0xFF);
        }

        int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(1, n - 1)));
        if (bits > 4) {
            bits = 8;
        } else if (bits > 2) {
            bits = 4;
        }

        IndexColorModel model = new IndexColorModel(bits, n, r, g, b);
        BufferedImage indexed = new BufferedImage(
                image.getWidth(), image.getHeight(),
                bits <= 4 ? BufferedImage.TYPE_BYTE_BINARY : BufferedImage.TYPE_BYTE_INDEXED,
                model);
        indexed.getGraphics().drawImage(image, 0, 0, null);
        return indexed;
    }
}
