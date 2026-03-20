package geneticrace.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Runs an external process with separate stderr consumption to prevent deadlock.
 * Package-private: used only by PythonService.
 */
class ProcessRunner {
    private static final Logger LOGGER = Logger.getLogger(ProcessRunner.class.getName());

    record Result(String stdout, String stderr, int exitCode, boolean timedOut) {}

    /**
     * Executes a command and captures stdout/stderr.
     * Stderr is read on a separate daemon thread to prevent buffer deadlock.
     */
    Result run(List<String> command, long timeoutSeconds) throws IOException, InterruptedException {
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(false);

        Process process = pb.start();

        // Read stderr in a separate thread to prevent deadlock:
        // if both stdout and stderr buffers fill, the process blocks and
        // sequential reads on the Java side would also block.
        StringBuilder stderr = new StringBuilder();
        Thread stderrReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getErrorStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    stderr.append(line).append("\n");
                }
            } catch (IOException e) {
                LOGGER.log(Level.FINE, "Error reading process stderr", e);
            }
        }, "stderr-reader");
        stderrReader.setDaemon(true);
        stderrReader.start();

        // Read stdout on the calling thread
        StringBuilder stdout = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                stdout.append(line);
            }
        }

        boolean completed = process.waitFor(timeoutSeconds, TimeUnit.SECONDS);

        if (!completed) {
            process.destroyForcibly();
            stderrReader.interrupt();
            return new Result("", stderr.toString(), -1, true);
        }

        // Wait for stderr reader to finish (it should be done since process exited)
        stderrReader.join(1000);

        return new Result(stdout.toString(), stderr.toString(), process.exitValue(), false);
    }
}
