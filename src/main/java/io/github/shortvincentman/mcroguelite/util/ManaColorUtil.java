package io.github.shortvincentman.mcroguelite.util;

import org.bukkit.Color;
import org.bukkit.Particle;
import org.bukkit.Particle.DustOptions;

/**
 * Utility class for full charge count-based colors.
 * Used for particles, effects, and future boss bar coloring.
 * Color progresses based on total full mana charges achieved.
 */
public class ManaColorUtil {

    // Color progression by full charge count
    private static final Color TIER_BEGINNER = Color.fromRGB(100, 149, 237);    // Light Blue (0-24 charges)
    private static final Color TIER_NOVICE = Color.fromRGB(0, 191, 255);        // Sky Blue (25-89 charges)
    private static final Color TIER_ADEPT = Color.fromRGB(138, 43, 226);        // Blue-Violet (90-149 charges)
    private static final Color TIER_EXPERT = Color.fromRGB(255, 0, 255);        // Magenta (150-249 charges)
    private static final Color TIER_MASTER = Color.fromRGB(255, 215, 0);        // Gold (250+ charges)

    /**
     * Get the color associated with a full charge count.
     * @param fullCharges The player's total full charge count (0+)
     * @return The corresponding Color
     */
    public static Color getColorForLevel(int fullCharges) {
        if (fullCharges < 25) {
            return TIER_BEGINNER;
        } else if (fullCharges < 90) {
            return TIER_NOVICE;
        } else if (fullCharges < 150) {
            return TIER_ADEPT;
        } else if (fullCharges < 250) {
            return TIER_EXPERT;
        } else {
            return TIER_MASTER;
        }
    }

    /**
     * Get a smoothly interpolated color based on full charge count.
     * Creates a gradient effect between tier colors.
     * @param fullCharges The player's total full charge count (0+)
     * @return Interpolated Color
     */
    public static Color getGradientColor(int fullCharges) {
        if (fullCharges < 25) {
            return interpolate(TIER_BEGINNER, TIER_NOVICE, fullCharges / 25.0);
        } else if (fullCharges < 90) {
            return interpolate(TIER_NOVICE, TIER_ADEPT, (fullCharges - 25) / 65.0);
        } else if (fullCharges < 150) {
            return interpolate(TIER_ADEPT, TIER_EXPERT, (fullCharges - 90) / 60.0);
        } else if (fullCharges < 250) {
            return interpolate(TIER_EXPERT, TIER_MASTER, (fullCharges - 150) / 100.0);
        } else {
            return TIER_MASTER;
        }
    }

    /**
     * Get DustOptions for DUST particles with charge-based coloring.
     * @param fullCharges The player's full charge count
     * @param size Particle size (0.5 - 4.0 recommended)
     * @return DustOptions for use with Particle.DUST
     */
    public static DustOptions getDustOptions(int fullCharges, float size) {
        return new DustOptions(getColorForLevel(fullCharges), size);
    }

    /**
     * Get DustOptions with default size (1.0).
     * @param fullCharges The player's full charge count
     * @return DustOptions for use with Particle.DUST
     */
    public static DustOptions getDustOptions(int fullCharges) {
        return getDustOptions(fullCharges, 1.0f);
    }

    /**
     * Get a DustTransition for particles that fade between two charge tier colors.
     * @param fromCharges Starting charge count for color
     * @param toCharges Ending charge count for color
     * @param size Particle size
     * @return DustTransition for use with Particle.DUST_COLOR_TRANSITION
     */
    public static Particle.DustTransition getDustTransition(int fromCharges, int toCharges, float size) {
        return new Particle.DustTransition(
                getColorForLevel(fromCharges),
                getColorForLevel(toCharges),
                size
        );
    }

    /**
     * Get the tier name for a full charge count.
     * @param fullCharges The player's full charge count
     * @return Tier name string
     */
    public static String getTierName(int fullCharges) {
        if (fullCharges < 25) {
            return "Beginner";
        } else if (fullCharges < 90) {
            return "Novice";
        } else if (fullCharges < 150) {
            return "Adept";
        } else if (fullCharges < 250) {
            return "Expert";
        } else {
            return "Master";
        }
    }

    /**
     * Get the chat color code for a full charge count.
     * @param fullCharges The player's full charge count
     * @return Minecraft color code string (e.g., "§b")
     */
    public static String getChatColor(int fullCharges) {
        if (fullCharges < 25) {
            return "§9";  // Blue
        } else if (fullCharges < 90) {
            return "§b";  // Aqua
        } else if (fullCharges < 150) {
            return "§5";  // Dark Purple
        } else if (fullCharges < 250) {
            return "§d";  // Light Purple
        } else {
            return "§6";  // Gold
        }
    }

    /**
     * Interpolate between two colors.
     * @param c1 Start color
     * @param c2 End color
     * @param ratio Interpolation ratio (0.0 - 1.0)
     * @return Interpolated Color
     */
    private static Color interpolate(Color c1, Color c2, double ratio) {
        ratio = Math.max(0, Math.min(1, ratio));
        int r = (int) (c1.getRed() + (c2.getRed() - c1.getRed()) * ratio);
        int g = (int) (c1.getGreen() + (c2.getGreen() - c1.getGreen()) * ratio);
        int b = (int) (c1.getBlue() + (c2.getBlue() - c1.getBlue()) * ratio);
        return Color.fromRGB(r, g, b);
    }

    /**
     * Get RGB values as an array for external use.
     * @param fullCharges The player's full charge count
     * @return int array [r, g, b]
     */
    public static int[] getRGB(int fullCharges) {
        Color c = getColorForLevel(fullCharges);
        return new int[]{c.getRed(), c.getGreen(), c.getBlue()};
    }
}
