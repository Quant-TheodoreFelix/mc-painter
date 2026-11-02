package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.FileDialog;
import java.awt.Frame;
import java.io.FilenameFilter;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import space.qu4nt.thirdparty.mcpainter.anvil.world.LevelData;
import space.qu4nt.thirdparty.mcpainter.io.ProjectFile;

final class FileChoosers {

    static final String MAC_DIRECTORY_PROPERTY = "apple.awt.fileDialogForDirectories";
    static final String PNG = "png";

    enum Platform {
        MAC,
        WINDOWS,
        OTHER;

        static Platform of(String osName) {
            String os = osName == null ? "" : osName.toLowerCase(Locale.ROOT);
            if (os.contains("mac")) {
                return MAC;
            }
            return os.contains("win") ? WINDOWS : OTHER;
        }

        static Platform current() {
            return of(System.getProperty("os.name"));
        }
    }

    private FileChoosers() {
    }

    static Optional<Path> openProject(Frame owner, Messages messages, Path directory) {
        return open(owner, messages.text("chooser.openProject"), directory, projectExtension());
    }

    static Optional<Path> openPng(Frame owner, Messages messages, Path directory) {
        return open(owner, messages.text("chooser.openPng"), directory, PNG);
    }

    static Optional<Path> saveProject(Frame owner, Messages messages, Path directory, String suggestedName) {
        FileDialog dialog = new FileDialog(owner, messages.text("chooser.saveProject"), FileDialog.SAVE);
        setDirectory(dialog, directory != null ? directory : home());
        dialog.setFile(suggestedName);
        dialog.setVisible(true);
        return result(dialog);
    }

    static Optional<Path> chooseWorld(Frame owner, Messages messages, Path directory) {
        Platform platform = Platform.current();
        if (platform == Platform.MAC) {
            FileDialog dialog = new FileDialog(owner, messages.text("chooser.world"), FileDialog.LOAD);
            setDirectory(dialog, directory);
            String previous = System.getProperty(MAC_DIRECTORY_PROPERTY);
            System.setProperty(MAC_DIRECTORY_PROPERTY, "true");
            try {
                dialog.setVisible(true);
            } finally {
                if (previous == null) {
                    System.clearProperty(MAC_DIRECTORY_PROPERTY);
                } else {
                    System.setProperty(MAC_DIRECTORY_PROPERTY, previous);
                }
            }
            return result(dialog);
        }
        FileDialog dialog = new FileDialog(owner, messages.text("chooser.worldLevelDat"), FileDialog.LOAD);
        setDirectory(dialog, directory);
        dialog.setFilenameFilter((dir, name) -> name.equals(LevelData.FILE_NAME));
        dialog.setFile(LevelData.FILE_NAME);
        dialog.setVisible(true);
        return result(dialog).map(FileChoosers::worldDirectory);
    }

    static Path worldDirectory(Path selected) {
        Path name = selected.getFileName();
        if (name != null && name.toString().equals(LevelData.FILE_NAME) && selected.getParent() != null) {
            return selected.getParent();
        }
        return selected;
    }

    static Path withProjectExtension(Path file) {
        Path name = file.getFileName();
        if (name == null || name.toString().toLowerCase(Locale.ROOT).endsWith(ProjectFile.EXTENSION)) {
            return file;
        }
        return file.resolveSibling(name + ProjectFile.EXTENSION);
    }

    static boolean needsOverwriteConfirmation(Path chosen, Path target, boolean targetExists, boolean replacingOpen) {
        return targetExists && !replacingOpen && !target.equals(chosen);
    }

    static FilenameFilter extensionFilter(String extension) {
        String suffix = "." + extension.toLowerCase(Locale.ROOT);
        return (dir, name) -> name.toLowerCase(Locale.ROOT).endsWith(suffix);
    }

    static Path minecraftSaves(String osName, String home, String appData) {
        Path user = Path.of(home);
        try {
            return switch (Platform.of(osName)) {
                case MAC -> user.resolve("Library").resolve("Application Support").resolve("minecraft")
                        .resolve("saves");
                case WINDOWS -> appData == null || appData.isBlank() ? user : Path.of(appData, ".minecraft", "saves");
                case OTHER -> user.resolve(".minecraft").resolve("saves");
            };
        } catch (InvalidPathException e) {
            return user;
        }
    }

    static Path defaultWorldDirectory() {
        Path saves = minecraftSaves(System.getProperty("os.name", ""), home().toString(), System.getenv("APPDATA"));
        return Files.isDirectory(saves) ? saves : home();
    }

    static Optional<Path> resolve(String directory, String file) {
        if (file == null || file.isEmpty()) {
            return Optional.empty();
        }
        try {
            return Optional.of(directory == null ? Path.of(file) : Path.of(directory, file));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static Optional<Path> open(Frame owner, String title, Path directory, String extension) {
        FileDialog dialog = new FileDialog(owner, title, FileDialog.LOAD);
        setDirectory(dialog, directory);
        dialog.setFilenameFilter(extensionFilter(extension));
        if (Platform.current() == Platform.WINDOWS) {
            dialog.setFile("*." + extension);
        }
        dialog.setVisible(true);
        return result(dialog);
    }

    private static Optional<Path> result(FileDialog dialog) {
        return resolve(dialog.getDirectory(), dialog.getFile());
    }

    private static void setDirectory(FileDialog dialog, Path directory) {
        if (directory != null) {
            dialog.setDirectory(directory.toString());
        }
    }

    private static String projectExtension() {
        return ProjectFile.EXTENSION.substring(1);
    }

    private static Path home() {
        return Path.of(System.getProperty("user.home", "."));
    }
}
