package com.pictool.service;

import com.pictool.model.MediaType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class MediaTimeExtractor {

    private MediaTimeExtractor() {
    }

    /**
     * 图片优先读取 EXIF 拍摄时间；视频及其他情况退回 创建时间 -> 最后修改时间。
     */
    public static LocalDateTime extractTime(Path file, MediaType type) {
        if (type == MediaType.IMAGE) {
            LocalDateTime exif = ExifDateReader.readJpegExifDate(file);
            if (exif != null) {
                return exif;
            }
        }
        try {
            BasicFileAttributes attrs = Files.readAttributes(file, BasicFileAttributes.class);
            LocalDateTime creation = toLocal(attrs.creationTime());
            if (creation != null) {
                return creation;
            }
            return toLocal(attrs.lastModifiedTime());
        } catch (IOException e) {
            return null;
        }
    }

    private static LocalDateTime toLocal(FileTime time) {
        if (time == null) {
            return null;
        }
        Instant instant = time.toInstant();
        if (instant.toEpochMilli() <= 0) {
            return null;
        }
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}
