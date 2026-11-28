package com.menta.virtual.infrastructure.cdn;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.LoggerFactory;

/**
 * Test helper: captures the events of one logger in a thread-safe list (the CDN probe logs from the HTTP client's
 * executor thread, so a plain {@code ListAppender} would race with the assertions).
 */
public final class CapturedLogs extends AppenderBase<ILoggingEvent> implements AutoCloseable {

    private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();
    private final Logger logger;

    private CapturedLogs(String loggerName) {
        this.logger = (Logger) LoggerFactory.getLogger(loggerName);
    }

    /** Starts capturing {@code source}'s logger at DEBUG until {@link #close()}. */
    public static CapturedLogs of(Class<?> source) {
        return of(source.getName());
    }

    /** Starts capturing the logger called {@code loggerName} (and, by inheritance, its children) at DEBUG. */
    public static CapturedLogs of(String loggerName) {
        CapturedLogs captured = new CapturedLogs(loggerName);
        captured.logger.setLevel(Level.DEBUG);
        captured.start();
        captured.logger.addAppender(captured);
        return captured;
    }

    @Override
    protected void append(ILoggingEvent event) {
        events.add(event);
    }

    /** Snapshot of every captured event, in order. */
    public List<ILoggingEvent> events() {
        return List.copyOf(events);
    }

    /** Every captured formatted message, one per line. */
    public String text() {
        return String.join("\n", events().stream().map(ILoggingEvent::getFormattedMessage).toList());
    }

    /** Formatted messages captured at exactly {@code level}. */
    public List<String> messages(Level level) {
        return events().stream().filter(e -> e.getLevel() == level).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Override
    public void close() {
        logger.detachAppender(this);
        stop();
    }
}
