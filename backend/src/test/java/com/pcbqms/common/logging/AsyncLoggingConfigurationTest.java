package com.pcbqms.common.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.util.LogbackMDCAdapter;
import ch.qos.logback.core.OutputStreamAppender;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AsyncLoggingConfigurationTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void blockedConsoleDoesNotBlockRequestsOrFileLogging() throws Exception {
        URL configuration = Objects.requireNonNull(
            getClass().getClassLoader().getResource("logback-spring.xml"),
            "logback-spring.xml must be present");
        Path logFile = temporaryDirectory.resolve("qms-api.log");
        BlockingOutputStream blockedConsole = new BlockingOutputStream();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        LoggerContext context = new LoggerContext();
        try {
            context.setMDCAdapter(new LogbackMDCAdapter());
            context.putProperty("LOG_FILE", logFile.toString());
            context.putProperty("QMS_LOG_ASYNC_QUEUE_SIZE", "1");
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(configuration);

            Logger root = context.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
            AsyncAppender asyncConsole = (AsyncAppender) root.getAppender("ASYNC_CONSOLE");
            @SuppressWarnings("unchecked")
            OutputStreamAppender<ILoggingEvent> console = (OutputStreamAppender<ILoggingEvent>)
                asyncConsole.iteratorForAppenders().next();
            assertThat(asyncConsole.isStarted()).isTrue();
            assertThat(console.isStarted()).isTrue();
            console.setOutputStream(blockedConsole);

            Logger logger = context.getLogger("async-logging-test");
            assertThat(logger.isInfoEnabled()).isTrue();
            Future<?> firstLog = executor.submit(() -> logger.info("first event"));

            assertThatCode(() -> firstLog.get(500, TimeUnit.MILLISECONDS)).doesNotThrowAnyException();
            assertThat(blockedConsole.awaitWrite(Duration.ofSeconds(2))).isTrue();
            logger.info("event waiting in the full console queue");
            Future<?> overflowLog = executor.submit(() ->
                logger.info("request completed while console queue is full"));
            assertThatCode(() -> overflowLog.get(500, TimeUnit.MILLISECONDS))
                .doesNotThrowAnyException();
            awaitFileContains(logFile, "first event");
        } finally {
            blockedConsole.release();
            context.stop();
            executor.shutdownNow();
        }
    }

    private static void awaitFileContains(Path logFile, String expected) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(2).toNanos();
        while (System.nanoTime() < deadline) {
            if (Files.exists(logFile) && Files.readString(logFile).contains(expected)) {
                return;
            }
            Thread.sleep(10);
        }
        assertThat(Files.exists(logFile) ? Files.readString(logFile) : "")
            .contains(expected);
    }

    private static final class BlockingOutputStream extends OutputStream {
        private final CountDownLatch writeStarted = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        @Override
        public void write(int value) throws IOException {
            writeStarted.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while simulating a blocked console", exception);
            }
        }

        boolean awaitWrite(Duration timeout) throws InterruptedException {
            return writeStarted.await(timeout.toMillis(), TimeUnit.MILLISECONDS);
        }

        void release() {
            release.countDown();
        }
    }
}
