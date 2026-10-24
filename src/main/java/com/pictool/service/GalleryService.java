package com.pictool.service;

import com.pictool.model.MediaItem;
import com.pictool.model.MediaType;
import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public final class GalleryService {

    public enum SortOrder {
        TIME_ASC("时间升序"),
        TIME_DESC("时间降序"),
        NAME("文件名");

        private final String label;

        SortOrder(String label) {
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    private GalleryService() {
    }

    public static Task<List<MediaItem>> createLoadTask(Path target,
                                                       LocalDate start,
                                                       LocalDate end,
                                                       SortOrder sortOrder) {
        return new Task<>() {
            @Override
            protected List<MediaItem> call() throws Exception {
                List<Path> files = new ArrayList<>();
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> !p.getFileName().toString().startsWith("."))
                            .filter(MediaFormats::isMedia)
                            .forEach(files::add);
                }
                List<MediaItem> items = new ArrayList<>();
                long done = 0;
                for (Path file : files) {
                    if (isCancelled()) {
                        break;
                    }
                    done++;
                    updateProgress(done, files.size());
                    try {
                        MediaType type = MediaFormats.isImage(file) ? MediaType.IMAGE : MediaType.VIDEO;
                        LocalDateTime time = MediaTimeExtractor.extractTime(file, type);
                        if (start != null && (time == null || time.toLocalDate().isBefore(start))) {
                            continue;
                        }
                        if (end != null && (time == null || time.toLocalDate().isAfter(end))) {
                            continue;
                        }
                        items.add(new MediaItem(
                                file.getFileName().toString(), file, type,
                                Files.size(file), time));
                    } catch (IOException e) {
                        updateMessage("读取失败: " + file.getFileName() + " - " + e.getMessage());
                    }
                }
                Comparator<MediaItem> comparator = switch (sortOrder) {
                    case TIME_ASC -> Comparator.comparing(MediaItem::getTime,
                            Comparator.nullsLast(Comparator.naturalOrder()));
                    case TIME_DESC -> Comparator.comparing(MediaItem::getTime,
                            Comparator.nullsLast(Comparator.naturalOrder())).reversed();
                    case NAME -> Comparator.comparing(MediaItem::getName,
                            String.CASE_INSENSITIVE_ORDER);
                };
                items.sort(comparator);
                return items;
            }
        };
    }
}
