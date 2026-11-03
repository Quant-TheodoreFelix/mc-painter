package space.qu4nt.thirdparty.mcpainter.ui;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;
import java.util.function.DoubleConsumer;
import javax.swing.JComponent;

final class MacGestures {

    static final String PACKAGE = "com.apple.eawt.event";

    private MacGestures() {
    }

    static boolean available() {
        String os = System.getProperty("os.name", "");
        return os.toLowerCase(Locale.ROOT).startsWith("mac")
                && JComponent.class.getModule().isExported(PACKAGE, MacGestures.class.getModule());
    }

    static boolean installMagnify(JComponent target, DoubleConsumer magnify) {
        if (!available()) {
            return false;
        }
        try {
            ClassLoader loader = JComponent.class.getClassLoader();
            Class<?> utilities = Class.forName(PACKAGE + ".GestureUtilities", true, loader);
            Class<?> gestureListener = Class.forName(PACKAGE + ".GestureListener", true, loader);
            Class<?> magnificationListener = Class.forName(PACKAGE + ".MagnificationListener", true, loader);
            Class<?> magnificationEvent = Class.forName(PACKAGE + ".MagnificationEvent", true, loader);
            Method magnification = magnificationEvent.getMethod("getMagnification");
            Method add = utilities.getMethod("addGestureListenerTo", JComponent.class, gestureListener);
            InvocationHandler handler = (proxy, method, args) -> switch (method.getName()) {
                case "magnify" -> {
                    if (args != null && args.length == 1 && magnificationEvent.isInstance(args[0])
                            && magnification.invoke(args[0]) instanceof Double value) {
                        magnify.accept(value);
                    }
                    yield null;
                }
                case "equals" -> args != null && args.length == 1 && proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                case "toString" -> "MacGestures.magnify";
                default -> null;
            };
            Object listener = Proxy.newProxyInstance(MacGestures.class.getClassLoader(),
                    new Class<?>[] {magnificationListener}, handler);
            add.invoke(null, target, listener);
            return true;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
            return false;
        }
    }
}
