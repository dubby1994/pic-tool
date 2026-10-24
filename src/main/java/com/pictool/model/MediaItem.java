package com.pictool.model;

import java.nio.file.Path;
import java.time.LocalDateTime;

public final class MediaItem {

    private final String name;
    private final Path path;
    private final MediaType type;
    private final long size;
    private final LocalDateTime time;

    public MediaItem(String name, Path path, MediaType type, long size, LocalDateTime time) {
        this.name = name;
        this.path = path;
        this.type = type;
        this.size = size;
        this.time = time;
    }

    public String getName() {
        return name;
    }

    public Path getPath() {
        return path;
    }

    public MediaType getType() {
        return type;
    }

    public long getSize() {
        return size;
    }

    public LocalDateTime getTime() {
        return time;
    }
}
