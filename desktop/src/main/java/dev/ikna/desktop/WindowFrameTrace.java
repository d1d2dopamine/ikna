package dev.ikna.desktop;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** Bounded DEV-only geometry trace. Disk I/O never runs on the AWT/render thread. */
public final class WindowFrameTrace implements AutoCloseable {
    public static final int MAX_RECORDS = 512;
    private static final int MAX_LINE = 1024;
    private final Path path;
    private final Consumer<Throwable> onFailure;
    private final AtomicBoolean failed = new AtomicBoolean();
    private final ExecutorService writer = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "ikna-window-surface-trace");
        thread.setDaemon(true);
        return thread;
    });
    private int records;
    private boolean closed;

    public WindowFrameTrace(Path path, Consumer<Throwable> onFailure) {
        this.path = path;
        this.onFailure = onFailure;
        writer.execute(() -> {
            try {
                Files.createDirectories(path.toAbsolutePath().getParent());
                if (Files.exists(path)) {
                    Files.move(path, path.resolveSibling(path.getFileName() + ".previous"),
                            StandardCopyOption.REPLACE_EXISTING);
                }
                Files.writeString(path,
                        "ikna-window-surface-v1; AWT bounds=logical units; picture/root=px; " +
                        "records picture recording, not GPU Present/DWM frames; pid=" +
                        ProcessHandle.current().pid() + "\n",
                        StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (IOException error) { fail(error); }
        });
    }

    public synchronized boolean isRecording() {
        return !closed && !failed.get() && records <= MAX_RECORDS;
    }

    public synchronized void record(String line) {
        if (closed || failed.get() || records > MAX_RECORDS) return;
        if (records++ == MAX_RECORDS) {
            submit("trace-limit reached; further records omitted");
        } else {
            String oneLine = line.replace('\r', ' ').replace('\n', ' ');
            submit(oneLine.substring(0, Math.min(oneLine.length(), MAX_LINE)));
        }
    }

    private void submit(String line) {
        writer.execute(() -> {
            if (failed.get()) return;
            try {
                Files.writeString(path, line + "\n", StandardCharsets.UTF_8,
                        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            } catch (IOException error) { fail(error); }
        });
    }

    private void fail(Throwable error) {
        if (failed.compareAndSet(false, true)) onFailure.accept(error);
    }

    @Override public synchronized void close() {
        if (closed) return;
        closed = true;
        writer.shutdown(); // No EDT wait: already queued records drain in order.
    }

    /** Only the headless check waits; the application never calls this on its render thread. */
    public boolean awaitClosed(long timeout, TimeUnit unit) throws InterruptedException {
        return writer.awaitTermination(timeout, unit);
    }
}
