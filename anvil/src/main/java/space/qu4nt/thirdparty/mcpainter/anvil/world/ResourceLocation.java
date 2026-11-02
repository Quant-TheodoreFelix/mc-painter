package space.qu4nt.thirdparty.mcpainter.anvil.world;

import java.util.regex.Pattern;

public record ResourceLocation(String namespace, String path) {

    public static final String DEFAULT_NAMESPACE = "minecraft";

    private static final int MAX_LENGTH = 256;
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern PATH = Pattern.compile("[a-z0-9_.-]+(/[a-z0-9_.-]+)*");

    public ResourceLocation {
        if (namespace == null || !NAMESPACE.matcher(namespace).matches()) {
            throw new IllegalArgumentException("invalid resource namespace: " + namespace);
        }
        if (path == null || !PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("invalid resource path: " + path);
        }
        if (namespace.length() + 1 + path.length() > MAX_LENGTH) {
            throw new IllegalArgumentException("resource location too long");
        }
        if (isDotSegment(namespace)) {
            throw new IllegalArgumentException("invalid resource namespace: " + namespace);
        }
        for (String segment : path.split("/", -1)) {
            if (isDotSegment(segment)) {
                throw new IllegalArgumentException("invalid resource path: " + path);
            }
        }
    }

    public static ResourceLocation minecraft(String path) {
        return new ResourceLocation(DEFAULT_NAMESPACE, path);
    }

    public static ResourceLocation parse(String value) {
        int colon = value.indexOf(':');
        if (colon < 0) {
            return new ResourceLocation(DEFAULT_NAMESPACE, value);
        }
        return new ResourceLocation(value.substring(0, colon), value.substring(colon + 1));
    }

    private static boolean isDotSegment(String segment) {
        return segment.equals(".") || segment.equals("..");
    }

    @Override
    public String toString() {
        return namespace + ":" + path;
    }
}
