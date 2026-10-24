package com.pictool.model;

public final class ImportStats {

    public int total;
    public int copied;
    public int skipped;
    public int failed;

    @Override
    public String toString() {
        return "共扫描 " + total + " 个媒体文件：复制 " + copied + " 个，跳过(已存在) " + skipped + " 个，失败 " + failed + " 个";
    }
}
