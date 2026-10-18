import org.junit.runner.*;
import org.junit.runner.notification.*;
import java.util.*;
public final class TestEvidence {
    static String quote(String s) { return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r") + "\""; }
    public static void main(String[] args) throws Exception {
        List<String> skipped = new ArrayList<>(), failed = new ArrayList<>();
        JUnitCore core = new JUnitCore();
        core.addListener(new RunListener() {
            public void testAssumptionFailure(Failure f) { skipped.add(f.getDescription().toString()); }
            public void testFailure(Failure f) { failed.add(f.getDescription().toString() + ": " + f.getTrace()); }
        });
        Class<?>[] classes = new Class<?>[args.length];
        for (int i = 0; i < args.length; i++) classes[i] = Class.forName(args[i]);
        Result r = core.run(classes);
        System.out.println("{\"reported_tests\":" + r.getRunCount() + ",\"passed_tests\":" + (r.getRunCount()-r.getFailureCount()-skipped.size()) + ",\"failed_tests\":" + r.getFailureCount() + ",\"ignored_tests\":" + r.getIgnoreCount() + ",\"assumption_skipped_tests\":" + skipped.size() + ",\"duration_ms\":" + r.getRunTime() + ",\"skipped\":[" + String.join(",", skipped.stream().map(TestEvidence::quote).toList()) + "],\"failures\":[" + String.join(",", failed.stream().map(TestEvidence::quote).toList()) + "]}");
        if (!r.wasSuccessful()) System.exit(1);
    }
}
