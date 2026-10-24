package com.pictool.service;

import com.pictool.model.ImportStats;
import javafx.concurrent.Task;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public final class ImportService {

    private ImportService() {
    }

    public static final class ImportTask extends Task<ImportStats> {
        private final List<Path> sources;
        private final Path target;
        private final IndexStore index;
        private final List<String> failures = Collections.synchronizedList(new ArrayList<>());

        private ImportTask(List<Path> sources, Path target, IndexStore index) {
            this.sources = List.copyOf(sources);
            this.target = target;
            this.index = index;
        }

        public List<String> getFailures() {
            return List.copyOf(failures);
        }

        @Override
        protected ImportStats call() throws Exception {
            ImportStats stats = new ImportStats();
            Files.createDirectories(target);

            updateMessage("正在扫描源目录…");
            List<Path> candidates = new ArrayList<>();
            for (Path source : sources) {
                if (isCancelled()) {
                    break;
                }
                try (Stream<Path> walk = Files.walk(source)) {
                    walk.filter(Files::isRegularFile)
                            .filter(MediaFormats::isMedia)
                            .forEach(candidates::add);
                }
            }
            stats.total = candidates.size();
            updateMessage("扫描完成，共 " + stats.total + " 个文件，开始导入…");

            long done = 0;
            for (Path src : candidates) {
                if (isCancelled()) {
                    updateMessage("已取消");
                    break;
                }
                done++;
                updateProgress(done, stats.total);
                String name = src.getFileName().toString();
                try {
                    String md5 = Md5Util.hex(src);
                    long size = Files.size(src);
                    IndexStore.IndexEntry existing = index.get(md5);
                    if (existing != null && Files.exists(target.resolve(existing.relativePath()))) {
                        stats.skipped++;
                    } else {
                        Path dest = uniqueDest(target, name);
                        Files.copy(src, dest, StandardCopyOption.COPY_ATTRIBUTES);
                        index.put(md5, size, target.relativize(dest).toString());
                        stats.copied++;
                    }
                } catch (Exception e) {
                    stats.failed++;
                    failures.add(name + ": " + e.getMessage());
                }
                if (done % 100 == 0 || done == stats.total) {
                    updateMessage(String.format("导入中 %d/%d (复制 %d, 跳过 %d, 失败 %d)",
                            done, stats.total, stats.copied, stats.skipped, stats.failed));
                }
            }
            index.save();
            return stats;
        }
    }

    public static ImportTask importTask(List<Path> sources, Path target, IndexStore index) {
        return new ImportTask(sources, target, index);
    }

    public static Task<Integer> createRebuildTask(Path target, IndexStore index) {
        return new Task<>() {
            @Override
            protected Integer call() throws Exception {
                List<Path> files = new ArrayList<>();
                try (Stream<Path> walk = Files.walk(target)) {
                    walk.filter(Files::isRegularFile)
                            .filter(p -> !p.getFileName().toString().startsWith("."))
                            .filter(MediaFormats::isMedia)
                            .forEach(files::add);
                }
                Map<String, IndexStore.IndexEntry> rebuilt = new HashMap<>();
                long done = 0;
                for (Path file : files) {
                    if (isCancelled()) {
                        break;
                    }
                    done++;
                    updateProgress(done, files.size());
                    try {
                        String md5 = Md5Util.hex(file);
                        rebuilt.put(md5, new IndexStore.IndexEntry(
                                Files.size(file), target.relativize(file).toString()));
                    } catch (IOException e) {
                        updateMessage("索引失败: " + file.getFileName() + " - " + e.getMessage());
                    }
                    updateMessage("重建索引中 " + done + "/" + files.size());
                }
                if (!isCancelled()) {
                    index.replaceWith(rebuilt);
                    index.save();
                }
                return rebuilt.size();
            }
        };
    }

    private static Path uniqueDest(Path target, String fileName) {
        Path dest = target.resolve(fileName);
        if (!Files.exists(dest)) {
            return dest;
        }
        int dot = fileName.lastIndexOf('.');
        String base = dot < 0 ? fileName : fileName.substring(0, dot);
        String ext = dot < 0 ? "" : fileName.substring(dot);
        for (int i = 1; i < 100_000; i++) {
            dest = target.resolve(base + " (" + i + ")" + ext);
            if (!Files.exists(dest)) {
                return dest;
            }
        }
        throw new IllegalStateException("无法为目标文件生成唯一名称: " + fileName);
    }
}
