package kelp;

import javax.swing.JFrame;
import javax.swing.SwingUtilities;

/** Kelp starts here. */
public class Kelp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kelp");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setContentPane(new OceanPanel());
            frame.setSize(1000, 640);
            frame.setLocationRelativeTo(null); // center on screen
            frame.setVisible(true);
        });
    }
}
