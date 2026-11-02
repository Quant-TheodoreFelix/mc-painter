package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.Font;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextArea;
import javax.swing.UIManager;

final class SafeText {

    static final String HTML_DISABLE = "html.disable";
    static final int MESSAGE_COLUMNS = 52;

    private SafeText() {
    }

    static String neutralize(String text) {
        if (text == null) {
            return "";
        }
        StringBuilder out = new StringBuilder(text.length() + 1);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            out.append(isUnsafe(c) ? '�' : c);
        }
        if (out.length() > 0 && out.charAt(0) == '<') {
            out.insert(0, ' ');
        }
        return out.toString();
    }

    private static boolean isUnsafe(char c) {
        if (c == '\n' || c == '\t') {
            return false;
        }
        if (Character.isISOControl(c)) {
            return true;
        }
        return c == '؜' || c == '‎' || c == '‏' || (c >= '‪' && c <= '‮')
                || (c >= '⁦' && c <= '⁩');
    }

    static <T extends JComponent> T plain(T component) {
        component.putClientProperty(HTML_DISABLE, Boolean.TRUE);
        return component;
    }

    static JLabel label(String text) {
        JLabel label = plain(new JLabel());
        label.setText(neutralize(text));
        return label;
    }

    static void setText(JLabel label, String text) {
        plain(label);
        label.setText(neutralize(text));
    }

    static JTextArea message(String text) {
        JTextArea area = new JTextArea(neutralize(text));
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setOpaque(false);
        area.setBorder(null);
        Font font = UIManager.getFont("Label.font");
        if (font != null) {
            area.setFont(font);
        }
        area.setColumns(MESSAGE_COLUMNS);
        area.setSize(area.getPreferredSize().width, Short.MAX_VALUE);
        area.setSize(area.getPreferredSize());
        return area;
    }
}
