package dev.ikna.desktop;

/** Layout must match the first resized picture, rather than catch up on the next frame. */
public final class WindowFrameLayout {
    private int width = -1, height = -1;
    private float scale;
    private long revision, preparedRevision = -1;
    private boolean rendering;
    private int composeWidth = -1, composeHeight = -1;

    public void invalidate() { revision++; }

    public void observeComposeSize(int width, int height) {
        composeWidth = width;
        composeHeight = height;
    }

    public int getComposeWidth() { return composeWidth; }
    public int getComposeHeight() { return composeHeight; }

    public boolean needsPreparation(int width, int height, float scale) {
        return width > 0 && height > 0 && Float.isFinite(scale) && scale > 0 &&
                (this.width != width || this.height != height || this.scale != scale ||
                 preparedRevision != revision);
    }

    /** Returns whether layout ran; successful preparation delegates drawing exactly once. */
    public boolean render(int width, int height, float scale, Runnable prepare, Runnable draw) {
        if (rendering) throw new IllegalStateException("Recursive window picture recording");
        rendering = true;
        boolean prepared = false;
        try {
            if (needsPreparation(width, height, scale)) {
                long observedRevision = revision;
                prepare.run();
                // Commit only after successful layout. Invalidation during prepare/draw
                // stays pending for the following frame instead of being swallowed.
                this.width = width;
                this.height = height;
                this.scale = scale;
                preparedRevision = observedRevision;
                prepared = true;
            }
            draw.run();
            return prepared;
        } finally {
            rendering = false;
        }
    }
}
