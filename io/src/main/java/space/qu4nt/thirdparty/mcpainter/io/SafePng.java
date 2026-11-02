package space.qu4nt.thirdparty.mcpainter.io;

import java.awt.image.BufferedImage;
import java.io.BufferedInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;

final class SafePng {

    static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    @FunctionalInterface
    interface SizeCheck {

        void check(int width, int height) throws IOException;
    }

    private SafePng() {
    }

    static BufferedImage read(Path file, String what, long maxBytes, int maxDimension, SizeCheck extra)
            throws IOException {
        AtomicFiles.requireNotSymlink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("not a regular file: " + file);
        }
        if (Files.size(file) > maxBytes) {
            throw new IOException(what + " file exceeds " + maxBytes + " bytes");
        }
        ImageIO.setUseCache(false);
        try (InputStream in = new BufferedInputStream(
                new LimitedInputStream(Files.newInputStream(file, LinkOption.NOFOLLOW_LINKS), maxBytes, what))) {
            in.mark(SIGNATURE.length);
            if (!Arrays.equals(in.readNBytes(SIGNATURE.length), SIGNATURE)) {
                throw new IOException(what + " must be a PNG file");
            }
            in.reset();
            Iterator<ImageReader> readers = ImageIO.getImageReadersByFormatName("png");
            if (!readers.hasNext()) {
                throw new IOException("no PNG decoder available");
            }
            ImageReader reader = readers.next();
            try (ImageInputStream stream = ImageIO.createImageInputStream(in)) {
                if (stream == null) {
                    throw new IOException("cannot open " + what + " " + file);
                }
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > maxDimension || height > maxDimension) {
                    throw new IOException(what + " size " + width + "x" + height + " outside 1.." + maxDimension);
                }
                extra.check(width, height);
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        }
    }

    private static final class LimitedInputStream extends FilterInputStream {

        private final String what;
        private long remaining;

        LimitedInputStream(InputStream in, long limit, String what) {
            super(in);
            this.remaining = limit;
            this.what = what;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) {
                consume(1);
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int offset, int length) throws IOException {
            int n = super.read(buffer, offset, length);
            if (n > 0) {
                consume(n);
            }
            return n;
        }

        @Override
        public long skip(long n) throws IOException {
            long skipped = super.skip(n);
            consume(skipped);
            return skipped;
        }

        private void consume(long n) throws IOException {
            remaining -= n;
            if (remaining < 0) {
                throw new IOException(what + " file grew beyond its size limit while reading");
            }
        }
    }
}
