package com.pictool.service;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Set;

public final class MediaFormats {

    private static final Set<String> IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "gif", "bmp", "webp");

    private static final Set<String> VIDEO_EXTS = Set.of(
            "mp4", "mov", "avi", "mkv", "wmv", "flv", "m4v", "3gp", "mpg", "mpeg", "ts", "rmvb");

    private MediaFormats() {
    }

    public static boolean isImage(Path path) {
        return IMAGE_EXTS.contains(ext(path));
    }

    public static boolean isVideo(Path path) {
        return VIDEO_EXTS.contains(ext(path));
    }

    public static boolean isMedia(Path path) {
        String ext = ext(path);
        return IMAGE_EXTS.contains(ext) || VIDEO_EXTS.contains(ext);
    }

    private static String ext(Path path) {
        String name = path.getFileName().toString();
        int i = name.lastIndexOf('.');
        return i < 0 ? "" : name.substring(i + 1).toLowerCase(Locale.ROOT);
    }
}
