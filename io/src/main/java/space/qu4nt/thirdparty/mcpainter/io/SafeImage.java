package space.qu4nt.thirdparty.mcpainter.io;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Iterator;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStreamImpl;
import space.qu4nt.thirdparty.mcpainter.anvil.internal.AtomicFiles;

final class SafeImage {

    static final Set<String> PNG = Set.of("png");
    static final Set<String> COMMON = Set.of("png", "jpeg", "jpg", "bmp", "gif", "tif", "tiff");

    record Decoded(BufferedImage image, String format, int width, int height) {
    }

    @FunctionalInterface
    interface SizeCheck {

        void check(int width, int height) throws IOException;
    }

    private SafeImage() {
    }

    static Decoded read(Path file, String what, long maxBytes, int maxDimension, Set<String> formats, int targetSide,
            SizeCheck extra) throws IOException {
        AtomicFiles.requireNotSymlink(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IOException("not a regular file: " + file);
        }
        if (Files.size(file) > maxBytes) {
            throw new IOException(what + " file exceeds " + maxBytes + " bytes");
        }
        ImageIO.setUseCache(false);
        try (ChannelImageInputStream stream = new ChannelImageInputStream(
                Files.newByteChannel(file, Set.of(StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)), maxBytes, what)) {
            ImageReader reader = findReader(stream, formats, what);
            try {
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > maxDimension || height > maxDimension) {
                    throw new IOException(what + " size " + width + "x" + height + " outside 1.." + maxDimension);
                }
                extra.check(width, height);
                ImageReadParam param = reader.getDefaultReadParam();
                int factor = targetSide > 0 ? Math.max(1, Math.max(width, height) / (targetSide * 2)) : 1;
                if (factor > 1) {
                    param.setSourceSubsampling(factor, factor, 0, 0);
                }
                return new Decoded(reader.read(0, param), reader.getFormatName().toUpperCase(Locale.ROOT), width,
                        height);
            } finally {
                reader.dispose();
            }
        }
    }

    private static ImageReader findReader(ChannelImageInputStream stream, Set<String> formats, String what)
            throws IOException {
        Iterator<ImageReader> readers = ImageIO.getImageReaders(stream);
        while (readers.hasNext()) {
            ImageReader reader = readers.next();
            ImageReaderSpi provider = reader.getOriginatingProvider();
            if (provider != null && provider.getClass().getModule() == ImageIO.class.getModule()) {
                for (String name : provider.getFormatNames()) {
                    if (formats.contains(name.toLowerCase(Locale.ROOT))) {
                        stream.seek(0);
                        return reader;
                    }
                }
            }
            reader.dispose();
        }
        Set<String> supported = new TreeSet<>();
        for (String format : formats) {
            supported.add(format.equals("jpg") ? "jpeg" : format.equals("tif") ? "tiff" : format);
        }
        throw new IOException(what + " is not a supported image (" + String.join(", ", supported) + ")");
    }

    private static final class ChannelImageInputStream extends ImageInputStreamImpl {

        private static final int BUFFER_BYTES = 64 * 1024;

        private final SeekableByteChannel channel;
        private final long maxBytes;
        private final String what;
        private final ByteBuffer buffer = ByteBuffer.allocate(BUFFER_BYTES);
        private long bufferStart = -1;
        private int bufferLength;

        ChannelImageInputStream(SeekableByteChannel channel, long maxBytes, String what) {
            this.channel = channel;
            this.maxBytes = maxBytes;
            this.what = what;
        }

        private boolean fill() throws IOException {
            if (bufferStart >= 0 && streamPos >= bufferStart && streamPos < bufferStart + bufferLength) {
                return true;
            }
            if (streamPos >= maxBytes) {
                if (channel.size() > maxBytes) {
                    throw new IOException(what + " file grew beyond its size limit while reading");
                }
                return false;
            }
            channel.position(streamPos);
            buffer.clear();
            buffer.limit((int) Math.min(BUFFER_BYTES, maxBytes - streamPos));
            int read = channel.read(buffer);
            if (read <= 0) {
                bufferStart = -1;
                bufferLength = 0;
                return false;
            }
            bufferStart = streamPos;
            bufferLength = read;
            return true;
        }

        @Override
        public int read() throws IOException {
            checkClosed();
            bitOffset = 0;
            if (!fill()) {
                return -1;
            }
            int value = buffer.get((int) (streamPos - bufferStart)) & 0xFF;
            streamPos++;
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            checkClosed();
            Objects.checkFromIndexSize(offset, length, bytes.length);
            bitOffset = 0;
            if (length == 0) {
                return 0;
            }
            if (!fill()) {
                return -1;
            }
            int index = (int) (streamPos - bufferStart);
            int count = Math.min(length, bufferLength - index);
            buffer.get(index, bytes, offset, count);
            streamPos += count;
            return count;
        }

        @Override
        public long length() {
            try {
                return Math.min(channel.size(), maxBytes);
            } catch (IOException e) {
                return -1;
            }
        }

        @Override
        public void close() throws IOException {
            try {
                super.close();
            } finally {
                channel.close();
            }
        }
    }
}
