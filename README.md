# PicTool - 图片/视频合并导入工具

基于 **Java 25 + Maven + JavaFX** 的桌面工具，用于把多个源目录中的图片/视频合并导入到一个目标目录，并按时间浏览。

## 功能

1. **GUI 操作**：图形界面选择多个源目录和一个目标目录，一键合并导入。
2. **MD5 去重加速**：记录目标目录下每个媒体文件的 `MD5 + 文件大小`，已存在的文件直接跳过；同名不同内容的文件自动改名（如 `照片 (1).jpg`）。
3. **索引持久化**：索引保存在目标目录下的 `.pic-tool-index.tsv` 文件中（重启/换机器不丢失），导入结束后自动写盘。
4. **时间范围与排序展示**：媒体库展示支持按开始/结束日期筛选，支持按拍摄时间升序/降序或按文件名排序；图片优先读取 EXIF 拍摄时间，视频取文件创建时间。缩略图分页展示，双击可用系统默认程序打开。
5. **免安装可执行程序**：通过 `jpackage` 打包，Windows 产出单一 exe 安装包（自定义图标、可选安装目录、自动创建快捷方式），macOS 产出 `PicTool.app`；产物自带精简版 Java 运行时，拷贝到未安装 JDK/JRE 的电脑即可运行。

## 环境要求（仅开发/打包需要）

- JDK 25（含 `jpackage`，Oracle JDK / Temurin 均可）
- Maven 3.9+

## 运行（开发调试）

```bash
mvn javafx:run
```

## 打包（免安装分发）

> 注意：`jpackage` 不能跨平台打包，Windows 程序需要在 Windows 上构建，macOS 程序需要在 macOS 上构建。

### Windows：单一 exe 安装包

构建 exe 安装包需要 **WiX Toolset 3.x**（`candle.exe` / `light.exe` 需在 PATH 中）：

1. 从 [WiX 3.14.1](https://github.com/wixtoolset/wix3/releases/tag/wix3141rtm) 下载 `wix314-binaries.zip`，解压后把目录加入 PATH（例如 `set PATH=C:\tools\wix314;%PATH%`）。
2. 执行：

```bash
mvn clean package -Ppackage-win
```

产物为单一文件 **`target/dist/PicTool-1.0.0.exe`**（约 130MB，内含精简 Java 运行时）。
拷到任意 Windows 电脑，双击运行安装向导（可选择安装目录、创建开始菜单/桌面快捷方式，无需管理员权限），完成后从开始菜单或桌面启动，**无需安装 JDK/JRE**。
重新打包前请先关闭正在运行的 PicTool（安装目录文件被占用会导致清理失败）。

应用图标位于 `src/packaging/pictool.ico`（16/32/48/256 多尺寸）。如需重新生成：

```bash
java packaging/IconGenerator.java
```

> 如需 `.msi` 安装包，把 `pom.xml` 中 `package-win` profile 的 `<type>EXE</type>` 改为 `<type>MSI</type>` 即可。

### macOS

```bash
mvn clean package -Ppackage-mac
```

产物：`target/dist/PicTool.app`，拷贝到任意 Mac（应用程序文件夹或其他位置）双击即可运行。
如果从网络下载后首次打开提示“无法打开”，在“系统设置 → 隐私与安全性”中允许，或执行：

```bash
xattr -d com.apple.quarantine PicTool.app
```

## 使用说明

1. 点击 **添加…** 选择一个或多个源目录（会递归扫描子目录）。
2. 点击 **浏览…** 选择目标目录，程序自动加载该目录下的历史索引。
3. 点击 **开始导入**：
   - 已存在的文件（MD5 命中且文件仍在）直接跳过；
   - 新文件复制进目标目录并更新索引；
   - 导入结束后自动保存索引并刷新“媒体展示”页。
4. 如果目标目录里已有大量历史文件而从未建过索引，点击 **重建目标索引** 先扫描一遍，可显著加快首次导入速度。
5. 在“媒体展示”页可指定开始/结束日期筛选，并可按时间升序/降序、文件名排序浏览；双击缩略图用系统程序打开原文件。

## 技术要点

- 索引文件：`<目标目录>/.pic-tool-index.tsv`，每行 `md5\tsize\t相对路径`，原子写入（临时文件 + move）。
- 图片 EXIF 拍摄时间：内置轻量 JPEG EXIF 解析器（无需第三方依赖）。
- 去重键：MD5（内容相同则大小必然相同）；命中索引时会二次确认目标文件仍存在，避免漏拷。
- 打包：`jpackage` 以 app-image 方式生成应用目录，内含 jlink 精简运行时。

## 支持的格式

- 图片：`jpg / jpeg / png / gif / bmp / webp`
- 视频：`mp4 / mov / avi / mkv / wmv / flv / m4v / 3gp / mpg / mpeg / ts / rmvb`

视频在展示页显示占位图（不生成视频帧缩略图），双击调用系统播放器打开。
