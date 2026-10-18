package com.themoon.y1.apple;

import org.junit.Test;

import java.io.FileOutputStream;

import static org.junit.Assert.*;

public class QrCodeTest {
    @Test
    public void encodesLocalPairingUrl() throws Exception {
        String url = "http://192.168.1.20:8080/apple-music";
        QrCode code = QrCode.encode(url);
        assertTrue(code.size >= 21);
        assertTrue(code.getModule(0, 0));
        assertTrue(code.getModule(code.size - 1, 0));
        assertTrue(code.getModule(0, code.size - 1));
        writeBmp(code, "/tmp/apple-music-qr.bmp");
    }

    private static void writeBmp(QrCode code, String path) throws Exception {
        int scale = 8;
        int border = 4;
        int modules = code.size + border * 2;
        int width = modules * scale;
        int rowStride = (width * 3 + 3) & ~3;
        int pixelBytes = rowStride * width;
        byte[] header = new byte[54];
        header[0] = 'B';
        header[1] = 'M';
        int fileSize = 54 + pixelBytes;
        writeInt(header, 2, fileSize);
        writeInt(header, 10, 54);
        writeInt(header, 14, 40);
        writeInt(header, 18, width);
        writeInt(header, 22, width);
        header[26] = 1;
        header[28] = 24;
        writeInt(header, 34, pixelBytes);
        byte[] pixels = new byte[pixelBytes];
        for (int y = 0; y < width; y++) {
            int moduleY = (width - 1 - y) / scale - border;
            for (int x = 0; x < width; x++) {
                int moduleX = x / scale - border;
                boolean dark = moduleX >= 0 && moduleY >= 0 && moduleX < code.size && moduleY < code.size
                        && code.getModule(moduleX, moduleY);
                int offset = y * rowStride + x * 3;
                pixels[offset] = pixels[offset + 1] = pixels[offset + 2] = dark ? 0 : (byte) 255;
            }
        }
        FileOutputStream out = new FileOutputStream(path);
        out.write(header);
        out.write(pixels);
        out.close();
    }

    private static void writeInt(byte[] header, int offset, int value) {
        header[offset] = (byte) value;
        header[offset + 1] = (byte) (value >> 8);
        header[offset + 2] = (byte) (value >> 16);
        header[offset + 3] = (byte) (value >> 24);
    }
}
