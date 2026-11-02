package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.Component;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.filechooser.FileSystemView;
import javax.swing.filechooser.FileView;
import space.qu4nt.thirdparty.mcpainter.io.ProjectFile;

final class FileChoosers {

    private FileChoosers() {
    }

    static Optional<Path> openProject(Component parent, Messages messages, Path directory) {
        JFileChooser chooser = chooser(directory);
        chooser.setFileFilter(new FileNameExtensionFilter(messages.text("filter.project"), extension()));
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? selected(chooser) : Optional.empty();
    }

    static Optional<Path> saveProject(Component parent, Messages messages, Path directory, String suggestedName) {
        JFileChooser chooser = chooser(directory);
        chooser.setFileFilter(new FileNameExtensionFilter(messages.text("filter.project"), extension()));
        if (directory != null) {
            chooser.setSelectedFile(directory.resolve(suggestedName).toFile());
        }
        if (chooser.showSaveDialog(parent) != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }
        return selected(chooser).map(FileChoosers::withProjectExtension);
    }

    static Optional<Path> openPng(Component parent, Messages messages, Path directory) {
        JFileChooser chooser = chooser(directory);
        chooser.setFileFilter(new FileNameExtensionFilter(messages.text("filter.png"), "png"));
        return chooser.showOpenDialog(parent) == JFileChooser.APPROVE_OPTION ? selected(chooser) : Optional.empty();
    }

    static Optional<Path> chooseWorld(Component parent, Messages messages, Path directory) {
        JFileChooser chooser = chooser(directory);
        chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        chooser.setAcceptAllFileFilterUsed(false);
        chooser.setDialogTitle(messages.text("chooser.world"));
        return chooser.showDialog(parent, messages.text("dialog.select")) == JFileChooser.APPROVE_OPTION
                ? selected(chooser) : Optional.empty();
    }

    static Path withProjectExtension(Path file) {
        Path name = file.getFileName();
        if (name == null || name.toString().toLowerCase(Locale.ROOT).endsWith(ProjectFile.EXTENSION)) {
            return file;
        }
        return file.resolveSibling(name + ProjectFile.EXTENSION);
    }

    static Path minecraftSaves(String osName, String home, String appData) {
        Path user = Path.of(home);
        String os = osName.toLowerCase(Locale.ROOT);
        try {
            if (os.contains("mac")) {
                return user.resolve("Library").resolve("Application Support").resolve("minecraft").resolve("saves");
            }
            if (os.contains("win")) {
                return appData == null || appData.isBlank() ? user : Path.of(appData, ".minecraft", "saves");
            }
            return user.resolve(".minecraft").resolve("saves");
        } catch (InvalidPathException e) {
            return user;
        }
    }

    static Path defaultWorldDirectory() {
        String home = System.getProperty("user.home", ".");
        Path saves = minecraftSaves(System.getProperty("os.name", ""), home, System.getenv("APPDATA"));
        return Files.isDirectory(saves) ? saves : Path.of(home);
    }

    private static String extension() {
        return ProjectFile.EXTENSION.substring(1);
    }

    private static Optional<Path> selected(JFileChooser chooser) {
        File file = chooser.getSelectedFile();
        if (file == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(file.toPath());
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private static JFileChooser chooser(Path directory) {
        JFileChooser chooser = new JFileChooser(directory == null ? null : directory.toFile());
        chooser.setFileView(new FileView() {
            @Override
            public String getName(File file) {
                String name = FileSystemView.getFileSystemView().getSystemDisplayName(file);
                if (name == null || name.isEmpty()) {
                    name = file.getName().isEmpty() ? file.getPath() : file.getName();
                }
                return SafeText.neutralize(name);
            }
        });
        return chooser;
    }
}
