package kelp;

import java.awt.Desktop;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Opens a mod's code for editing: in VS Code if it's installed (it colors the code), or else a plain text editor. */
public final class Editors {
    private Editors() {
    }

    public static void open(Path file) throws IOException {
        Path vsCode = vsCode();
        if (vsCode != null) {
            start(List.of(vsCode.toString(), file.toString()));
            return;
        }
        if (Rules.osName().equals("osx") && Files.isDirectory(Path.of("/Applications/Visual Studio Code.app"))) {
            start(List.of("open", "-a", "Visual Studio Code", file.toString()));
            return;
        }
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.EDIT)) {
                Desktop.getDesktop().edit(file.toFile());
                return;
            }
        } catch (IOException noEditorForJava) {
            // fall back to the computer's own plain text editor below
        }
        if (Rules.osName().equals("windows")) start(List.of("notepad.exe", file.toString()));
        else if (Rules.osName().equals("osx")) start(List.of("open", "-t", file.toString()));
        else start(List.of("xdg-open", file.toString()));
    }

    /** VS Code on Windows, installed just for this user or for everyone. Null if it isn't there. */
    private static Path vsCode() {
        if (!Rules.osName().equals("windows")) return null;
        String local = System.getenv("LOCALAPPDATA");
        for (Path candidate : new Path[] {
                local == null ? null : Path.of(local, "Programs", "Microsoft VS Code", "Code.exe"),
                Path.of("C:\\Program Files\\Microsoft VS Code\\Code.exe")}) {
            if (candidate != null && Files.exists(candidate)) return candidate;
        }
        return null;
    }

    private static void start(List<String> command) throws IOException {
        new ProcessBuilder(command).start();
    }
}
