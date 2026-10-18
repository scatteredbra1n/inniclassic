package com.themoon.y1.apple;

/**
 * Byte-mode QR codes (ISO/IEC 18004), versions 1–6, error correction L or M.
 * Enough for a local URL such as http://192.168.1.20:8080/apple-music.
 */
public final class QrCode {
    private static final int[][][] EC = {
            null,
            {{7, 1, 19}, {10, 1, 16}},
            {{10, 1, 34}, {16, 1, 28}},
            {{15, 1, 55}, {26, 1, 44}},
            {{20, 1, 80}, {18, 2, 32}},
            {{26, 1, 108}, {24, 2, 43}},
            {{18, 2, 68}, {16, 4, 27}},
    };
    private static final int[][] ALIGN = {
            {},
            {},
            {6, 18},
            {6, 22},
            {6, 26},
            {6, 30},
            {6, 34},
    };

    public final int size;
    private final boolean[][] modules;

    private QrCode(int size, boolean[][] modules) {
        this.size = size;
        this.modules = modules;
    }

    public boolean getModule(int x, int y) {
        return modules[y][x];
    }

    public static QrCode encode(String text) {
        if (text == null) throw new IllegalArgumentException("Missing QR text");
        byte[] data;
        try {
            data = text.getBytes("UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            throw new IllegalArgumentException(e);
        }
        if (data.length > 255) throw new IllegalArgumentException("QR text is too long");
        for (int version = 1; version <= 6; version++) {
            for (int level = 1; level >= 0; level--) {
                int dataCodewords = EC[version][level][1] * EC[version][level][2];
                int bits = 4 + 8 + data.length * 8;
                if (bits <= dataCodewords * 8) return encode(data, version, level);
            }
        }
        throw new IllegalArgumentException("QR text is too long");
    }

    private static QrCode encode(byte[] data, int version, int level) {
        int eccPerBlock = EC[version][level][0];
        int blocks = EC[version][level][1];
        int dataPerBlock = EC[version][level][2];
        int dataCodewords = blocks * dataPerBlock;
        boolean[] bits = new boolean[dataCodewords * 8];
        int cursor = 0;
        cursor = writeBits(bits, cursor, 0x4, 4);
        cursor = writeBits(bits, cursor, data.length, 8);
        for (byte value : data) cursor = writeBits(bits, cursor, value & 0xFF, 8);
        int terminator = Math.min(4, bits.length - cursor);
        cursor += terminator;
        cursor = (cursor + 7) / 8 * 8;
        int pad = 0xEC;
        while (cursor < bits.length) {
            cursor = writeBits(bits, cursor, pad, 8);
            pad = pad == 0xEC ? 0x11 : 0xEC;
        }
        byte[] codewords = new byte[dataCodewords];
        for (int i = 0; i < codewords.length; i++) {
            int value = 0;
            for (int bit = 0; bit < 8; bit++) if (bits[i * 8 + bit]) value |= 1 << (7 - bit);
            codewords[i] = (byte) value;
        }
        byte[] interleaved = interleave(codewords, blocks, dataPerBlock, eccPerBlock);
        boolean[] stream = new boolean[interleaved.length * 8];
        for (int i = 0; i < interleaved.length; i++) {
            writeBits(stream, i * 8, interleaved[i] & 0xFF, 8);
        }
        return place(version, level, stream);
    }

    private static int writeBits(boolean[] bits, int cursor, int value, int length) {
        for (int i = length - 1; i >= 0; i--) bits[cursor++] = ((value >> i) & 1) != 0;
        return cursor;
    }

    private static byte[] interleave(byte[] data, int blocks, int dataPerBlock, int eccPerBlock) {
        byte[][] payloads = new byte[blocks][dataPerBlock];
        byte[][] ecc = new byte[blocks][eccPerBlock];
        for (int i = 0; i < blocks; i++) {
            System.arraycopy(data, i * dataPerBlock, payloads[i], 0, dataPerBlock);
            byte[] remainder = reedSolomon(payloads[i], eccPerBlock);
            System.arraycopy(remainder, 0, ecc[i], 0, eccPerBlock);
        }
        byte[] out = new byte[blocks * (dataPerBlock + eccPerBlock)];
        int cursor = 0;
        for (int i = 0; i < dataPerBlock; i++) {
            for (int block = 0; block < blocks; block++) out[cursor++] = payloads[block][i];
        }
        for (int i = 0; i < eccPerBlock; i++) {
            for (int block = 0; block < blocks; block++) out[cursor++] = ecc[block][i];
        }
        return out;
    }

    private static byte[] reedSolomon(byte[] data, int eccLen) {
        int[] divisor = new int[eccLen];
        divisor[eccLen - 1] = 1;
        int root = 1;
        for (int i = 0; i < eccLen; i++) {
            for (int j = 0; j < eccLen; j++) {
                divisor[j] = mul(divisor[j], root);
                if (j + 1 < eccLen) divisor[j] ^= divisor[j + 1];
            }
            root = mul(root, 0x02);
        }
        int[] remainder = new int[eccLen];
        for (byte value : data) {
            int factor = (value & 0xFF) ^ remainder[0];
            System.arraycopy(remainder, 1, remainder, 0, remainder.length - 1);
            remainder[remainder.length - 1] = 0;
            for (int j = 0; j < eccLen; j++) remainder[j] ^= mul(divisor[j], factor);
        }
        byte[] out = new byte[eccLen];
        for (int i = 0; i < eccLen; i++) out[i] = (byte) remainder[i];
        return out;
    }

    private static final int[] EXP = new int[512];
    private static final int[] LOG = new int[256];

    static {
        int value = 1;
        for (int i = 0; i < 255; i++) {
            EXP[i] = value;
            LOG[value] = i;
            value <<= 1;
            if (value >= 256) value ^= 0x11D;
        }
        for (int i = 255; i < 512; i++) EXP[i] = EXP[i - 255];
    }

    private static int mul(int a, int b) {
        if (a == 0 || b == 0) return 0;
        return EXP[LOG[a] + LOG[b]];
    }

    private static QrCode place(int version, int level, boolean[] data) {
        int size = 21 + 4 * (version - 1);
        boolean[][] func = new boolean[size][size];
        boolean[][] dark = new boolean[size][size];
        drawFinder(dark, func, 0, 0);
        drawFinder(dark, func, size - 7, 0);
        drawFinder(dark, func, 0, size - 7);
        for (int i = 8; i < size - 8; i++) {
            boolean on = i % 2 == 0;
            setFunction(dark, func, i, 6, on);
            setFunction(dark, func, 6, i, on);
        }
        int[] align = ALIGN[version];
        for (int y : align) {
            for (int x : align) {
                if (func[y][x]) continue;
                drawAlignment(dark, func, x, y);
            }
        }
        setFunction(dark, func, 8, size - 8, true);
        reserveFormat(func, size);
        int bit = 0;
        boolean upward = true;
        for (int right = size - 1; right >= 1; right -= 2) {
            if (right == 6) right = 5;
            for (int vert = 0; vert < size; vert++) {
                int y = upward ? size - 1 - vert : vert;
                for (int shift = 0; shift < 2; shift++) {
                    int x = right - shift;
                    if (func[y][x]) continue;
                    dark[y][x] = bit < data.length && data[bit];
                    bit++;
                }
            }
            upward = !upward;
        }
        int bestMask = 0;
        int bestScore = Integer.MAX_VALUE;
        boolean[][] best = null;
        for (int mask = 0; mask < 8; mask++) {
            boolean[][] candidate = applyMask(dark, func, mask);
            // Spec format bits: M = 00, L = 01. The table index is the other way around.
            writeFormat(candidate, size, level == 0 ? 1 : 0, mask);
            int score = penalty(candidate);
            if (score < bestScore) {
                bestScore = score;
                bestMask = mask;
                best = candidate;
            }
        }
        if (bestMask < 0) throw new IllegalStateException("No QR mask");
        return new QrCode(size, best);
    }

    private static void drawFinder(boolean[][] dark, boolean[][] func, int ox, int oy) {
        for (int dy = -1; dy <= 7; dy++) {
            for (int dx = -1; dx <= 7; dx++) {
                int x = ox + dx;
                int y = oy + dy;
                if (x < 0 || y < 0 || y >= dark.length || x >= dark.length) continue;
                boolean on = dx >= 0 && dx <= 6 && dy >= 0 && dy <= 6
                        && (dx == 0 || dx == 6 || dy == 0 || dy == 6 || (dx >= 2 && dx <= 4 && dy >= 2 && dy <= 4));
                setFunction(dark, func, x, y, on);
            }
        }
    }

    private static void drawAlignment(boolean[][] dark, boolean[][] func, int cx, int cy) {
        for (int dy = -2; dy <= 2; dy++) {
            for (int dx = -2; dx <= 2; dx++) {
                boolean on = Math.max(Math.abs(dx), Math.abs(dy)) != 1;
                setFunction(dark, func, cx + dx, cy + dy, on);
            }
        }
    }

    private static void setFunction(boolean[][] dark, boolean[][] func, int x, int y, boolean on) {
        func[y][x] = true;
        dark[y][x] = on;
    }

    private static void reserveFormat(boolean[][] func, int size) {
        for (int i = 0; i < 9; i++) {
            func[8][i] = true;
            func[i][8] = true;
        }
        for (int i = 0; i < 8; i++) {
            func[8][size - 1 - i] = true;
            func[size - 1 - i][8] = true;
        }
    }

    private static boolean[][] applyMask(boolean[][] dark, boolean[][] func, int mask) {
        int size = dark.length;
        boolean[][] out = new boolean[size][size];
        for (int y = 0; y < size; y++) {
            for (int x = 0; x < size; x++) {
                boolean bit = dark[y][x];
                if (!func[y][x] && maskBit(mask, x, y)) bit = !bit;
                out[y][x] = bit;
            }
        }
        return out;
    }

    private static boolean maskBit(int mask, int x, int y) {
        switch (mask) {
            case 0: return (x + y) % 2 == 0;
            case 1: return y % 2 == 0;
            case 2: return x % 3 == 0;
            case 3: return (x + y) % 3 == 0;
            case 4: return (y / 2 + x / 3) % 2 == 0;
            case 5: return (x * y) % 2 + (x * y) % 3 == 0;
            case 6: return ((x * y) % 2 + (x * y) % 3) % 2 == 0;
            default: return ((x + y) % 2 + (x * y) % 3) % 2 == 0;
        }
    }

    private static void writeFormat(boolean[][] dark, int size, int level, int mask) {
        int data = (level << 3) | mask;
        int rem = data << 10;
        for (int i = 14; i >= 10; i--) {
            if (((rem >> i) & 1) != 0) rem ^= 0x537 << (i - 10);
        }
        int bits = ((data << 10) | rem) ^ 0x5412;
        int[][] near = {
                {8, 0}, {8, 1}, {8, 2}, {8, 3}, {8, 4}, {8, 5}, {8, 7}, {8, 8},
                {7, 8}, {5, 8}, {4, 8}, {3, 8}, {2, 8}, {1, 8}, {0, 8},
        };
        for (int i = 0; i < 15; i++) {
            boolean on = ((bits >> i) & 1) != 0;
            dark[near[i][1]][near[i][0]] = on;
            if (i < 8) dark[8][size - 1 - i] = on;
            else dark[size - 15 + i][8] = on;
        }
    }

    private static int penalty(boolean[][] dark) {
        int size = dark.length;
        int score = 0;
        for (int y = 0; y < size; y++) score += runPenalty(dark, y, true);
        for (int x = 0; x < size; x++) score += runPenalty(dark, x, false);
        for (int y = 0; y < size - 1; y++) {
            for (int x = 0; x < size - 1; x++) {
                boolean bit = dark[y][x];
                if (bit == dark[y][x + 1] && bit == dark[y + 1][x] && bit == dark[y + 1][x + 1]) score += 3;
            }
        }
        score += finderPenalty(dark, true);
        score += finderPenalty(dark, false);
        int darkCount = 0;
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) if (dark[y][x]) darkCount++;
        int percent = darkCount * 100 / (size * size);
        int previous = Math.abs(percent - 50) / 5;
        score += previous * 10;
        return score;
    }

    private static int runPenalty(boolean[][] dark, int fixed, boolean row) {
        int size = dark.length;
        int score = 0;
        int run = 1;
        boolean color = row ? dark[fixed][0] : dark[0][fixed];
        for (int i = 1; i < size; i++) {
            boolean bit = row ? dark[fixed][i] : dark[i][fixed];
            if (bit == color) {
                run++;
                if (run == 5) score += 3;
                else if (run > 5) score++;
            } else {
                color = bit;
                run = 1;
            }
        }
        return score;
    }

    private static int finderPenalty(boolean[][] dark, boolean row) {
        int size = dark.length;
        int score = 0;
        for (int fixed = 0; fixed < size; fixed++) {
            int bits = 0;
            for (int i = 0; i < size; i++) {
                boolean on = row ? dark[fixed][i] : dark[i][fixed];
                bits = ((bits << 1) & 0x7FF) | (on ? 1 : 0);
                if (i >= 10 && (bits == 0x5D || bits == 0x5D0)) score += 40;
            }
        }
        return score;
    }
}
