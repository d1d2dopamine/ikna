package dev.ikna.desktop;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/** Shared assertions for JUnit and a standalone JDK 17 run; no Windows/GPU claim. */
public final class WindowFrameLayoutChecks {
    private static void require(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    public static void firstResizedPicture() {
        WindowFrameLayout layout = new WindowFrameLayout();
        int[] child = {1180, 800};
        layout.observeComposeSize(1475, 1000); // Existing viewport at 125%.
        // Model the pinned immediate path: surface is now 1536x864, but
        // the native child and Compose constraints have not completed layout.
        require(child[0] != 1536 && layout.getComposeWidth() != 1920, "fixture must begin stale");
        List<String> calls = new ArrayList<>();
        boolean prepared = layout.render(1920, 1080, 1.25f, () -> {
            calls.add("layout");
            child[0] = 1536; child[1] = 864;
            layout.observeComposeSize(1920, 1080);
        }, () -> {
            calls.add("draw");
            require(child[0] == 1536 && child[1] == 864, "first resized draw uses stale child bounds");
            require(layout.getComposeWidth() == 1920 && layout.getComposeHeight() == 1080,
                    "first resized draw uses old Compose constraints");
        });
        require(prepared && calls.equals(List.of("layout", "draw")), "layout must precede the same picture");
    }

    public static void steadyFramesAndScaleChanges() {
        WindowFrameLayout layout = new WindowFrameLayout();
        AtomicInteger prepares = new AtomicInteger(), draws = new AtomicInteger();
        for (int i = 0; i < 100; i++) layout.render(1200, 800, 1, prepares::incrementAndGet, draws::incrementAndGet);
        require(prepares.get() == 1 && draws.get() == 100, "steady animation relayouts or skips frames");
        layout.render(1200, 800, 1.25f, prepares::incrementAndGet, draws::incrementAndGet);
        require(prepares.get() == 2, "scale-only change must prepare again, even at identical pixel size");
        layout.render(1201, 800, 1.25f, prepares::incrementAndGet, draws::incrementAndGet);
        layout.render(1201, 801, 1.25f, prepares::incrementAndGet, draws::incrementAndGet);
        require(prepares.get() == 4 && draws.get() == 103, "width/height change must each prepare");
    }

    public static void nativeInvalidationAndMinimizeReturn() {
        WindowFrameLayout layout = new WindowFrameLayout();
        AtomicInteger prepares = new AtomicInteger();
        layout.render(1200, 800, 1, prepares::incrementAndGet, () -> {});
        layout.invalidate();
        layout.invalidate();
        layout.render(1200, 800, 1, prepares::incrementAndGet, () -> {});
        require(prepares.get() == 2, "return at the same size needs one preparation, not one per event");
        layout.invalidate();
        layout.render(1200, 800, 1, () -> { prepares.incrementAndGet(); layout.invalidate(); }, () -> {});
        require(layout.needsPreparation(1200, 800, 1), "invalidation inside layout was lost");
        layout.render(1200, 800, 1, prepares::incrementAndGet, layout::invalidate);
        require(layout.needsPreparation(1200, 800, 1), "invalidation inside draw was lost");
    }

    public static void exceptionsAndZeroSize() {
        WindowFrameLayout layout = new WindowFrameLayout();
        AtomicInteger draws = new AtomicInteger();
        try {
            layout.render(1200, 800, 1, () -> { throw new IllegalArgumentException("layout failed"); },
                    draws::incrementAndGet);
            throw new AssertionError("preparation failure was swallowed");
        } catch (IllegalArgumentException expected) { }
        require(draws.get() == 0 && layout.needsPreparation(1200, 800, 1), "failed preparation committed");
        try {
            layout.render(1200, 800, 1, () -> {}, () -> { throw new IllegalArgumentException("draw failed"); });
            throw new AssertionError("draw failure was swallowed");
        } catch (IllegalArgumentException expected) { }
        layout.render(1200, 800, 1, () -> { throw new AssertionError("successful layout was lost"); },
                draws::incrementAndGet);
        for (int[] size : new int[][] {{0, 0}, {1200, 0}, {0, 800}}) {
            layout.render(size[0], size[1], 1,
                    () -> { throw new AssertionError("layout on a zero-size picture"); }, draws::incrementAndGet);
        }
        require(draws.get() == 4, "delegate must still run on zero-size pictures");
    }

    public static void recursiveRecordingIsNotCommitted() {
        WindowFrameLayout layout = new WindowFrameLayout();
        try {
            layout.render(1200, 800, 1,
                    () -> layout.render(1200, 800, 1, () -> {}, () -> {}), () -> {});
            throw new AssertionError("recursive recording accepted");
        } catch (IllegalStateException expected) { }
        require(layout.needsPreparation(1200, 800, 1), "recursive preparation committed stale state");
        require(layout.render(1200, 800, 1, () -> {}, () -> {}), "guard was not released after failure");
    }

    public static void boundedTraceAndShutdown() throws Exception {
        Path folder = Files.createTempDirectory("ikna-surface-trace-check-");
        Path path = folder.resolve("window-surface.log");
        AtomicInteger failures = new AtomicInteger();
        WindowFrameTrace trace = new WindowFrameTrace(path, error -> failures.incrementAndGet());
        trace.record("one\ntwo\rthree");
        trace.record("x".repeat(2048));
        for (int i = 0; i < 10000; i++) trace.record("picture=" + i);
        require(!trace.isRecording(), "trace must stop collecting once capped");
        trace.close(); trace.close(); trace.record("after-close");
        require(trace.awaitClosed(5, TimeUnit.SECONDS), "writer did not drain");
        List<String> lines = Files.readAllLines(path);
        require(failures.get() == 0 && lines.size() == WindowFrameTrace.MAX_RECORDS + 2, "trace is not bounded");
        require(lines.get(1).equals("one two three") && lines.get(2).length() == 1024, "line limits/escaping");
        require(lines.get(lines.size() - 1).startsWith("trace-limit"), "truncation is not explicit");
        WindowFrameTrace next = new WindowFrameTrace(path, error -> failures.incrementAndGet());
        next.record("next-process"); next.close();
        require(next.awaitClosed(5, TimeUnit.SECONDS), "second trace did not drain");
        require(Files.readAllLines(path.resolveSibling("window-surface.log.previous")).equals(lines),
                "previous diagnostic was not retained");
        require(Files.readAllLines(path).size() == 2, "new run must not accumulate old records");
    }

    public static void failedTraceDoesNotBreakRendering() throws Exception {
        Path blocker = Files.createTempFile("ikna-surface-blocker-", ".tmp");
        AtomicInteger failures = new AtomicInteger();
        WindowFrameTrace trace = new WindowFrameTrace(blocker.resolve("log"), error -> failures.incrementAndGet());
        for (int i = 0; i < 50; i++) trace.record("picture=" + i);
        trace.close();
        require(trace.awaitClosed(5, TimeUnit.SECONDS), "failed writer did not stop");
        require(failures.get() == 1 && !trace.isRecording(), "trace failure must be reported once and disabled");
    }

    public static void main(String[] args) throws Exception {
        firstResizedPicture(); steadyFramesAndScaleChanges(); nativeInvalidationAndMinimizeReturn();
        exceptionsAndZeroSize(); recursiveRecordingIsNotCommitted(); boundedTraceAndShutdown();
        failedTraceDoesNotBreakRendering();
        System.out.println("PASS: 7 picture-layout/trace groups; simulated recording order, not Windows rendering");
    }
}
