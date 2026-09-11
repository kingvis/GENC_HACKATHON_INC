package com.hackathon.insuranceportal.alerting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * Thin client over the Resend REST API ({@code POST /emails}).
 * Uses Spring's RestClient (already on the classpath via spring-boot-starter-web),
 * so no extra dependency is needed.
 */
@Component
public class ResendEmailClient {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailClient.class);

    private final AlertingProperties props;
    private final RestClient restClient;

    public ResendEmailClient(AlertingProperties props) {
        this.props = props;
        this.restClient = RestClient.create();
    }

    /**
     * Sends one HTML email. Returns true on success. Never throws - a mail
     * failure should be logged, not crash the scheduled scan.
     */
    public boolean send(String subject, String html) {
        if (props.getResendApiKey() == null || props.getResendApiKey().isBlank()) {
            log.warn("[alerting] RESEND_API_KEY is not set - skipping email send");
            return false;
        }
        List<String> recipients = props.getMailTo();
        if (recipients == null || recipients.isEmpty()) {
            log.warn("[alerting] alerting.mail-to is empty - skipping email send");
            return false;
        }

        Map<String, Object> body = Map.of(
                "from", props.getMailFrom(),
                "to", recipients,
                "subject", subject,
                "html", html);

        try {
            String response = restClient.post()
                    .uri(props.getResendUrl())
                    .header("Authorization", "Bearer " + props.getResendApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);
            log.info("[alerting] Report emailed to {} (Resend response: {})", recipients, response);
            return true;
        } catch (Exception e) {
            log.error("[alerting] Failed to send error report via Resend: {}", e.getMessage());
            return false;
        }
    }
}
