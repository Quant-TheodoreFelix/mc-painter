package space.qu4nt.thirdparty.mcpainter.ui;

import java.awt.Component;
import javax.swing.JComponent;
import javax.swing.JOptionPane;

final class Dialogs {

    enum Choice {
        SAVE,
        DISCARD,
        CANCEL
    }

    private Dialogs() {
    }

    static void error(Component parent, Messages messages, String headlineKey, Throwable error) {
        error(parent, messages, messages.text(headlineKey), describe(error));
    }

    static void error(Component parent, Messages messages, String headline, String detail) {
        String text = detail == null || detail.isBlank() ? headline
                : headline + "\n\n" + messages.text("error.detail") + "\n" + detail;
        show(parent, messages, "dialog.errorTitle", SafeText.message(text), JOptionPane.ERROR_MESSAGE);
    }

    static void info(Component parent, Messages messages, String titleKey, String text) {
        show(parent, messages, titleKey, SafeText.message(text), JOptionPane.INFORMATION_MESSAGE);
    }

    static boolean confirm(Component parent, Messages messages, String titleKey, String text, String confirmKey) {
        Object[] options = {messages.text(confirmKey), messages.text("dialog.cancel")};
        int result = JOptionPane.showOptionDialog(parent, SafeText.message(text), messages.text(titleKey),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE, null, options, options[1]);
        return result == 0;
    }

    static boolean form(Component parent, Messages messages, String titleKey, JComponent form) {
        Object[] options = {messages.text("dialog.ok"), messages.text("dialog.cancel")};
        int result = JOptionPane.showOptionDialog(parent, form, messages.text(titleKey),
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
        return result == 0;
    }

    static Choice unsaved(Component parent, Messages messages, String documentName) {
        Object[] options = {messages.text("dialog.save"), messages.text("dialog.discard"),
                messages.text("dialog.cancel")};
        int result = JOptionPane.showOptionDialog(parent,
                SafeText.message(messages.text("unsaved.message", documentName)),
                messages.text("dialog.confirmTitle"), JOptionPane.YES_NO_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE, null, options, options[0]);
        return switch (result) {
            case 0 -> Choice.SAVE;
            case 1 -> Choice.DISCARD;
            default -> Choice.CANCEL;
        };
    }

    static String describe(Throwable error) {
        if (error == null) {
            return "";
        }
        StringBuilder text = new StringBuilder();
        Throwable current = error;
        for (int depth = 0; current != null && depth < 4; depth++) {
            String message = current.getMessage();
            String line = message == null || message.isBlank() ? current.getClass().getSimpleName() : message;
            if (text.indexOf(line) < 0) {
                if (!text.isEmpty()) {
                    text.append('\n');
                }
                text.append(line);
            }
            current = current.getCause();
        }
        return text.toString();
    }

    private static void show(Component parent, Messages messages, String titleKey, Object message, int type) {
        Object[] options = {messages.text("dialog.ok")};
        JOptionPane.showOptionDialog(parent, message, messages.text(titleKey), JOptionPane.DEFAULT_OPTION, type,
                null, options, options[0]);
    }
}
