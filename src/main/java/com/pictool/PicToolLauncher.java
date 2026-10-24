package com.pictool;

import javafx.application.Application;

/**
 * 启动入口。单独使用一个非 Application 子类的启动类，
 * 避免 JDK 25 启动器对 Application 主类的模块化 JavaFX 校验
 * （否则在 classpath 模式下会报“缺少 JavaFX 运行时组件”）。
 */
public final class PicToolLauncher {

    private PicToolLauncher() {
    }

    public static void main(String[] args) {
        Application.launch(PicToolApp.class, args);
    }
}
