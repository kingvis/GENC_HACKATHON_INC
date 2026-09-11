package com.hackathon.insuranceportal.alerting;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Accumulates business WARNING events per calendar date and decides when to fire
 * a Warning Report.
 *
 * The rule (per the requirement "take the date and warning count; if more than
 * the threshold, trigger a mail"): warnings are buffered against the current
 * date. Each scan adds the newly-seen warnings; when the buffered count reaches
 * {@link AlertingProperties#getWarningThreshold()} the whole batch is returned
 * for emailing and the buffer is cleared so counting starts fresh for the next
 * batch. When the date rolls over to a new day, the buffer resets first.
 *
 * State is persisted to a small JSON sidecar next to the log file so the running
 * count survives an application restart.
 */
@Component
public class WarningAggregator {

    private static final Logger log = LoggerFactory.getLogger(WarningAggregator.class);

    /** Persisted buffer: the date being counted and the warnings seen so far. */
    public record WarningState(String date, List<ErrorEvent> warnings) {
    }

    /** Result of feeding new warnings in: whether to mail, and the current buffered size. */
    public record WarningCheck(boolean triggered, List<ErrorEvent> batch, int bufferedAfter) {
    }

    private final AlertingProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public WarningAggregator(AlertingProperties props) {
        this.props = props;
    }

    private Path statePath() {
        return Path.of(props.getLogFilePath() + ".warnstate");
    }

    /**
     * Adds {@code newWarnings} to today's buffer and reports whether the
     * threshold was crossed. {@code today} is passed in so the rule is easy to
     * unit-test across date boundaries.
     */
    public synchronized WarningCheck addAndCheck(LocalDate today, List<ErrorEvent> newWarnings, int threshold) {
        WarningState state = load();

        // New calendar day -> start the count over.
        List<ErrorEvent> buffer = (state != null && today.toString().equals(state.date()))
                ? new ArrayList<>(state.warnings())
                : new ArrayList<>();

        if (newWarnings != null) {
            buffer.addAll(newWarnings);
        }

        if (buffer.size() >= threshold && !buffer.isEmpty()) {
            List<ErrorEvent> batch = List.copyOf(buffer);
            save(new WarningState(today.toString(), List.of()));  // clear, keep the date
            log.info("[alerting] Warning threshold reached: {} warning(s) on {} (threshold {})",
                    batch.size(), today, threshold);
            return new WarningCheck(true, batch, 0);
        }

        save(new WarningState(today.toString(), buffer));
        return new WarningCheck(false, List.of(), buffer.size());
    }

    private WarningState load() {
        Path path = statePath();
        try {
            if (Files.exists(path)) {
                return objectMapper.readValue(Files.readAllBytes(path), WarningState.class);
            }
        } catch (Exception e) {
            log.warn("[alerting] Could not read warning state ({}); starting fresh", e.getMessage());
        }
        return null;
    }

    private void save(WarningState state) {
        try {
            Files.write(statePath(), objectMapper.writeValueAsBytes(state));
        } catch (Exception e) {
            log.warn("[alerting] Could not persist warning state: {}", e.getMessage());
        }
    }
}
