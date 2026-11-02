package space.qu4nt.thirdparty.mcpainter.nbt;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.EOFException;
import java.io.FilterInputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Objects;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.util.zip.InflaterInputStream;
import java.util.zip.ZipException;

public final class NbtIo {

    private static final int BUFFER_SIZE = 8192;

    private NbtIo() {
    }

    public static RootTag read(InputStream in, NbtCompression compression) throws IOException {
        return read(in, compression, NbtLimits.DEFAULT);
    }

    public static RootTag read(InputStream in, NbtCompression compression, NbtLimits limits)
            throws IOException {
        Objects.requireNonNull(in, "in");
        Objects.requireNonNull(compression, "compression");
        Objects.requireNonNull(limits, "limits");
        InputStream guarded = new NonClosingInputStream(in);
        try (InputStream decoded = new BufferedInputStream(decoder(guarded, compression), BUFFER_SIZE)) {
            return NbtReader.read(decoded, limits);
        } catch (ZipException | EOFException e) {
            throw new NbtFormatException("corrupt " + compression + " stream", e);
        }
    }

    public static void write(OutputStream out, RootTag root, NbtCompression compression)
            throws IOException {
        Objects.requireNonNull(out, "out");
        Objects.requireNonNull(root, "root");
        Objects.requireNonNull(compression, "compression");
        OutputStream guarded = new NonClosingOutputStream(out);
        try (OutputStream encoded = new BufferedOutputStream(encoder(guarded, compression), BUFFER_SIZE)) {
            NbtWriter.write(encoded, root);
        }
        out.flush();
    }

    private static InputStream decoder(InputStream in, NbtCompression compression) throws IOException {
        return switch (compression) {
            case NONE -> in;
            case GZIP -> new GZIPInputStream(in, BUFFER_SIZE);
            case ZLIB -> new InflaterInputStream(in);
        };
    }

    private static OutputStream encoder(OutputStream out, NbtCompression compression) throws IOException {
        return switch (compression) {
            case NONE -> out;
            case GZIP -> new GZIPOutputStream(out, BUFFER_SIZE);
            case ZLIB -> new DeflaterOutputStream(out);
        };
    }

    private static final class NonClosingInputStream extends FilterInputStream {

        private NonClosingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public void close() {
        }
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {

        private NonClosingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            out.write(b, off, len);
        }

        @Override
        public void close() throws IOException {
            out.flush();
        }
    }
}
