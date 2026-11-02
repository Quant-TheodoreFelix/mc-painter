package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;
import java.util.function.Function;
import javax.swing.DefaultListCellRenderer;
import javax.swing.Icon;
import javax.swing.ListCellRenderer;

final class Renderers {

    private Renderers() {
    }

    record Swatch(int rgb) implements Icon {

        static final int SIZE = 14;

        @Override
        public void paintIcon(Component c, Graphics g, int x, int y) {
            g.setColor(new Color(rgb));
            g.fillRect(x, y, SIZE, SIZE);
            g.setColor(Color.DARK_GRAY);
            g.drawRect(x, y, SIZE - 1, SIZE - 1);
        }

        @Override
        public int getIconWidth() {
            return SIZE;
        }

        @Override
        public int getIconHeight() {
            return SIZE;
        }
    }

    static <T> ListCellRenderer<T> text(Function<? super T, String> label) {
        return withIcon(label, value -> null);
    }

    static <T> ListCellRenderer<T> withIcon(Function<? super T, String> label, Function<? super T, Icon> icon) {
        DefaultListCellRenderer base = SafeText.plain(new DefaultListCellRenderer());
        return (list, value, index, selected, focused) -> {
            String text = value == null ? "" : SafeText.neutralize(label.apply(value));
            Component component = base.getListCellRendererComponent(list, text, index, selected, focused);
            base.setIcon(value == null ? null : icon.apply(value));
            return component;
        };
    }
}
