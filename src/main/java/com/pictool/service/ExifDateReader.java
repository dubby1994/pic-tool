package com.pictool.service;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * 轻量 JPEG EXIF 拍摄时间读取器，只解析 APP1 段中的
 * DateTimeOriginal(0x9003) / DateTimeDigitized(0x9004) / DateTime(0x0132)。
 */
public final class ExifDateReader {

    private static final DateTimeFormatter EXIF_FORMAT = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss");

    private ExifDateReader() {
    }

    public static LocalDateTime readJpegExifDate(Path file) {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            if (in.read() != 0xFF || in.read() != 0xD8) {
                return null;
            }
            while (true) {
                int b = in.read();
                if (b == -1) {
                    return null;
                }
                if (b != 0xFF) {
                    continue;
                }
                int marker = in.read();
                if (marker == -1) {
                    return null;
                }
                if (marker == 0xFF) {
                    continue;
                }
                if (marker == 0x01 || (marker >= 0xD0 && marker <= 0xD9)) {
                    continue;
                }
                int lenHi = in.read();
                int lenLo = in.read();
                if (lenHi == -1 || lenLo == -1) {
                    return null;
                }
                int len = (lenHi << 8) | lenLo;
                if (len < 2) {
                    return null;
                }
                if (marker == 0xE1) {
                    return parseApp1(in.readNBytes(len - 2));
                }
                long skipped = in.skip(len - 2);
                while (skipped < len - 2) {
                    long more = in.skip(len - 2 - skipped);
                    if (more <= 0) {
                        return null;
                    }
                    skipped += more;
                }
            }
        } catch (IOException e) {
            return null;
        }
    }

    private static LocalDateTime parseApp1(byte[] data) {
        if (data == null || data.length < 14
                || data[0] != 'E' || data[1] != 'x' || data[2] != 'i' || data[3] != 'f'
                || data[4] != 0 || data[5] != 0) {
            return null;
        }
        ByteBuffer tiff = ByteBuffer.wrap(data, 6, data.length - 6).slice();
        byte b0 = tiff.get(0);
        byte b1 = tiff.get(1);
        if (b0 == 'I' && b1 == 'I') {
            tiff.order(ByteOrder.LITTLE_ENDIAN);
        } else if (b0 == 'M' && b1 == 'M') {
            tiff.order(ByteOrder.BIG_ENDIAN);
        } else {
            return null;
        }
        if (tiff.getShort(2) != 42) {
            return null;
        }
        int ifd0 = tiff.getInt(4);
        String date = findDateTime(tiff, ifd0);
        return parseDate(date);
    }

    private static String findDateTime(ByteBuffer tiff, int ifdOffset) {
        if (ifdOffset <= 0 || ifdOffset + 2 > tiff.limit()) {
            return null;
        }
        int count = tiff.getShort(ifdOffset) & 0xFFFF;
        String ifd0Date = null;
        int exifIfdOffset = -1;
        for (int i = 0; i < count; i++) {
            int entry = ifdOffset + 2 + i * 12;
            if (entry + 12 > tiff.limit()) {
                break;
            }
            int tag = tiff.getShort(entry) & 0xFFFF;
            int type = tiff.getShort(entry + 2) & 0xFFFF;
            int num = tiff.getInt(entry + 4);
            if (tag == 0x0132 && type == 2) {
                ifd0Date = readAscii(tiff, entry + 8, num);
            } else if (tag == 0x8769 && (type == 3 || type == 4)) {
                exifIfdOffset = type == 4 ? tiff.getInt(entry + 8) : (tiff.getShort(entry + 8) & 0xFFFF);
            }
        }
        if (exifIfdOffset > 0) {
            String exifDate = findInExifIfd(tiff, exifIfdOffset);
            if (exifDate != null) {
                return exifDate;
            }
        }
        return ifd0Date;
    }

    private static String findInExifIfd(ByteBuffer tiff, int ifdOffset) {
        if (ifdOffset <= 0 || ifdOffset + 2 > tiff.limit()) {
            return null;
        }
        int count = tiff.getShort(ifdOffset) & 0xFFFF;
        String digitized = null;
        for (int i = 0; i < count; i++) {
            int entry = ifdOffset + 2 + i * 12;
            if (entry + 12 > tiff.limit()) {
                break;
            }
            int tag = tiff.getShort(entry) & 0xFFFF;
            int type = tiff.getShort(entry + 2) & 0xFFFF;
            int num = tiff.getInt(entry + 4);
            if (type != 2) {
                continue;
            }
            if (tag == 0x9003) {
                String v = readAscii(tiff, entry + 8, num);
                if (v != null) {
                    return v;
                }
            } else if (tag == 0x9004) {
                digitized = readAscii(tiff, entry + 8, num);
            }
        }
        return digitized;
    }

    private static String readAscii(ByteBuffer tiff, int valueFieldPos, int len) {
        if (len <= 0) {
            return null;
        }
        int start = len <= 4 ? valueFieldPos : tiff.getInt(valueFieldPos);
        if (start < 0 || start + len > tiff.limit()) {
            return null;
        }
        byte[] bytes = new byte[len];
        for (int i = 0; i < len; i++) {
            bytes[i] = tiff.get(start + i);
        }
        int end = 0;
        while (end < bytes.length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, 0, end, StandardCharsets.US_ASCII);
    }

    private static LocalDateTime parseDate(String s) {
        if (s == null) {
            return null;
        }
        try {
            return LocalDateTime.parse(s.trim(), EXIF_FORMAT);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
