package kelp;

import static kelp.Lang.t;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The emblem editor, Call of Duty style. The emblem is on the left: drag on it to move the picked layer. Next to it,
 * the layers (top one first), and the tools for the picked layer: its shape, color, size, turn and flip. More tools,
 * shapes, colors and layers unlock as the player's Squid Count grows; locked ones say how many points they need.
 */
public class EmblemScreen extends Screen {
    private static final int[] MORE_COLORS = {0x8C5A32, 0xF0A0C8, 0x46C8DC, 0xA0E050, 0xC0C0C0, 0x7A0F0F, 0x103C78, 0xFFE68C};

    private final Screen parent;
    private final Account account;
    private final int points;
    private final Emblem emblem;
    private int selected;
    private String message;
    private boolean dragging;

    private final McButton shapeButton = new McButton("", this::nextShape);
    private final McButton flipXButton = new McButton("", () -> flip(true));
    private final McButton flipYButton = new McButton("", () -> flip(false));
    private final McButton addButton = new McButton(t("Add"), this::addLayer);
    private final McButton deleteButton = new McButton(t("Delete"), this::deleteLayer);
    private final McButton upButton = new McButton(t("Up"), () -> move(1));
    private final McButton downButton = new McButton(t("Down"), () -> move(-1));
    private final McButton saveButton = new McButton(t("Save"), this::save);
    private final McButton cancelButton = new McButton(t("Cancel"), this::cancel);
    private final McSlider sizeSlider;
    private final McSlider turnSlider;
    private final McSlider hueSlider;
    private final List<McSlider> sliders = new ArrayList<>();

    // Where things are, worked out while drawing, for the mouse
    private int canvasX;
    private int canvasY;
    private int canvasSize;
    private int listX;
    private int listY;
    private int listW;
    private int rowH;
    private int paletteX;
    private int paletteY;
    private int swatch;

    public EmblemScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        this.account = Accounts.active();
        this.points = SquidCount.points(account.id());
        Emblem saved = Emblem.load(account.id());
        this.emblem = saved != null ? saved : Emblem.starter();
        selected = emblem.layers.size() - 1;
        sizeSlider = new McSlider(5, 200, 1, 100, v -> t("Size: {0}%", (int) v), v -> {
            Emblem.Layer layer = layer();
            if (layer != null) layer.size = v / 100;
        });
        turnSlider = new McSlider(0, 359, 1, 0, v -> points >= Emblem.UNLOCKS.TURN ? t("Turn: {0}°", (int) v)
                : t("Turn: unlocks at {0} Squid Count", Emblem.UNLOCKS.TURN), v -> {
            Emblem.Layer layer = layer();
            if (layer != null && points >= Emblem.UNLOCKS.TURN) layer.turn = v;
        });
        hueSlider = new McSlider(0, 359, 1, 0, v -> points >= Emblem.UNLOCKS.ALL_COLORS ? t("Any color: {0}", (int) v)
                : t("Any color: unlocks at {0} Squid Count", Emblem.UNLOCKS.ALL_COLORS), v -> {
            Emblem.Layer layer = layer();
            if (layer != null && points >= Emblem.UNLOCKS.ALL_COLORS) layer.color = Color.HSBtoRGB((float) (v / 360), 0.8f, 0.95f) & 0xFFFFFF;
        });
        sliders.add(sizeSlider);
        sliders.add(turnSlider);
        sliders.add(hueSlider);
        for (McButton b : new McButton[] {shapeButton, flipXButton, flipYButton, addButton, deleteButton, upButton, downButton, saveButton, cancelButton}) {
            buttons.add(b);
        }
        pick(selected);
    }

    private Emblem.Layer layer() {
        return selected >= 0 && selected < emblem.layers.size() ? emblem.layers.get(selected) : null;
    }

    /** Picks a layer to change, and shows its values on the tools. */
    private void pick(int index) {
        selected = Math.max(-1, Math.min(emblem.layers.size() - 1, index));
        Emblem.Layer layer = layer();
        if (layer == null) return;
        sizeSlider.setValue(layer.size * 100);
        turnSlider.setValue(((layer.turn % 360) + 360) % 360);
    }

    private void nextShape() {
        Emblem.Layer layer = layer();
        if (layer == null) return;
        Emblem.Shape[] all = Emblem.Shape.values();
        for (int i = 1; i <= all.length; i++) {
            Emblem.Shape next = all[(layer.shape.ordinal() + i) % all.length];
            if (next.points <= points) {
                layer.shape = next;
                return;
            }
        }
    }

    private void flip(boolean across) {
        Emblem.Layer layer = layer();
        if (layer == null) return;
        if (points < Emblem.UNLOCKS.FLIP) {
            message = t("Flipping unlocks at {0} Squid Count.", Emblem.UNLOCKS.FLIP);
            return;
        }
        if (across) layer.flipX = !layer.flipX;
        else layer.flipY = !layer.flipY;
    }

    private void addLayer() {
        int most = Emblem.UNLOCKS.layers(points);
        if (emblem.layers.size() >= most) {
            message = t("You can have {0} layers. Earn more Squid Count for more!", most);
            return;
        }
        emblem.layers.add(selected + 1, new Emblem.Layer(Emblem.Shape.CIRCLE, Emblem.BASIC_COLORS[(emblem.layers.size() + 2) % 8], 0.5, 0.5, 0.5, 0, false, false));
        pick(selected + 1);
    }

    private void deleteLayer() {
        if (layer() == null) return;
        emblem.layers.remove(selected);
        pick(Math.min(selected, emblem.layers.size() - 1));
    }

    /** Moves the picked layer up (in front) or down (behind) one. */
    private void move(int by) {
        int to = selected + by;
        if (layer() == null || to < 0 || to >= emblem.layers.size()) return;
        Emblem.Layer layer = emblem.layers.remove(selected);
        emblem.layers.add(to, layer);
        selected = to;
    }

    private void cancel() {
        panel.setScreen(parent);
    }

    private void save() {
        try {
            emblem.save(account.id());
            panel.setScreen(parent);
        } catch (IOException e) {
            message = t("Couldn't save it: {0}", e.getMessage());
        }
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        centered(g, t("Emblem"), w, 10 * GUI, 0xFFFFFF);
        centered(g, t("Squid Count: {0}", points), w, 22 * GUI, 0xFFFF55);
        int left = w / 2 - 200 * GUI;
        int top = 46 * GUI;

        // The emblem, big, on a checkerboard so see-through parts show
        canvasX = left;
        canvasY = top;
        canvasSize = 112 * GUI;
        int cell = canvasSize / 8;
        for (int i = 0; i < 8; i++) {
            for (int j = 0; j < 8; j++) {
                g.setColor((i + j) % 2 == 0 ? new Color(60, 66, 78) : new Color(48, 52, 62));
                g.fillRect(canvasX + i * cell, canvasY + j * cell, cell, cell);
            }
        }
        g.drawImage(emblem.draw(), canvasX, canvasY, canvasSize, canvasSize, null);
        Emblem.Layer layer = layer();
        if (layer != null) { // a little cross on the picked layer's middle
            int cx = canvasX + (int) (layer.x * canvasSize);
            int cy = canvasY + (int) (layer.y * canvasSize);
            g.setColor(Color.WHITE);
            g.fillRect(cx - 3 * GUI, cy, 7 * GUI, GUI);
            g.fillRect(cx, cy - 3 * GUI, GUI, 7 * GUI);
        }
        int y = canvasY + canvasSize + 6 * GUI;
        addButton.setBounds(left, y, 57 * GUI, 20 * GUI);
        deleteButton.setBounds(left + 59 * GUI, y, 57 * GUI, 20 * GUI);
        upButton.setBounds(left, y + 22 * GUI, 57 * GUI, 20 * GUI);
        downButton.setBounds(left + 59 * GUI, y + 22 * GUI, 57 * GUI, 20 * GUI);

        // The layers, the front one at the top
        listX = left + 118 * GUI;
        listY = top;
        listW = 80 * GUI;
        rowH = 14 * GUI;
        font.draw(g, t("Layers: {0} of {1}", emblem.layers.size(), Emblem.UNLOCKS.layers(points)), listX, top - 10 * GUI, GUI, 0xA0A0A0);
        for (int row = 0; row < emblem.layers.size() && row < 11; row++) {
            int index = emblem.layers.size() - 1 - row;
            Emblem.Layer l = emblem.layers.get(index);
            int ry = listY + row * rowH;
            g.setColor(index == selected ? new Color(255, 255, 255, 70) : new Color(0, 0, 0, 90));
            g.fillRect(listX, ry, listW, rowH - GUI);
            g.setColor(new Color(l.color & 0xFFFFFF));
            g.fillRect(listX + 2 * GUI, ry + 2 * GUI, 9 * GUI, 9 * GUI);
            font.draw(g, t(l.shape.label), listX + 14 * GUI, ry + 3 * GUI, GUI, 0xFFFFFF);
        }

        // The tools for the picked layer
        int tx = left + 204 * GUI;
        int tw = 196 * GUI;
        boolean has = layer != null;
        shapeButton.setLabel(has ? t("Shape: {0}", t(layer.shape.label)) : t("Shape"));
        shapeButton.setBounds(tx, top, tw, 20 * GUI);
        shapeButton.setActive(has);
        paletteX = tx;
        paletteY = top + 24 * GUI;
        swatch = tw / 8;
        for (int i = 0; i < 16; i++) {
            int color = i < 8 ? Emblem.BASIC_COLORS[i] : MORE_COLORS[i - 8];
            boolean locked = i >= 8 && points < Emblem.UNLOCKS.ALL_COLORS;
            int sx = paletteX + (i % 8) * swatch;
            int sy = paletteY + (i / 8) * swatch / 2;
            g.setColor(new Color(locked ? 0x404040 : color));
            g.fillRect(sx + GUI, sy + GUI, swatch - 2 * GUI, swatch / 2 - 2 * GUI);
            if (has && !locked && (layer.color & 0xFFFFFF) == color) {
                g.setColor(Color.WHITE);
                g.drawRect(sx, sy, swatch - 1, swatch / 2 - 1);
            }
        }
        int sy = paletteY + swatch + 4 * GUI;
        hueSlider.setBounds(tx, sy, tw, 20 * GUI);
        sizeSlider.setBounds(tx, sy + 24 * GUI, tw, 20 * GUI);
        turnSlider.setBounds(tx, sy + 48 * GUI, tw, 20 * GUI);
        for (McSlider s : sliders) s.draw(g, font, GUI);
        boolean flips = points >= Emblem.UNLOCKS.FLIP;
        flipXButton.setLabel(flips ? t("Flip Across") : t("Flip: {0} pts", Emblem.UNLOCKS.FLIP));
        flipYButton.setLabel(flips ? t("Flip Down") : t("Flip: {0} pts", Emblem.UNLOCKS.FLIP));
        flipXButton.setBounds(tx, sy + 72 * GUI, tw / 2 - GUI, 20 * GUI);
        flipYButton.setBounds(tx + tw / 2 + GUI, sy + 72 * GUI, tw / 2 - GUI, 20 * GUI);
        flipXButton.setActive(has && flips);
        flipYButton.setActive(has && flips);
        deleteButton.setActive(has);
        upButton.setActive(has && selected < emblem.layers.size() - 1);
        downButton.setActive(has && selected > 0);

        if (message != null) centered(g, message, w, h - 40 * GUI, 0xFFFF55);
        else centered(g, t("Drag on the emblem to move a layer. Earn Squid Count to unlock more."), w, h - 40 * GUI, 0x808080);
        saveButton.setBounds(w / 2 - 100 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        cancelButton.setBounds(w / 2 + 2 * GUI, h - 28 * GUI, 98 * GUI, 20 * GUI);
        for (McButton b : buttons) b.draw(g, font, GUI);
    }

    @Override
    public void mouseMoved(int x, int y) {
        super.mouseMoved(x, y);
        for (McSlider s : sliders) s.mouseMoved(x, y);
    }

    @Override
    public void mousePressed(int x, int y) {
        message = null;
        if (x >= canvasX && x < canvasX + canvasSize && y >= canvasY && y < canvasY + canvasSize && layer() != null) {
            dragging = true;
            moveLayerTo(x, y);
            return;
        }
        if (x >= listX && x < listX + listW && y >= listY) {
            int row = (y - listY) / rowH;
            if (row < emblem.layers.size() && row < 11) {
                pick(emblem.layers.size() - 1 - row);
                return;
            }
        }
        if (x >= paletteX && x < paletteX + swatch * 8 && y >= paletteY && y < paletteY + swatch && layer() != null) {
            int i = (x - paletteX) / swatch + ((y - paletteY) / (swatch / 2)) * 8;
            if (i >= 8 && points < Emblem.UNLOCKS.ALL_COLORS) {
                message = t("More colors unlock at {0} Squid Count.", Emblem.UNLOCKS.ALL_COLORS);
            } else if (i < 16) {
                layer().color = i < 8 ? Emblem.BASIC_COLORS[i] : MORE_COLORS[i - 8];
            }
            return;
        }
        if (turnSlider.mousePressed(x, y)) {
            if (points < Emblem.UNLOCKS.TURN) message = t("Turning unlocks at {0} Squid Count.", Emblem.UNLOCKS.TURN);
            return;
        }
        for (McSlider s : sliders) {
            if (s != turnSlider && s.mousePressed(x, y)) return;
        }
        super.mousePressed(x, y);
    }

    private void moveLayerTo(int x, int y) {
        Emblem.Layer layer = layer();
        if (layer == null) return;
        layer.x = Math.max(0, Math.min(1, (x - canvasX) / (double) canvasSize));
        layer.y = Math.max(0, Math.min(1, (y - canvasY) / (double) canvasSize));
    }

    @Override
    public void mouseDragged(int x, int y) {
        if (dragging) {
            moveLayerTo(x, y);
            return;
        }
        for (McSlider s : sliders) s.mouseDragged(x, y);
    }

    @Override
    public void mouseReleased(int x, int y) {
        dragging = false;
        for (McSlider s : sliders) s.mouseReleased();
    }

    /** A small copy of an account's emblem, for next to their name (or nothing if they haven't made one). */
    static void drawSmall(Graphics2D g, String accountId, int x, int y, int size) {
        BufferedImage picture = Emblem.picture(accountId);
        if (picture != null) g.drawImage(picture, x, y, size, size, null);
    }
}
