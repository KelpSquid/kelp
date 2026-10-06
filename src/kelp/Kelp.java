package kelp;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Kelp starts here. */
public class Kelp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kelp");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            try {
                frame.setIconImages(icons());
                frame.setContentPane(new OceanPanel());
            } catch (IllegalStateException e) {
                // Usually a missing texture: show what went wrong instead of silently closing
                JOptionPane.showMessageDialog(null, e.getMessage(), "Kelp", JOptionPane.ERROR_MESSAGE);
                System.exit(1);
            }
            frame.setSize(1000, 640);
            frame.setLocationRelativeTo(null); // center on screen
            frame.setVisible(true);
        });
    }

    /** The Kelp logo in several sizes, so Windows can pick a sharp one for the title bar and the taskbar. */
    private static List<Image> icons() {
        BufferedImage logo = Textures.read(new File("branding", "kelp.png"));
        List<Image> icons = new ArrayList<>();
        for (int size : new int[] {16, 20, 24, 32, 40, 48, 64, 128, 256}) icons.add(Textures.shrink(logo, size));
        return icons;
    }
}
