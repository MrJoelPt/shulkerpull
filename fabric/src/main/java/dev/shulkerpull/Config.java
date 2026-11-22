package dev.shulkerpull;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/** Tiny settings file: config/shulkerpull.properties (created on first start, edit it and restart the game). */
public final class Config {
    private static final double DEFAULT_RANGE = 12.0;
    private static final double MIN_RANGE = 1.0;
    private static final double MAX_RANGE = 256.0;
    private static final System.Logger LOG = System.getLogger("shulkerpull");

    private static volatile double range = DEFAULT_RANGE;

    private Config() {}

    /** How many blocks around you Shulker Pull looks at for blocks the build still needs. */
    public static double range() {
        return range;
    }

    public static void load(Path configDir) {
        Path file = configDir.resolve("shulkerpull.properties");
        try {
            if (!Files.exists(file)) {
                Files.createDirectories(configDir);
                Files.writeString(file,
                        "# Shulker Pull settings. Restart the game after changing them.\n"
                      + "# range: only blocks of the build within this many blocks of you are taken into account.\n"
                      + "#        Your normal survival reach is about 4.5 blocks. Allowed: 1 to 256.\n"
                      + "range=" + (int) DEFAULT_RANGE + "\n", StandardCharsets.UTF_8);
                return;
            }
            Properties p = new Properties();
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            }
            String v = p.getProperty("range");
            if (v != null) {
                double d = Double.parseDouble(v.trim());
                range = Math.max(MIN_RANGE, Math.min(MAX_RANGE, d));
            }
        } catch (IOException | NumberFormatException e) {
            LOG.log(System.Logger.Level.WARNING, "Shulker Pull: could not read settings, using range {0}", DEFAULT_RANGE);
            range = DEFAULT_RANGE;
        }
    }
}
