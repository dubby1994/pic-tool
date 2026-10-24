package com.pictool;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.io.IOException;
import java.io.InputStream;
import java.util.Objects;

public class PicToolApp extends Application {

    @Override
    public void start(Stage stage) throws IOException {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
        Parent root = loader.load();

        Scene scene = new Scene(root, 1100, 760);
        try (InputStream css = getClass().getResourceAsStream("/css/style.css")) {
            if (css != null) {
                scene.getStylesheets().add(
                        Objects.requireNonNull(getClass().getResource("/css/style.css")).toExternalForm());
            }
        }

        stage.setTitle("PicTool - 图片/视频合并导入工具");
        loadWindowIcons(stage);
        stage.setScene(scene);
        stage.setMinWidth(900);
        stage.setMinHeight(600);
        stage.show();
    }

    private void loadWindowIcons(Stage stage) {
        for (int size : new int[]{16, 32, 48, 256}) {
            String path = "/icons/pictool-" + size + ".png";
            if (getClass().getResource(path) != null) {
                stage.getIcons().add(new Image(path));
            }
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}
