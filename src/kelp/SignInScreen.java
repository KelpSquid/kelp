package kelp;

import static kelp.Lang.t;

import java.awt.Desktop;
import java.awt.Graphics2D;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.net.URI;

/**
 * Signs in to a Microsoft account. Kelp shows a short code, you type it on Microsoft's page in your browser
 * and sign in there, and Kelp notices by itself when you're done.
 */
public class SignInScreen extends Screen {
    private enum Phase { NOT_READY, ASKING, WAITING, FINISHING, DONE, FAILED }

    private final Screen parent;
    private final McButton openButton = new McButton(t("Open Page"), this::openPage);
    private final McButton copyButton = new McButton(t("Copy Code"), this::copyCode);
    private final McButton backButton = new McButton(t("Cancel"), this::back);

    private volatile Phase phase;
    private volatile MicrosoftLogin.Code code;
    private volatile Account account;
    private volatile String error;
    private volatile boolean cancelled;
    private boolean copied;

    public SignInScreen(OceanPanel panel, Screen parent) {
        super(panel);
        this.parent = parent;
        buttons.add(openButton);
        buttons.add(copyButton);
        buttons.add(backButton);
        if (!MicrosoftLogin.ready()) {
            phase = Phase.NOT_READY;
            return;
        }
        phase = Phase.ASKING;
        Thread worker = new Thread(() -> {
            try {
                MicrosoftLogin login = new MicrosoftLogin();
                code = login.start();
                phase = Phase.WAITING;
                Account signedIn = login.waitForSignIn(code, () -> cancelled);
                if (signedIn == null || cancelled) return; // Cancel was clicked
                phase = Phase.FINISHING;
                Accounts.add(signedIn);
                account = signedIn;
                phase = Phase.DONE;
            } catch (Exception e) {
                error = e.getMessage() != null ? e.getMessage() : e.toString();
                phase = Phase.FAILED;
            }
        }, "microsoft sign-in");
        worker.setDaemon(true);
        worker.start();
    }

    private void openPage() {
        try {
            Desktop.getDesktop().browse(URI.create(code.page()));
        } catch (Exception e) {
            error = t("Couldn't open your browser. Go to {0} yourself.", code.page());
        }
    }

    private void copyCode() {
        try {
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(code.userCode()), null);
            copied = true;
        } catch (Exception e) {
            // no clipboard: the code is on screen to type instead
        }
    }

    private void back() {
        cancelled = true; // stops the waiting
        panel.setScreen(parent);
    }

    @Override
    public void draw(Graphics2D g, int w, int h) {
        McFont font = panel.getMcFont();
        int centerY = h / 2;
        centered(g, t("Add Microsoft"), w, 12 * GUI, 0xFFFFFF);
        boolean waiting = phase == Phase.WAITING;
        openButton.setActive(waiting);
        copyButton.setActive(waiting);
        backButton.setLabel(phase == Phase.DONE ? t("Done") : phase == Phase.FAILED || phase == Phase.NOT_READY ? t("Back") : t("Cancel"));

        switch (phase) {
            case NOT_READY -> {
                centered(g, t("Microsoft sign-in isn't ready yet."), w, centerY - 40 * GUI, 0xFFFF55);
                centered(g, t("Mojang has to approve Kelp first."), w, centerY - 26 * GUI, 0xA0A0A0);
                centered(g, t("Until then, use an offline name."), w, centerY - 14 * GUI, 0xA0A0A0);
            }
            case ASKING -> centered(g, t("Asking Microsoft for a code..."), w, centerY - 40 * GUI, 0xFFFFFF);
            case WAITING -> {
                MicrosoftLogin.Code c = code;
                String page = c.page().replaceFirst("^https?://(www\\.)?", "");
                centered(g, t("1. Go to {0}", page), w, centerY - 70 * GUI, 0xFFFFFF);
                centered(g, t("2. Type this code:"), w, centerY - 56 * GUI, 0xFFFFFF);
                int big = 4 * GUI; // the code is big, so it's easy to read
                font.draw(g, c.userCode(), (w - font.width(c.userCode(), big)) / 2, centerY - 42 * GUI, big, 0xFFFF55);
                centered(g, t("3. Sign in. Kelp notices by itself!"), w, centerY - 4 * GUI, 0xFFFFFF);
                if (error != null) centered(g, error, w, centerY + 10 * GUI, 0xFF5555);
                else if (copied) centered(g, t("Copied! Paste it on the page."), w, centerY + 10 * GUI, 0x55FF55);
            }
            case FINISHING -> centered(g, t("Signing in to Minecraft..."), w, centerY - 40 * GUI, 0xFFFFFF);
            case DONE -> {
                centered(g, t("Signed in as {0}!", account.name()), w, centerY - 40 * GUI, 0x55FF55);
                centered(g, t("Kelp will play as this account now."), w, centerY - 26 * GUI, 0xA0A0A0);
            }
            case FAILED -> {
                centered(g, t("Couldn't sign in:"), w, centerY - 50 * GUI, 0xFF5555);
                int y = centerY - 36 * GUI;
                for (String line : wrap(font, String.valueOf(error), 300 * GUI)) {
                    centered(g, line, w, y, 0xFFFFFF);
                    y += 12 * GUI;
                }
            }
        }

        int y = centerY + 26 * GUI;
        openButton.setBounds(w / 2 - 100 * GUI, y, 98 * GUI, 20 * GUI);
        copyButton.setBounds(w / 2 + 2 * GUI, y, 98 * GUI, 20 * GUI);
        backButton.setBounds(w / 2 - 100 * GUI, y + 24 * GUI, 200 * GUI, 20 * GUI);
        if (waiting) {
            openButton.draw(g, font, GUI);
            copyButton.draw(g, font, GUI);
        }
        backButton.draw(g, font, GUI);
    }

    @Override
    public void mousePressed(int x, int y) {
        if (phase != Phase.WAITING && (openButton.contains(x, y) || copyButton.contains(x, y))) return; // they're hidden
        super.mousePressed(x, y);
    }

    /** Splits text into lines that fit the width, breaking between words. */
    private static java.util.List<String> wrap(McFont font, String text, int maxWidth) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        String line = "";
        for (String word : text.split(" ")) {
            String longer = line.isEmpty() ? word : line + " " + word;
            if (!line.isEmpty() && font.width(longer, GUI) > maxWidth) {
                lines.add(line);
                line = word;
            } else {
                line = longer;
            }
        }
        if (!line.isEmpty()) lines.add(line);
        return lines;
    }
}
