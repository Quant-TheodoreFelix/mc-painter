package space.qu4nt.thirdparty.mcpainter.nbt;

final class Mutf8 {

    static final int MAX_ENCODED_LENGTH = 0xFFFF;

    private Mutf8() {
    }

    static int encodedLength(String value) {
        int length = 0;
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c >= 0x0001 && c <= 0x007F) {
                length += 1;
            } else if (c <= 0x07FF) {
                length += 2;
            } else {
                length += 3;
            }
        }
        return length;
    }

    static String requireEncodable(String value, String what) {
        if (value == null) {
            throw new NullPointerException(what + " must not be null");
        }
        if (encodedLength(value) > MAX_ENCODED_LENGTH) {
            throw new IllegalArgumentException(what + " exceeds " + MAX_ENCODED_LENGTH + " encoded bytes");
        }
        return value;
    }

    static String decodeStrict(byte[] bytes) throws NbtFormatException {
        char[] chars = new char[bytes.length];
        int count = 0;
        int i = 0;
        while (i < bytes.length) {
            int b0 = bytes[i] & 0xFF;
            if (b0 >= 0x01 && b0 <= 0x7F) {
                chars[count++] = (char) b0;
                i += 1;
            } else if ((b0 & 0xE0) == 0xC0) {
                int b1 = continuation(bytes, i + 1);
                int c = ((b0 & 0x1F) << 6) | b1;
                if (c != 0 && c < 0x80) {
                    throw malformed(i);
                }
                chars[count++] = (char) c;
                i += 2;
            } else if ((b0 & 0xF0) == 0xE0) {
                int b1 = continuation(bytes, i + 1);
                int b2 = continuation(bytes, i + 2);
                int c = ((b0 & 0x0F) << 12) | (b1 << 6) | b2;
                if (c < 0x800) {
                    throw malformed(i);
                }
                chars[count++] = (char) c;
                i += 3;
            } else {
                throw malformed(i);
            }
        }
        return new String(chars, 0, count);
    }

    private static int continuation(byte[] bytes, int index) throws NbtFormatException {
        if (index >= bytes.length || (bytes[index] & 0xC0) != 0x80) {
            throw malformed(index);
        }
        return bytes[index] & 0x3F;
    }

    private static NbtFormatException malformed(int offset) {
        return new NbtFormatException("malformed modified UTF-8 at byte " + offset);
    }
}
