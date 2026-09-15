package com.hackathon.insuranceportal.alerting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates events per calendar date and fires once a daily threshold is
 * crossed. Shared by {@link WarningAggregator} (business warnings) and
 * {@link ErrorAggregator} (infrastructure errors / "system failure").
 *
 * The rule ("take the date and the count; when it exceeds the threshold, send a
 * report"): events are buffered against the current date. Each scan adds the
 * newly-seen events; when the buffered count reaches the threshold the whole
 * batch is returned for emailing and the buffer is cleared, so counting starts
 * fresh for the next batch. A new calendar day resets the buffer first.
 *
 * State is persisted to a small JSON sidecar (log file + {@code suffix}) so the
 * running count survives an application restart.
 */
public abstract class ThresholdAggregator {

    private static final Logger log = LoggerFactory.getLogger(ThresholdAggregator.class);

    /** Persisted buffer: the date being counted and the events seen so far. */
    public record State(String date, List<ErrorEvent> events) {
    }

    /** Result of feeding new events in: whether to mail, the batch, and buffered size. */
    public record Check(boolean triggered, List<ErrorEvent> batch, int bufferedAfter) {
    }

    private final AlertingProperties props;
    private final String suffix;
    private final String label;
    private final ObjectMapper objectMapper = new ObjectMapper();

    protected ThresholdAggregator(AlertingProperties props, String suffix, String label) {
        this.props = props;
        this.suffix = suffix;
        this.label = label;
    }

    private Path statePath() {
        return Path.of(props.getLogFilePath() + suffix);
    }

    /**
     * Adds {@code newEvents} to today's buffer and reports whether the threshold
     * was crossed. {@code today} is passed in so the rule is easy to unit-test
     * across date boundaries.
     */
    public synchronized Check addAndCheck(LocalDate today, List<ErrorEvent> newEvents, int threshold) {
        State state = load();

        // New calendar day -> start the count over.
        List<ErrorEvent> buffer = (state != null && today.toString().equals(state.date()))
                ? new ArrayList<>(state.events())
                : new ArrayList<>();

        if (newEvents != null) {
            buffer.addAll(newEvents);
        }

        if (!buffer.isEmpty() && buffer.size() >= threshold) {
            List<ErrorEvent> batch = List.copyOf(buffer);
            save(new State(today.toString(), List.of()));  // clear, keep the date
            log.info("[alerting] {} threshold reached: {} {}(s) on {} (threshold {})",
                    label, batch.size(), label.toLowerCase(), today, threshold);
            return new Check(true, batch, 0);
        }

        save(new State(today.toString(), buffer));
        return new Check(false, List.of(), buffer.size());
    }

    private State load() {
        Path path = statePath();
        try {
            if (Files.exists(path)) {
                return objectMapper.readValue(Files.readAllBytes(path), State.class);
            }
        } catch (Exception e) {
            log.warn("[alerting] Could not read {} state ({}); starting fresh", label, e.getMessage());
        }
        return null;
    }

    private void save(State state) {
        try {
            Files.write(statePath(), objectMapper.writeValueAsBytes(state));
        } catch (Exception e) {
            log.warn("[alerting] Could not persist {} state: {}", label, e.getMessage());
        }
    }
}
