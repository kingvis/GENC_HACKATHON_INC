package com.hackathon.insuranceportal.alerting;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Turns a list of events into a polished, email-client-safe HTML report grouped
 * by service ({@code application_name}): a header banner, an at-a-glance summary,
 * and a zebra-striped detail table per service with severity badges.
 *
 * The same layout renders two report kinds - a red {@code ERROR} report (infra
 * failures) and an amber {@code WARNING} report (business warnings over the
 * threshold) - selected via {@link Kind}.
 *
 * Everything is table-based with inline styles - the only layout that renders
 * consistently across Gmail, Outlook and Apple Mail.
 */
@Component
public class ErrorReportBuilder {

    private static final DateTimeFormatter TIMESTAMP_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm 'UTC'").withZone(ZoneOffset.UTC);

    // Shared neutrals.
    private static final String NAVY = "#0b2545";
    private static final String PAGE_BG = "#eef1f5";
    private static final String BORDER = "#e3e8ee";
    private static final String MUTED = "#6b7a8d";

    /** A report flavour: its wording and accent colours. */
    private enum Kind {
        ERROR("Error Report", "error", "#c62828", "#fdecea", "#7a1c1c",
                "new infrastructure error events were found since the last scan."),
        WARNING("Warning Report", "warning", "#b45309", "#fef3c7", "#7a4a12",
                "the number of warning events crossed the alert threshold.");

        final String title;
        final String noun;
        final String accent;
        final String stripBg;
        final String stripText;
        final String footerReason;

        Kind(String title, String noun, String accent, String stripBg, String stripText, String footerReason) {
            this.title = title;
            this.noun = noun;
            this.accent = accent;
            this.stripBg = stripBg;
            this.stripText = stripText;
            this.footerReason = footerReason;
        }
    }

    /** Groups by service, preserving a stable alphabetical order. */
    public Map<String, List<ErrorEvent>> groupByService(List<ErrorEvent> events) {
        return events.stream().collect(Collectors.groupingBy(
                ErrorEvent::service, TreeMap::new, Collectors.toList()));
    }

    // ---- Public API: error reports (unchanged behaviour) -------------------
    public String subject(List<ErrorEvent> events) {
        return subject(events, Kind.ERROR);
    }

    public String buildHtml(List<ErrorEvent> events) {
        return render(events, Kind.ERROR);
    }

    // ---- Public API: warning reports --------------------------------------
    public String warningSubject(List<ErrorEvent> events) {
        return subject(events, Kind.WARNING);
    }

    public String buildWarningHtml(List<ErrorEvent> events) {
        return render(events, Kind.WARNING);
    }

    private String subject(List<ErrorEvent> events, Kind kind) {
        int services = groupByService(events).size();
        return "[Insurance Portal] " + events.size() + " " + kind.noun + " event(s) across "
                + services + " service(s)";
    }

    private String render(List<ErrorEvent> events, Kind kind) {
        Map<String, List<ErrorEvent>> byService = groupByService(events);
        int total = events.size();
        int services = byService.size();
        String generatedAt = TIMESTAMP_FMT.format(Instant.now());

        String font = "font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;";
        StringBuilder h = new StringBuilder();

        h.append("<!DOCTYPE html><html><head><meta charset=\"utf-8\">")
         .append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"></head>")
         .append("<body style=\"margin:0;padding:0;background:").append(PAGE_BG).append(";\">");

        // Hidden preheader - the grey snippet shown in the inbox list.
        h.append("<div style=\"display:none;max-height:0;overflow:hidden;opacity:0;color:").append(PAGE_BG).append(";\">")
         .append(total).append(" ").append(kind.noun).append("(s) across ").append(services).append(" service(s)</div>");

        // Centered container.
        h.append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"background:")
         .append(PAGE_BG).append(";padding:28px 12px;\"><tr><td align=\"center\">");
        h.append("<table role=\"presentation\" width=\"640\" cellpadding=\"0\" cellspacing=\"0\" style=\"width:640px;max-width:640px;")
         .append(font).append("background:#ffffff;border-radius:12px;overflow:hidden;")
         .append("box-shadow:0 2px 8px rgba(11,37,69,0.10);\">");

        // ---- Header banner ----
        h.append("<tr><td style=\"background:").append(NAVY).append(";padding:28px 32px;\">")
         .append("<div style=\"color:#9fb3c8;font-size:12px;letter-spacing:2px;text-transform:uppercase;font-weight:600;\">Insurance Portal</div>")
         .append("<div style=\"color:#ffffff;font-size:24px;font-weight:700;margin-top:6px;\">").append(kind.title).append("</div>")
         .append("<div style=\"color:#9fb3c8;font-size:13px;margin-top:8px;\">Generated ").append(escape(generatedAt)).append("</div>")
         .append("</td></tr>");

        // ---- Alert strip ----
        h.append("<tr><td style=\"background:").append(kind.stripBg).append(";border-left:4px solid ").append(kind.accent).append(";padding:16px 32px;\">")
         .append("<span style=\"color:").append(kind.accent).append(";font-size:16px;font-weight:700;\">")
         .append(total).append(" ").append(kind.noun).append(" event").append(total == 1 ? "" : "s").append("</span>")
         .append("<span style=\"color:").append(kind.stripText).append(";font-size:14px;\"> detected across ")
         .append(services).append(" service").append(services == 1 ? "" : "s").append("</span>")
         .append("</td></tr>");

        // ---- Summary: service -> count ----
        h.append("<tr><td style=\"padding:24px 32px 8px;\">")
         .append("<div style=\"font-size:12px;font-weight:700;letter-spacing:1px;text-transform:uppercase;color:")
         .append(MUTED).append(";margin-bottom:10px;\">Summary by service</div>")
         .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" style=\"border-collapse:collapse;\">");
        for (Map.Entry<String, List<ErrorEvent>> e : byService.entrySet()) {
            h.append("<tr>")
             .append("<td style=\"padding:10px 12px;border-bottom:1px solid ").append(BORDER)
             .append(";font-size:14px;color:").append(NAVY).append(";font-weight:600;\">")
             .append(escape(e.getKey())).append("</td>")
             .append("<td align=\"right\" style=\"padding:10px 12px;border-bottom:1px solid ").append(BORDER).append(";\">")
             .append(countPill(e.getValue().size(), kind)).append("</td>")
             .append("</tr>");
        }
        h.append("</table></td></tr>");

        // ---- Detail table per service ----
        for (Map.Entry<String, List<ErrorEvent>> e : byService.entrySet()) {
            h.append("<tr><td style=\"padding:20px 32px 4px;\">")
             .append("<div style=\"font-size:16px;font-weight:700;color:").append(NAVY)
             .append(";border-left:3px solid ").append(kind.accent).append(";padding-left:10px;margin-bottom:12px;\">")
             .append(escape(e.getKey())).append("</div>")
             .append("<table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" ")
             .append("style=\"border-collapse:collapse;width:100%;font-size:12px;\">")
             .append("<thead><tr style=\"background:").append(NAVY).append(";\">")
             .append(th("Time")).append(th("Event")).append(th("Stage"))
             .append(th("Status")).append(th(capitalize(kind.noun))).append(th("Correlation"))
             .append("</tr></thead><tbody>");
            boolean odd = true;
            for (ErrorEvent ev : e.getValue()) {
                String rowBg = odd ? "#ffffff" : "#f7f9fb";
                odd = !odd;
                h.append("<tr style=\"background:").append(rowBg).append(";\">")
                 .append(td(ev.timestamp()))
                 .append(td(ev.eventType()))
                 .append(td(ev.stage()))
                 .append(tdRaw(statusBadge(ev.status())))
                 .append(tdMono(ev.error(), kind.accent))
                 .append(td(ev.correlationId()))
                 .append("</tr>");
            }
            h.append("</tbody></table></td></tr>");
        }

        // ---- Footer ----
        h.append("<tr><td style=\"padding:22px 32px 28px;background:#f7f9fb;border-top:1px solid ").append(BORDER).append(";\">")
         .append("<div style=\"font-size:12px;color:").append(MUTED).append(";line-height:1.6;\">")
         .append("Automated scan of the structured log stream. You are receiving this because ")
         .append(kind.footerReason)
         .append("</div></td></tr>");

        h.append("</table></td></tr></table></body></html>");
        return h.toString();
    }

    private String countPill(int count, Kind kind) {
        return "<span style=\"display:inline-block;min-width:22px;text-align:center;background:" + kind.accent
                + ";color:#ffffff;font-size:13px;font-weight:700;padding:3px 10px;border-radius:11px;\">"
                + count + "</span>";
    }

    private String statusBadge(String status) {
        String s = status == null ? "-" : status;
        String bg, fg;
        if ("ERROR".equalsIgnoreCase(s) || "FAILED".equalsIgnoreCase(s)) {
            bg = "#fdecea"; fg = "#c62828";
        } else if ("WARNING".equalsIgnoreCase(s)) {
            bg = "#fef3c7"; fg = "#b45309";
        } else {
            bg = "#eef1f5"; fg = MUTED;
        }
        return "<span style=\"display:inline-block;background:" + bg + ";color:" + fg
                + ";font-size:11px;font-weight:700;letter-spacing:0.5px;padding:2px 8px;border-radius:4px;\">"
                + escape(s.toUpperCase()) + "</span>";
    }

    private String th(String s) {
        return "<th style=\"padding:9px 10px;text-align:left;color:#ffffff;font-size:11px;"
                + "font-weight:600;letter-spacing:0.5px;text-transform:uppercase;\">" + escape(s) + "</th>";
    }

    private String td(String s) {
        return "<td style=\"padding:9px 10px;border-bottom:1px solid " + BORDER + ";color:#33424f;"
                + "vertical-align:top;\">" + escape(s == null ? "-" : s) + "</td>";
    }

    /** A cell whose content is already trusted HTML (e.g. a badge span). */
    private String tdRaw(String innerHtml) {
        return "<td style=\"padding:9px 10px;border-bottom:1px solid " + BORDER + ";vertical-align:top;\">"
                + innerHtml + "</td>";
    }

    /** Monospaced cell for the raw error/warning string, so it stands out. */
    private String tdMono(String s, String accent) {
        return "<td style=\"padding:9px 10px;border-bottom:1px solid " + BORDER + ";vertical-align:top;"
                + "font-family:'Courier New',monospace;color:" + accent + ";font-weight:600;\">"
                + escape(s == null ? "-" : s) + "</td>";
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    private String escape(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
