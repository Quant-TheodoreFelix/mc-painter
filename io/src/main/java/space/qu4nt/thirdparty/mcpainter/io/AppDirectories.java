package space.qu4nt.thirdparty.mcpainter.io;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;

public final class AppDirectories {

    public static final String APP_FOLDER = "mc-painter";

    private AppDirectories() {
    }

    public static Path config(String osName, String home, String appData, String xdgConfigHome) {
        Path user = Path.of(home);
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        try {
            if (os.contains("mac")) {
                return user.resolve("Library").resolve("Application Support").resolve(APP_FOLDER);
            }
            if (os.contains("win")) {
                Path base = appData == null || appData.isBlank() ? user.resolve("AppData").resolve("Roaming")
                        : Path.of(appData);
                return absoluteOr(base, user.resolve("AppData").resolve("Roaming")).resolve(APP_FOLDER);
            }
            Path base = xdgConfigHome == null || xdgConfigHome.isBlank() ? user.resolve(".config")
                    : Path.of(xdgConfigHome);
            return absoluteOr(base, user.resolve(".config")).resolve(APP_FOLDER);
        } catch (InvalidPathException e) {
            return user.resolve("." + APP_FOLDER);
        }
    }

    public static Path config() {
        return config(System.getProperty("os.name"), System.getProperty("user.home", "."), System.getenv("APPDATA"),
                System.getenv("XDG_CONFIG_HOME"));
    }

    public static Path data(String osName, String home, String localAppData, String xdgDataHome) {
        Path user = Path.of(home);
        String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
        try {
            if (os.contains("mac")) {
                return user.resolve("Library").resolve("Application Support").resolve(APP_FOLDER);
            }
            if (os.contains("win")) {
                Path fallback = user.resolve("AppData").resolve("Local");
                Path base = localAppData == null || localAppData.isBlank() ? fallback : Path.of(localAppData);
                return absoluteOr(base, fallback).resolve(APP_FOLDER);
            }
            Path fallback = user.resolve(".local").resolve("share");
            Path base = xdgDataHome == null || xdgDataHome.isBlank() ? fallback : Path.of(xdgDataHome);
            return absoluteOr(base, fallback).resolve(APP_FOLDER);
        } catch (InvalidPathException e) {
            return user.resolve("." + APP_FOLDER);
        }
    }

    public static Path data() {
        return data(System.getProperty("os.name"), System.getProperty("user.home", "."), System.getenv("LOCALAPPDATA"),
                System.getenv("XDG_DATA_HOME"));
    }

    public static Path workspaces() {
        return data().resolve(Workspace.FOLDER);
    }

    private static Path absoluteOr(Path candidate, Path fallback) {
        return candidate.isAbsolute() ? candidate : fallback;
    }
}
