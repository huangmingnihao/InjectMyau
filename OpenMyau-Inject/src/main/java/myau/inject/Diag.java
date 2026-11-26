package myau.inject;

import java.io.File;
import java.io.FileWriter;

/**
 * Writes diagnostics into myau's own log next to the native log, so nothing lands
 * inside the game directory.  System.out is swallowed by the ForgeCompat loader
 * in some launchers, so file logging is the only reliable channel.
 */
public final class Diag {
    private static final String FILE_NAME =
            new File(System.getProperty("java.io.tmpdir", "."), "myau-native.log").getPath();
    private static boolean broken;

    private Diag() {
    }

    public static void log(String message) {
        if (broken) {
            return;
        }
        try {
            FileWriter writer = new FileWriter(FILE_NAME, true);
            try {
                writer.write("[myau-client] " + message + "\n");
            } finally {
                writer.close();
            }
        } catch (Throwable t) {
            broken = true;
        }
    }
}
