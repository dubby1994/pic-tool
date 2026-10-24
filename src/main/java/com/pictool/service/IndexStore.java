package com.pictool.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 目标目录媒体文件的持久化索引：md5 -> (size, 相对路径)。
 * 索引文件保存在目标目录下的 .pic-tool-index.tsv 中。
 */
public final class IndexStore {

    public static final String INDEX_FILE_NAME = ".pic-tool-index.tsv";

    public record IndexEntry(long size, String relativePath) {
    }

    private final ConcurrentHashMap<String, IndexEntry> byMd5 = new ConcurrentHashMap<>();
    private volatile Path indexFile;

    public void open(Path targetDir) throws IOException {
        byMd5.clear();
        indexFile = targetDir.resolve(INDEX_FILE_NAME);
        if (!Files.isRegularFile(indexFile)) {
            return;
        }
        try (BufferedReader reader = Files.newBufferedReader(indexFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split("\t", 3);
                if (parts.length != 3) {
                    continue;
                }
                try {
                    byMd5.put(parts[0], new IndexEntry(Long.parseLong(parts[1]), parts[2]));
                } catch (NumberFormatException ignored) {
                    // 跳过损坏的行
                }
            }
        }
    }

    public void save() throws IOException {
        Path file = indexFile;
        if (file == null) {
            return;
        }
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try (BufferedWriter writer = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, IndexEntry> e : byMd5.entrySet()) {
                writer.write(e.getKey());
                writer.write('\t');
                writer.write(Long.toString(e.getValue().size()));
                writer.write('\t');
                writer.write(e.getValue().relativePath());
                writer.newLine();
            }
        }
        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public IndexEntry get(String md5) {
        return md5 == null ? null : byMd5.get(md5);
    }

    public void put(String md5, long size, String relativePath) {
        byMd5.put(md5, new IndexEntry(size, relativePath));
    }

    public void replaceWith(Map<String, IndexEntry> newIndex) {
        byMd5.clear();
        byMd5.putAll(newIndex);
    }

    public int size() {
        return byMd5.size();
    }

    public boolean isOpened() {
        return indexFile != null;
    }
}
