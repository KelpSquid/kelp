package kelp;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;

/** Kelp starts here. */
public class Kelp {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Kelp");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            try {
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
}
