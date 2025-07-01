package com.golan;

import javafx.application.Platform;
import javafx.scene.control.TextArea;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class TextAreaOutputStream extends OutputStream {

    private final TextArea textArea;
    private final StringBuilder buffer = new StringBuilder();
    private final ScheduledExecutorService executor;

    public TextAreaOutputStream(TextArea textArea) {
        this.textArea = textArea;
        this.executor = Executors.newSingleThreadScheduledExecutor();
        executor.scheduleWithFixedDelay(() -> {
            if (buffer.length() > 0) {
                Platform.runLater(() -> {
                    textArea.appendText(buffer.toString());
                    buffer.setLength(0);
                });
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    @Override
    public void write(int b) throws IOException {
        buffer.append((char) b);
    }

    public void close() {
        executor.shutdown();
    }
}
