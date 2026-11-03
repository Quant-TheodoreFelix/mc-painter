package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Predicate;
import javax.swing.DefaultListModel;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.ListSelectionModel;
import space.qu4nt.thirdparty.mcpainter.io.Workspace;

final class RestoreForm {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm", Locale.ROOT)
            .withZone(ZoneId.systemDefault());

    private RestoreForm() {
    }

    static Optional<Workspace.Summary> choose(Component parent, Messages messages, List<Workspace.Summary> candidates,
            Predicate<Workspace.Summary> delete) {
        DefaultListModel<Workspace.Summary> model = new DefaultListModel<>();
        candidates.forEach(model::addElement);
        JList<Workspace.Summary> list = SafeText.plain(new JList<>(model));
        list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        list.setCellRenderer(Renderers.withIcon(summary -> describe(messages, summary), summary -> null));
        list.setSelectedIndex(0);
        list.setVisibleRowCount(Math.min(8, Math.max(3, candidates.size())));
        JScrollPane scroll = new JScrollPane(list);
        scroll.setPreferredSize(new Dimension(620, scroll.getPreferredSize().height));
        JPanel panel = new JPanel(new BorderLayout(0, 8));
        panel.add(SafeText.message(messages.text("restore.hint")), BorderLayout.NORTH);
        panel.add(scroll, BorderLayout.CENTER);
        Object[] options = {messages.text("restore.open"), messages.text("dialog.delete"), messages.text("dialog.cancel")};
        while (!model.isEmpty()) {
            int result = JOptionPane.showOptionDialog(parent, panel, messages.text("restore.title"),
                    JOptionPane.DEFAULT_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
            Workspace.Summary selected = list.getSelectedValue();
            if (result == 0 && selected != null) {
                if (selected.locked()) {
                    Dialogs.error(parent, messages, messages.text("restore.locked"), "");
                    continue;
                }
                return Optional.of(selected);
            }
            if (result == 1 && selected != null) {
                if (delete.test(selected)) {
                    model.removeElement(selected);
                    list.setSelectedIndex(0);
                }
                continue;
            }
            if (result != 0) {
                return Optional.empty();
            }
        }
        return Optional.empty();
    }

    static String title(Messages messages, Workspace.Summary summary) {
        String name = summary.state().name().isBlank() ? messages.text("app.untitled") : summary.state().name();
        return name + "  " + TIME.format(Instant.ofEpochMilli(summary.state().modified()));
    }

    static String describe(Messages messages, Workspace.Summary summary) {
        String project = summary.state().project().map(Object::toString).orElse(messages.text("restore.noProject"));
        String status = summary.locked() ? messages.text("restore.inUse")
                : summary.state().unsaved() ? messages.text("restore.unsaved") : messages.text("restore.saved");
        return messages.text("restore.item", title(messages, summary), Forms.number(summary.tiles()), project,
                status);
    }
}
