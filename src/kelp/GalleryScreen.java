package kelp;

import static kelp.Lang.t;

import javax.imageio.ImageIO;
import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * An instance's screenshots (F2 in the game) and video clips (F8, with Squid), newest first, as a grid of small
 * pictures. Click one to see it big, then go through them, open one (clips play in the computer's video player), or
 * delete it.
 */
public class GalleryScreen extends Screen {
    private static final int THUMB_W = 96;
    private static final int THUMB_H = 54;

    private final Screen parent;
    private final Instance instance;
    private final McButton doneButton = new McButton(t("Done"), this::done);
    private final McButton folderButton = new McButton(t("Open Folder"), this::openFolder);
    private final McButton previousButton = new McButton("<", () -> step(-1));
    private final McButton nextButton = new McButton(">", () -> step(1));
    private final McButton openButton = new McButton(t("Open"), this::openPicture);
    private final McButton deleteButton = new McButton(t("Delete"), this::deletePicture);
    private final McButton themeButton = new McButton(t("Use as Theme"), this::useAsTheme);
    private final Map<Path, BufferedImage> thumbnails = new ConcurrentHashMap<>();
    private final Map<Path, BufferedImage> big = new ConcurrentHashMap<>();
    private List<Path> pictures;
    private int page;
    private int viewing = -1; // which picture is shown big, or -1 for the grid
    private String message;
    private String notice; // good news, in green

    public GalleryScreen(OceanPanel panel, Screen parent, Instance instance) {
        super(panel);
        this.parent = parent;
        this.instance = instance;
        for (McButton b : new McButton[] {doneButton, folderButton, previousButton, nextButton, openButton, deleteButton, themeButton}) buttons.add(b);
        pictures = list(instance);
        Thread loader = new Thread(() -> {
            for (Path picture : new ArrayList<>(pictures)) thumbnails.computeIfAbsent(picture, GalleryScreen::thumbnail);
        }, "gallery thumbnails");
        loader.setDaemon(true);
        loader.start();
    }

    private Path folder() {
        return instance.folder().resolve("screenshots");
    }

    /** An instance's screenshots and clips together, newest first. */
    static List<Path> list(Instance instance) {
        List<Path> all = list(instance.folder().resolve("screenshots"));
        Path clips = instance.folder().resolve("clips");
        if (Files.isDirectory(clips)) {
            try (Stream<Path> files = Files.list(clips)) {
                files.filter(GalleryScreen::isClip).forEach(all::add);
            } catch (IOException e) {
                // can't look: just the screenshots
            }
        }
        all.sort(Comparator.comparingLong(GalleryScreen::modified).reversed());
        return all;
    }

    static boolean isClip(Path file) {
        return file.getFileName().toString().toLowerCase().endsWith(".avi");
    }

    /** A clip's picture, saved next to it with the same name. */
    static Path still(Path file) {
        if (!isClip(file)) return file;
        String name = file.getFileName().toString();
        return file.resolveSibling(name.substring(0, name.length() - 4) + ".jpg");
    }

    /** The pictures in a folder, newest first. */
    static List<Path> list(Path folder) {
        if (!Files.isDirectory(folder)) return new ArrayList<>();
        try (Stream<Path> files = Files.list(folder)) {
            return new ArrayList<>(files.filter(f -> f.toString().toLowerCase().matches(".*\\.(png|jpe?g)"))
                    .sorted(Comparator.comparingLong(GalleryScreen::modified).reversed()).toList());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    private static long modified(Path file) {
        try {
            return Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            return 0;
        }
    }

    /** A small copy of a picture for the grid. */
    static BufferedImage thumbnail(Path picture) {
        try {
            BufferedImage full = ImageIO.read(still(picture).toFile());
            if (full == null) return null;
            BufferedImage small = new BufferedImage(THUMB_W * 2, THUMB_H * 2, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = small.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(full, 0, 0, small.getWidth(), small.getHeight(), null);
            g.dispose();
            return small;
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    private void done() {
        if (viewing >= 0) viewing = -1;
        else panel.setScreen(parent);
    }

    private void step(int by) {
        notice = null;
        if (pictures.isEmpty()) return;
        viewing = Math.floorMod(viewing + by, pictures.size());
    }

    private void openFolder() {
        try {
            Files.createDirectories(folder());
            Desktop.getDesktop().open(folder().toFile());
        } catch (IOException e) {
            message = t("Couldn't open the folder: {0}", e.getMessage());
        }
    }

    private void openPicture() {
        try {
            Desktop.getDesktop().open(pictures.get(viewing).toFile());
        } catch (IOException | RuntimeException e) {
            message = t("Couldn't open it: {0}", e.getMessage());
        }
    }

    /**
     * Makes Kelp's background this picture: a theme called "My Screenshot" with the theme in use's scene, colors and
     * music, and this picture behind it. Using another picture later replaces it.
     */
    private void useAsTheme() {
        try {
            Theme now = Theme.current();
            Theme made = Theme.save("screenshot", t("My Screenshot"), now.scene(), now.water(), now.base(), now.buttons(), still(pictures.get(viewing)), now.music());
            Theme.use(made);
            message = null;
            notice = t("Kelp's background is this picture now. Themes (in Options) can change it back.");
        } catch (IOException | RuntimeException e) {
            message = t("Couldn't make the theme: {0}", e.getMessage());
        }
    }

    private void deletePicture() {
        Path picture = pictures.get(viewing);
        panel.setScreen(new ConfirmScreen(panel, t("Delete {0}?", picture.getFileName()), t("It will be gone forever!"), () -> {
            try {
                Files.deleteIfExists(picture);
                if (isClip(picture)) Files.deleteIfExists(still(picture));
                pictures.remove(picture);
                viewing = pictures.isEmpty() ? -1 : Math.min(viewing, pictures.size() - 1);
            } catch (IOException e) {
                message = t("Couldn't delete it: {0}", e.getMessage());
            }
            panel.setScreen(this);
        }, () -> panel.setScreen(this)));
    }

    private int columns(int w) {
        return Math.max(1, (w - 20 * GUI) / ((THUMB_W + 6) * GUI));
    }

    private int rows(int h) {
        return Math.max(1, (h - 90 * GUI) / ((THUMB_H + 6) * GUI));
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        boolean grid = viewing < 0;
        for (McButton b : new McButton[] {previousButton, nextButton, openButton, deleteButton, themeButton}) b.setBounds(-1000, -1000, 0, 0);
        folderButton.setBounds(-1000, -1000, 0, 0);
        if (grid) {
            centered(g, t("Gallery of {0}", instance.name()), w, 12 * GUI, 0xFFFFFF);
            if (pictures.isEmpty()) centered(g, t("No screenshots or clips yet. Press F2 (or F8 for a clip) while playing!"), w, h / 2 - 10 * GUI, 0xA0A0A0);
            int columns = columns(w);
            int perPage = columns * rows(h);
            page = Math.min(page, Math.max(0, (pictures.size() - 1) / perPage));
            int left = (w - columns * (THUMB_W + 6) * GUI) / 2;
            for (int i = page * perPage; i < Math.min(pictures.size(), (page + 1) * perPage); i++) {
                int x = left + ((i - page * perPage) % columns) * (THUMB_W + 6) * GUI;
                int y = 32 * GUI + ((i - page * perPage) / columns) * (THUMB_H + 6) * GUI;
                BufferedImage thumb = thumbnails.get(pictures.get(i));
                g.setColor(new java.awt.Color(0, 0, 0, 120));
                g.fillRect(x, y, THUMB_W * GUI, THUMB_H * GUI);
                if (thumb != null) g.drawImage(thumb, x, y, THUMB_W * GUI, THUMB_H * GUI, null);
                if (isClip(pictures.get(i))) font.draw(g, t("CLIP"), x + 3 * GUI, y + 3 * GUI, GUI, 0xFFFF55);
            }
            if (pictures.size() > perPage) {
                centered(g, (page + 1) + " / " + ((pictures.size() - 1) / perPage + 1) + "   " + t("Scroll for more"), w, h - 44 * GUI, 0xA0A0A0);
            }
            folderButton.setBounds(w / 2 - 100 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
            doneButton.setBounds(w / 2 + 2 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        } else {
            Path picture = pictures.get(viewing);
            centered(g, isClip(picture) ? t("{0}: Open plays it", picture.getFileName()) : picture.getFileName().toString(), w, 8 * GUI, 0xFFFFFF);
            BufferedImage image = big.computeIfAbsent(picture, p -> {
                try {
                    BufferedImage read = ImageIO.read(still(p).toFile());
                    return read != null ? read : new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
                } catch (IOException e) {
                    return new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
                }
            });
            if (big.size() > 3) big.keySet().removeIf(p -> !p.equals(picture)); // big pictures take a lot of memory
            int maxW = w - 20 * GUI;
            int maxH = h - 68 * GUI;
            double scale = Math.min(maxW / (double) image.getWidth(), maxH / (double) image.getHeight());
            int iw = (int) (image.getWidth() * scale);
            int ih = (int) (image.getHeight() * scale);
            Graphics2D smooth = (Graphics2D) g.create();
            smooth.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            smooth.drawImage(image, (w - iw) / 2, 28 * GUI, iw, ih, null); // under the title and Use as Theme
            smooth.dispose();
            int y = h - 28 * GUI;
            previousButton.setBounds(w / 2 - 152 * GUI, y, 20 * GUI, 20 * GUI);
            openButton.setBounds(w / 2 - 128 * GUI, y, 80 * GUI, 20 * GUI);
            deleteButton.setBounds(w / 2 - 44 * GUI, y, 80 * GUI, 20 * GUI);
            doneButton.setBounds(w / 2 + 40 * GUI, y, 88 * GUI, 20 * GUI);
            nextButton.setBounds(w / 2 + 132 * GUI, y, 20 * GUI, 20 * GUI);
            themeButton.setBounds(w - 84 * GUI, 4 * GUI, 80 * GUI, 20 * GUI); // top-right, clear of the picture
        }
        if (message != null) centered(g, message, w, h - 40 * GUI, 0xFF5555);
        else if (notice != null && !grid) centered(g, notice, w, h - 40 * GUI, 0x55FF55);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mousePressed(int x, int y) {
        if (viewing < 0) {
            int w = panel.getWidth();
            int h = panel.getHeight();
            int columns = columns(w);
            int perPage = columns * rows(h);
            int left = (w - columns * (THUMB_W + 6) * GUI) / 2;
            for (int i = page * perPage; i < Math.min(pictures.size(), (page + 1) * perPage); i++) {
                int tx = left + ((i - page * perPage) % columns) * (THUMB_W + 6) * GUI;
                int ty = 32 * GUI + ((i - page * perPage) / columns) * (THUMB_H + 6) * GUI;
                if (x >= tx && x < tx + THUMB_W * GUI && y >= ty && y < ty + THUMB_H * GUI) {
                    viewing = i;
                    return;
                }
            }
        }
        super.mousePressed(x, y);
    }

    @Override
    public void mouseWheel(int x, int y, int notches) {
        if (viewing >= 0) {
            step(notches);
            return;
        }
        int perPage = columns(panel.getWidth()) * rows(panel.getHeight());
        int pages = Math.max(1, (pictures.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(pages - 1, page + notches));
    }
}
