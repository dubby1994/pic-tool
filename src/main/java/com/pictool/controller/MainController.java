package com.pictool.controller;

import com.pictool.model.ImportStats;
import com.pictool.model.MediaItem;
import com.pictool.model.MediaType;
import com.pictool.service.GalleryService;
import com.pictool.service.ImportService;
import com.pictool.service.IndexStore;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Pagination;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class MainController {

    private static final int PAGE_SIZE = 40;
    private static final int GRID_COLUMNS = 5;
    private static final double THUMB_WIDTH = 170;
    private static final double THUMB_HEIGHT = 126;
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    @FXML
    private ListView<Path> sourceList;
    @FXML
    private TextField targetField;
    @FXML
    private Button addSourceBtn;
    @FXML
    private Button removeSourceBtn;
    @FXML
    private Button startBtn;
    @FXML
    private Button rebuildIndexBtn;
    @FXML
    private Button cancelBtn;
    @FXML
    private ProgressBar progressBar;
    @FXML
    private Label statusLabel;
    @FXML
    private TabPane mainTabs;
    @FXML
    private TextArea logArea;
    @FXML
    private DatePicker startDatePicker;
    @FXML
    private DatePicker endDatePicker;
    @FXML
    private ComboBox<GalleryService.SortOrder> sortCombo;
    @FXML
    private Button refreshGalleryBtn;
    @FXML
    private Label resultInfoLabel;
    @FXML
    private Pagination galleryPagination;

    private final IndexStore indexStore = new IndexStore();
    private Task<?> runningTask;
    private List<MediaItem> currentItems = List.of();

    @FXML
    private void initialize() {
        sourceList.setCellFactory(lv -> new ListCell<>() {
            @Override
            protected void updateItem(Path item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.toString());
            }
        });

        sortCombo.getItems().setAll(GalleryService.SortOrder.values());
        sortCombo.getSelectionModel().select(GalleryService.SortOrder.TIME_ASC);

        galleryPagination.setPageFactory(this::createGalleryPage);

        startDatePicker.valueProperty().addListener((obs, o, v) -> loadGallery());
        endDatePicker.valueProperty().addListener((obs, o, v) -> loadGallery());
        sortCombo.valueProperty().addListener((obs, o, v) -> loadGallery());

        log("欢迎使用 PicTool。请选择源目录和目标目录后开始导入。");
    }

    @FXML
    private void onAddSource() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择源目录");
        if (!sourceList.getItems().isEmpty()) {
            Path last = sourceList.getItems().get(sourceList.getItems().size() - 1);
            if (last.getParent() != null) {
                chooser.setInitialDirectory(last.getParent().toFile());
            }
        }
        File dir = chooser.showDialog(ownerWindow());
        if (dir != null) {
            Path path = dir.toPath();
            if (!sourceList.getItems().contains(path)) {
                sourceList.getItems().add(path);
            }
            log("添加源目录: " + path);
        }
    }

    @FXML
    private void onRemoveSource() {
        sourceList.getItems().removeAll(sourceList.getSelectionModel().getSelectedItems());
    }

    @FXML
    private void onBrowseTarget() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("选择目标目录");
        File dir = chooser.showDialog(ownerWindow());
        if (dir == null) {
            return;
        }
        Path target = dir.toPath();
        targetField.setText(target.toString());
        try {
            indexStore.open(target);
            log("目标目录: " + target + "，已加载索引 " + indexStore.size() + " 条记录");
            loadGallery();
        } catch (Exception e) {
            log("加载索引失败: " + e.getMessage());
        }
    }

    @FXML
    private void onStartImport() {
        if (sourceList.getItems().isEmpty()) {
            warn("请至少添加一个源目录");
            return;
        }
        String targetText = targetField.getText();
        if (targetText == null || targetText.isBlank()) {
            warn("请选择目标目录");
            return;
        }
        Path target = Path.of(targetText);
        try {
            indexStore.open(target);
        } catch (Exception e) {
            warn("加载索引失败: " + e.getMessage());
            return;
        }

        ImportService.ImportTask task =
                ImportService.importTask(sourceList.getItems(), target, indexStore);
        runningTask = task;
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        setRunning(true);

        task.setOnSucceeded(e -> {
            ImportStats stats = task.getValue();
            log("导入完成 -> " + stats);
            for (String failure : task.getFailures()) {
                log("  失败详情: " + failure);
            }
            unbindAndFinish("完成: " + stats);
            loadGallery();
        });
        task.setOnFailed(e -> {
            log("导入出错: " + task.getException().getMessage());
            unbindAndFinish("出错");
        });
        task.setOnCancelled(e -> unbindAndFinish("已取消"));
        new Thread(task, "pic-tool-import").start();
    }

    @FXML
    private void onRebuildIndex() {
        String targetText = targetField.getText();
        if (targetText == null || targetText.isBlank()) {
            warn("请先选择目标目录");
            return;
        }
        Path target = Path.of(targetText);
        Task<Integer> task = ImportService.createRebuildTask(target, indexStore);
        runningTask = task;
        statusLabel.textProperty().bind(task.messageProperty());
        progressBar.progressProperty().bind(task.progressProperty());
        setRunning(true);
        task.setOnSucceeded(e -> {
            log("索引重建完成，共 " + task.getValue() + " 条记录");
            unbindAndFinish("索引已重建");
        });
        task.setOnFailed(e -> {
            log("重建索引出错: " + task.getException().getMessage());
            unbindAndFinish("出错");
        });
        task.setOnCancelled(e -> unbindAndFinish("已取消"));
        new Thread(task, "pic-tool-rebuild").start();
    }

    @FXML
    private void onCancel() {
        if (runningTask != null) {
            runningTask.cancel();
        }
    }

    @FXML
    private void onRefreshGallery() {
        loadGallery();
    }

    private void loadGallery() {
        String targetText = targetField.getText();
        if (targetText == null || targetText.isBlank()) {
            currentItems = List.of();
            updateGalleryView();
            return;
        }
        Task<List<MediaItem>> task = GalleryService.createLoadTask(
                Path.of(targetText),
                startDatePicker.getValue(),
                endDatePicker.getValue(),
                sortCombo.getValue());
        task.setOnSucceeded(e -> {
            currentItems = task.getValue();
            resultInfoLabel.setText("共 " + currentItems.size() + " 个文件");
            updateGalleryView();
        });
        task.setOnFailed(e -> log("加载媒体库失败: " + task.getException().getMessage()));
        new Thread(task, "pic-tool-gallery").start();
    }

    private void updateGalleryView() {
        int pages = Math.max(1, (int) Math.ceil(currentItems.size() / (double) PAGE_SIZE));
        // 重新设置 pageFactory 以强制刷新当前页内容
        galleryPagination.setPageFactory(this::createGalleryPage);
        galleryPagination.setPageCount(pages);
        galleryPagination.setCurrentPageIndex(0);
        if (currentItems.isEmpty()) {
            resultInfoLabel.setText("共 0 个文件");
        }
    }

    private Node createGalleryPage(int pageIndex) {
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.setPadding(new Insets(10));
        for (int i = 0; i < GRID_COLUMNS; i++) {
            ColumnConstraints constraints = new ColumnConstraints();
            constraints.setPercentWidth(100.0 / GRID_COLUMNS);
            grid.getColumnConstraints().add(constraints);
        }
        int from = pageIndex * PAGE_SIZE;
        int to = Math.min(from + PAGE_SIZE, currentItems.size());
        int col = 0;
        int row = 0;
        for (int i = from; i < to; i++) {
            grid.add(createMediaCell(currentItems.get(i)), col, row);
            col++;
            if (col == GRID_COLUMNS) {
                col = 0;
                row++;
            }
        }
        return grid;
    }

    private Node createMediaCell(MediaItem item) {
        StackPane thumb = new StackPane();
        thumb.setPrefSize(THUMB_WIDTH, THUMB_HEIGHT);
        thumb.getStyleClass().add("media-thumb");
        Label placeholder = new Label(item.getType() == MediaType.VIDEO ? "视频" : "加载中…");
        placeholder.getStyleClass().add("media-placeholder-label");
        thumb.getChildren().add(placeholder);

        if (item.getType() == MediaType.IMAGE) {
            Image image = new Image(
                    item.getPath().toUri().toString(),
                    THUMB_WIDTH, THUMB_HEIGHT, true, true, true);
            image.progressProperty().addListener((obs, o, progress) -> {
                if (progress.doubleValue() >= 1 && !image.isError()) {
                    placeholder.setVisible(false);
                }
            });
            image.errorProperty().addListener((obs, o, error) -> {
                if (error) {
                    placeholder.setVisible(true);
                    placeholder.setText("无法预览");
                }
            });
            ImageView imageView = new ImageView(image);
            imageView.setFitWidth(THUMB_WIDTH);
            imageView.setFitHeight(THUMB_HEIGHT);
            imageView.setPreserveRatio(true);
            thumb.getChildren().add(imageView);
        } else {
            thumb.getStyleClass().add("video-thumb");
            placeholder.getStyleClass().add("video-label");
        }

        thumb.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                openExternally(item);
            }
        });

        Label nameLabel = new Label(item.getName());
        nameLabel.getStyleClass().add("media-name");
        Label timeLabel = new Label(item.getTime() == null ? "无时间信息" : item.getTime().format(TIME_FORMAT));
        timeLabel.getStyleClass().add("media-time");

        VBox cell = new VBox(3, thumb, nameLabel, timeLabel);
        cell.setAlignment(Pos.TOP_CENTER);
        cell.setPrefWidth(THUMB_WIDTH + 10);
        return cell;
    }

    private void openExternally(MediaItem item) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(item.getPath().toFile());
            } else {
                log("当前系统不支持打开文件操作");
            }
        } catch (Exception e) {
            log("打开文件失败: " + item.getName() + " - " + e.getMessage());
        }
    }

    private void setRunning(boolean running) {
        startBtn.setDisable(running);
        rebuildIndexBtn.setDisable(running);
        addSourceBtn.setDisable(running);
        removeSourceBtn.setDisable(running);
        cancelBtn.setDisable(!running);
    }

    private void unbindAndFinish(String message) {
        statusLabel.textProperty().unbind();
        progressBar.progressProperty().unbind();
        statusLabel.setText(message);
        progressBar.setProgress(0);
        setRunning(false);
        runningTask = null;
    }

    private void warn(String message) {
        log(message);
        statusLabel.setText(message);
    }

    private void log(String message) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        logArea.appendText("[" + timestamp + "] " + message + "\n");
    }

    private Window ownerWindow() {
        return startBtn.getScene() != null ? startBtn.getScene().getWindow() : null;
    }
}
