package com.hackathon.insuranceportal.alerting;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the JSON log file and returns the error events that have appeared
 * since the previous scan.
 *
 * "Since the previous scan" is tracked with a byte offset persisted next to the
 * log file (a small {@code <logfile>.alertoffset} sidecar). Each scan reads from
 * the saved offset to the last complete line, then advances the offset. If the
 * file shrank (rotation/truncation) the offset resets to 0 so nothing is missed.
 */
@Component
public class LogErrorScanner {

    private static final Logger log = LoggerFactory.getLogger(LogErrorScanner.class);

    private final AlertingProperties props;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LogErrorScanner(AlertingProperties props) {
        this.props = props;
    }

    /**
     * Reads new lines since last scan and classifies them in a single pass:
     * infrastructure errors (mailed immediately) and business warnings
     * (accumulated for the threshold rule). Advances the offset once.
     */
    public synchronized ScanFindings scan() {
        Path logPath = Path.of(props.getLogFilePath());
        if (!Files.exists(logPath)) {
            log.debug("[alerting] Log file {} does not exist yet - nothing to scan", logPath.toAbsolutePath());
            return ScanFindings.empty();
        }

        Path offsetPath = Path.of(props.getLogFilePath() + ".alertoffset");
        long startOffset = readOffset(offsetPath);

        List<ErrorEvent> errors = new ArrayList<>();
        List<ErrorEvent> warnings = new ArrayList<>();
        try (RandomAccessFile raf = new RandomAccessFile(logPath.toFile(), "r")) {
            long fileLength = raf.length();
            if (startOffset > fileLength) {
                // File was truncated or rotated - start over from the beginning.
                log.info("[alerting] Log file shrank ({} < {}); resetting scan offset", fileLength, startOffset);
                startOffset = 0;
            }
            raf.seek(startOffset);

            String line;
            long lastCompleteLineEnd = startOffset;
            // readLine() reads bytes as ISO-8859-1; re-decode as UTF-8 to be safe.
            while ((line = raf.readLine()) != null) {
                lastCompleteLineEnd = raf.getFilePointer();
                String json = new String(line.getBytes(StandardCharsets.ISO_8859_1), StandardCharsets.UTF_8).trim();
                if (json.isEmpty()) {
                    continue;
                }
                classify(json, errors, warnings);
            }
            writeOffset(offsetPath, lastCompleteLineEnd);
        } catch (IOException e) {
            log.error("[alerting] Failed to scan log file {}: {}", logPath.toAbsolutePath(), e.getMessage());
        }

        return new ScanFindings(errors, warnings);
    }

    /** Back-compat helper: just the infrastructure errors from a scan. */
    public synchronized List<ErrorEvent> scanForNewErrors() {
        return scan().errors();
    }

    /** Parses one JSON line and adds it to the errors or warnings list, or ignores it. */
    private void classify(String json, List<ErrorEvent> errors, List<ErrorEvent> warnings) {
        try {
            JsonNode node = objectMapper.readTree(json);
            String status = text(node, "status");
            String error = text(node, "error");
            String eventType = text(node, "event_type");

            boolean isError = isAlertableError(error, eventType);
            boolean isWarning = !isError && "WARNING".equalsIgnoreCase(status);
            if (!isError && !isWarning) {
                return;
            }
            ErrorEvent event = new ErrorEvent(
                    text(node, "timestamp"),
                    orUnknown(text(node, "application_name")),
                    eventType,
                    text(node, "stage"),
                    status,
                    error,
                    text(node, "correlation_id"),
                    text(node, "server_id"));
            (isError ? errors : warnings).add(event);
        } catch (Exception e) {
            // A malformed line must never break a scan - skip it.
            log.debug("[alerting] Skipping unparseable log line: {}", e.getMessage());
        }
    }

    /**
     * A line is alertable only when its {@code error} or {@code event_type}
     * matches one of the configured infrastructure error types. Expected
     * business failures (now logged as WARNING with errors like
     * INVALID_CREDENTIALS or CLAIM_NOT_FOUND) are not on that list, so they are
     * intentionally ignored and never mailed.
     */
    private boolean isAlertableError(String error, String eventType) {
        for (String type : props.getErrorTypes()) {
            if (type == null || type.isBlank()) {
                continue;
            }
            if (matches(error, type) || matches(eventType, type)) {
                return true;
            }
        }
        return false;
    }

    private boolean matches(String value, String type) {
        return value != null && value.equalsIgnoreCase(type.trim());
    }

    /** Returns a trimmed text value, or null for missing/JSON-null nodes. */
    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        if (v == null || v.isNull()) {
            return null;
        }
        String s = v.asText();
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private String orUnknown(String s) {
        return s == null ? "UNKNOWN" : s;
    }

    private long readOffset(Path offsetPath) {
        try {
            if (Files.exists(offsetPath)) {
                return Long.parseLong(Files.readString(offsetPath).trim());
            }
        } catch (Exception e) {
            log.debug("[alerting] Could not read scan offset ({}), starting from 0", e.getMessage());
        }
        return 0L;
    }

    private void writeOffset(Path offsetPath, long offset) {
        try {
            Files.writeString(offsetPath, Long.toString(offset));
        } catch (IOException e) {
            log.warn("[alerting] Could not persist scan offset: {}", e.getMessage());
        }
    }
}
