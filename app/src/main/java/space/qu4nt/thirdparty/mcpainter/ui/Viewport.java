package space.qu4nt.thirdparty.mcpainter.ui;

final class Viewport {

    static final int MIN_ZOOM = -4;
    static final int MAX_ZOOM = 4;
    static final int MAX_FIT_ZOOM = 2;
    static final double WORLD_LIMIT = 30_000_000;

    private int zoom;
    private double originX;
    private double originZ;

    int zoom() {
        return zoom;
    }

    double scale() {
        return Math.scalb(1.0, zoom);
    }

    int lod() {
        return Math.max(0, -zoom);
    }

    int percent() {
        return (int) Math.round(scale() * 100);
    }

    double originX() {
        return originX;
    }

    double originZ() {
        return originZ;
    }

    double worldX(double screenX) {
        return originX + screenX / scale();
    }

    double worldZ(double screenZ) {
        return originZ + screenZ / scale();
    }

    double screenX(double worldX) {
        return (worldX - originX) * scale();
    }

    double screenZ(double worldZ) {
        return (worldZ - originZ) * scale();
    }

    boolean zoomAt(int steps, double screenX, double screenZ) {
        return setZoom(zoom + steps, screenX, screenZ);
    }

    boolean setZoom(int value, double screenX, double screenZ) {
        requireFinite(screenX, screenZ);
        int next = Math.clamp(value, MIN_ZOOM, MAX_ZOOM);
        if (next == zoom) {
            return false;
        }
        double anchorX = worldX(screenX);
        double anchorZ = worldZ(screenZ);
        zoom = next;
        setOrigin(anchorX - screenX / scale(), anchorZ - screenZ / scale());
        return true;
    }

    void pan(double screenDx, double screenDz) {
        requireFinite(screenDx, screenDz);
        setOrigin(originX - screenDx / scale(), originZ - screenDz / scale());
    }

    void centerOn(double worldX, double worldZ, int width, int height) {
        requireFinite(worldX, worldZ);
        setOrigin(worldX - width / 2.0 / scale(), worldZ - height / 2.0 / scale());
    }

    void fit(int minX, int minZ, int maxX, int maxZ, int width, int height) {
        int chosen = MIN_ZOOM;
        for (int candidate = MAX_FIT_ZOOM; candidate >= MIN_ZOOM; candidate--) {
            double s = Math.scalb(1.0, candidate);
            if ((double) (maxX - minX) * s <= width * 0.9 && (double) (maxZ - minZ) * s <= height * 0.9) {
                chosen = candidate;
                break;
            }
        }
        zoom = chosen;
        centerOn((minX + (double) maxX) / 2, (minZ + (double) maxZ) / 2, width, height);
    }

    private void setOrigin(double x, double z) {
        originX = Math.clamp(x, -WORLD_LIMIT, WORLD_LIMIT);
        originZ = Math.clamp(z, -WORLD_LIMIT, WORLD_LIMIT);
    }

    private static void requireFinite(double a, double b) {
        if (!Double.isFinite(a) || !Double.isFinite(b)) {
            throw new IllegalArgumentException("coordinates must be finite");
        }
    }
}
