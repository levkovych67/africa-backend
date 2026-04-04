package com.africe.backend.notification.email;

import com.africe.backend.common.dto.OrderResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import jakarta.mail.internet.MimeMessage;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

@Slf4j
@Component
public class EmailNotificationHandler {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper;
    private final String fromAddress;
    private final String fromName;
    private final String frontendUrl;

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd.MM.yyyy").withLocale(new Locale("uk")).withZone(ZoneId.of("Europe/Kyiv"));

    public EmailNotificationHandler(JavaMailSender mailSender,
                                     ObjectMapper objectMapper,
                                     @Value("${mail.from:noreply@africe.com}") String fromAddress,
                                     @Value("${mail.from-name:AFRICA}") String fromName,
                                     @Value("${frontend.url:http://localhost:3000}") String frontendUrl) {
        this.mailSender = mailSender;
        this.objectMapper = objectMapper;
        this.fromAddress = fromAddress;
        this.fromName = fromName;
        this.frontendUrl = frontendUrl;
    }

    public void handle(String type, String payload) {
        try {
            OrderResponse order = objectMapper.readValue(payload, OrderResponse.class);

            switch (type) {
                case "ORDER_CONFIRMED" -> sendEmail(order.email(),
                        "AFRICA SHOP \u2014 \u0417\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F #" + shortId(order.id()) + " \u043F\u0456\u0434\u0442\u0432\u0435\u0440\u0434\u0436\u0435\u043D\u043E",
                        buildConfirmedHtml(order));
                case "ORDER_CANCELLED" -> sendEmail(order.email(),
                        "AFRICA SHOP \u2014 \u0417\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F #" + shortId(order.id()) + " \u0441\u043A\u0430\u0441\u043E\u0432\u0430\u043D\u043E",
                        buildCancelledHtml(order));
                case "ORDER_SHIPPED" -> {
                    String ttn = order.shippingDetails() != null && order.shippingDetails().trackingNumber() != null
                            ? order.shippingDetails().trackingNumber() : "";
                    String subject = "AFRICA SHOP \u2014 \u0417\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F #" + shortId(order.id()) + " \u0432\u0456\u0434\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u043E";
                    if (!ttn.isEmpty()) subject += " (\u0422\u0422\u041D: " + ttn + ")";
                    sendEmail(order.email(), subject, buildShippedHtml(order));
                }
                case "ORDER_DELIVERED" -> sendEmail(order.email(),
                        "AFRICA SHOP \u2014 \u0417\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F #" + shortId(order.id()) + " \u0434\u043E\u0441\u0442\u0430\u0432\u043B\u0435\u043D\u043E",
                        buildDeliveredHtml(order));
                default -> log.debug("Unhandled email event type: {}", type);
            }
        } catch (Exception e) {
            log.error("Failed to handle email notification: {}", e.getMessage(), e);
            throw new RuntimeException("Email notification failed", e);
        }
    }

    private void sendEmail(String to, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(fromAddress, fromName);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.info("Email sent to {} \u2014 {}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Email send failed", e);
        }
    }

    // --- Status-specific builders ---

    private String buildConfirmedHtml(OrderResponse order) {
        String badge = statusBadge("#ECFDF5", "#A7F3D0", "#065F46",
                "\u2713 \u0417\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F \u043F\u0456\u0434\u0442\u0432\u0435\u0440\u0434\u0436\u0435\u043D\u043E",
                "\u0412\u0430\u0448\u0435 \u0437\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F \u043E\u0431\u0440\u043E\u0431\u043B\u044F\u0454\u0442\u044C\u0441\u044F");
        return buildEmailHtml(order, badge, "\u0412\u0456\u0434\u0441\u0442\u0435\u0436\u0438\u0442\u0438 \u0437\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F");
    }

    private String buildCancelledHtml(OrderResponse order) {
        String badge = statusBadge("#FFF1F2", "rgba(255,90,95,0.15)", "#FF5A5F",
                "\u0417\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F \u0441\u043A\u0430\u0441\u043E\u0432\u0430\u043D\u043E",
                "\u042F\u043A\u0449\u043E \u043C\u0430\u0454\u0442\u0435 \u043F\u0438\u0442\u0430\u043D\u043D\u044F \u2014 \u0437\u0432\u02BC\u044F\u0436\u0456\u0442\u044C\u0441\u044F \u0437 \u043D\u0430\u043C\u0438");
        return buildEmailHtml(order, badge, null);
    }

    private String buildShippedHtml(OrderResponse order) {
        String tracking = order.shippingDetails() != null && order.shippingDetails().trackingNumber() != null
                ? order.shippingDetails().trackingNumber() : "";

        String trackingPill = "";
        if (!tracking.isEmpty()) {
            trackingPill = "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-top:12px;\">" +
                    "<tr><td style=\"background-color:#FAFAF9;border-radius:12px;padding:10px 16px;\">" +
                    "<span style=\"font-family:Arial,sans-serif;font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:0.08em;color:#A8A29E;\">\u0422\u0422\u041D</span><br>" +
                    "<span style=\"font-family:'Courier New',monospace;font-size:14px;font-weight:500;color:#1C1917;letter-spacing:0.03em;\">" + esc(tracking) + "</span>" +
                    "</td></tr></table>";
        }

        String badge = "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-bottom:24px;\">" +
                "<tr><td style=\"background-color:#F5F5F4;border:1px solid #E7E5E4;border-radius:16px;padding:20px;\">" +
                "<span style=\"font-family:Arial,sans-serif;font-size:14px;font-weight:600;color:#1C1917;\">" +
                "\uD83D\uDCE6 \u0417\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F \u0432\u0456\u0434\u043F\u0440\u0430\u0432\u043B\u0435\u043D\u043E</span><br>" +
                "<span style=\"font-family:Arial,sans-serif;font-size:12px;color:#78716C;margin-top:4px;display:inline-block;\">" +
                "\u0412\u0456\u0434\u0441\u0442\u0435\u0436\u0443\u0439\u0442\u0435 \u043F\u043E\u0441\u0438\u043B\u043A\u0443 \u043D\u0430 \u0441\u0430\u0439\u0442\u0456 \u041D\u043E\u0432\u043E\u0457 \u041F\u043E\u0448\u0442\u0438</span>" +
                trackingPill +
                "</td></tr></table>";
        return buildEmailHtml(order, badge, "\u0412\u0456\u0434\u0441\u0442\u0435\u0436\u0438\u0442\u0438 \u0437\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F");
    }

    private String buildDeliveredHtml(OrderResponse order) {
        String badge = statusBadge("#ECFDF5", "#A7F3D0", "#065F46",
                "\u2713 \u0417\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F \u0434\u043E\u0441\u0442\u0430\u0432\u043B\u0435\u043D\u043E",
                "\u0414\u044F\u043A\u0443\u0454\u043C\u043E \u0437\u0430 \u043F\u043E\u043A\u0443\u043F\u043A\u0443!");
        return buildEmailHtml(order, badge, "\u041F\u043E\u0432\u0435\u0440\u043D\u0443\u0442\u0438\u0441\u044F \u0434\u043E \u043C\u0430\u0433\u0430\u0437\u0438\u043D\u0443");
    }

    // --- Shared template ---

    private String buildEmailHtml(OrderResponse order, String statusBadgeHtml, String ctaText) {
        String orderId = order.id() != null ? order.id() : "";
        String date = order.createdAt() != null ? DATE_FMT.format(order.createdAt()) : "";
        String orderUrl = frontendUrl + "/order/" + orderId;

        StringBuilder sb = new StringBuilder();

        // Doctype + wrapper
        sb.append("<!DOCTYPE html><html lang=\"uk\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1.0\"></head>");
        sb.append("<body style=\"margin:0;padding:0;background-color:#FDFCFB;font-family:'Inter','Helvetica Neue',Arial,sans-serif;\">");

        // Centered container
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background-color:#FDFCFB;\"><tr><td align=\"center\" style=\"padding:32px 16px;\">");
        sb.append("<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"max-width:600px;width:100%;\">");

        // ── HEADER ──
        sb.append("<tr><td style=\"padding:24px 32px;background-color:#FFFFFF;border-radius:16px 16px 0 0;border-bottom:1px solid #E7E5E4;\">");
        sb.append("<img src=\"").append(esc(frontendUrl)).append("/images/new%20logo.PNG\" alt=\"AFRICA SHOP\" width=\"120\" height=\"40\" style=\"display:block;height:32px;width:auto;\" />");
        sb.append("</td></tr>");

        // ── BODY ──
        sb.append("<tr><td style=\"padding:32px;background-color:#FFFFFF;\">");

        // Order title + date
        sb.append("<p style=\"font-family:Arial,sans-serif;font-size:18px;font-weight:700;color:#1C1917;margin:0 0 4px 0;letter-spacing:-0.01em;\">");
        sb.append("\u0417\u0430\u043C\u043E\u0432\u043B\u0435\u043D\u043D\u044F</p>");
        sb.append("<p style=\"font-family:'Courier New',monospace;font-size:13px;color:#78716C;margin:0 0 24px 0;\">");
        sb.append("#").append(esc(shortId(orderId)));
        if (!date.isEmpty()) sb.append(" &middot; ").append(date);
        sb.append("</p>");

        // Status badge
        sb.append(statusBadgeHtml);

        // ── ITEMS CARD ──
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"background-color:#FFFFFF;border:1px solid #E7E5E4;border-radius:16px;overflow:hidden;margin-bottom:16px;\">");

        // Items header
        sb.append("<tr><td style=\"padding:16px 20px;border-bottom:1px solid #F5F5F4;\">");
        sb.append("<span style=\"font-family:Arial,sans-serif;font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:0.08em;color:#A8A29E;\">");
        sb.append("\u0422\u043E\u0432\u0430\u0440\u0438</span></td></tr>");

        // Items rows
        if (order.items() != null) {
            for (int i = 0; i < order.items().size(); i++) {
                var item = order.items().get(i);
                BigDecimal lineTotal = item.unitPrice() != null
                        ? item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))
                        : BigDecimal.ZERO;

                sb.append("<tr><td style=\"padding:14px 20px;");
                if (i < order.items().size() - 1) sb.append("border-bottom:1px solid #F5F5F4;");
                sb.append("\">");
                sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");
                // Product info
                sb.append("<td style=\"font-family:Arial,sans-serif;font-size:14px;color:#1C1917;\">");
                sb.append(esc(item.productTitle()));
                sb.append("<br><span style=\"font-size:12px;color:#78716C;\">");
                sb.append(esc(item.variantName() != null ? item.variantName() : "\u2014"));
                sb.append(" <span style=\"font-family:'Courier New',monospace;\">x ").append(item.quantity()).append("</span>");
                sb.append("</span></td>");
                // Price
                sb.append("<td align=\"right\" style=\"font-family:'Courier New',monospace;font-size:14px;color:#1C1917;white-space:nowrap;\">");
                sb.append(formatPrice(lineTotal));
                sb.append("</td></tr></table></td></tr>");
            }
        }

        // Totals row
        sb.append("<tr><td style=\"padding:14px 20px;background-color:#FAFAF9;\">");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");
        sb.append("<td style=\"font-family:Arial,sans-serif;font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;color:#78716C;\">");
        sb.append("\u0420\u0430\u0437\u043E\u043C</td>");
        sb.append("<td align=\"right\" style=\"font-family:'Courier New',monospace;font-size:18px;font-weight:500;color:#1C1917;\">");
        sb.append(formatPrice(order.totalAmount()));
        sb.append("</td></tr></table></td></tr>");

        sb.append("</table>"); // end items card

        // ── SHIPPING + CONTACTS CARDS (2-col) ──
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-bottom:24px;\"><tr>");

        // Shipping card
        sb.append("<td width=\"48%\" valign=\"top\" style=\"background-color:#FFFFFF;border:1px solid #E7E5E4;border-radius:16px;padding:20px;\">");
        sb.append("<span style=\"font-family:Arial,sans-serif;font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:0.08em;color:#A8A29E;display:block;margin-bottom:12px;\">");
        sb.append("\u0414\u043E\u0441\u0442\u0430\u0432\u043A\u0430</span>");
        if (order.shippingDetails() != null) {
            sb.append("<span style=\"font-family:Arial,sans-serif;font-size:14px;color:#1C1917;display:block;\">")
                    .append(esc(order.shippingDetails().carrier() != null ? order.shippingDetails().carrier() : "\u041D\u043E\u0432\u0430 \u041F\u043E\u0448\u0442\u0430")).append("</span>");
            sb.append("<span style=\"font-family:Arial,sans-serif;font-size:13px;color:#78716C;display:block;margin-top:4px;\">")
                    .append(esc(order.shippingDetails().city())).append("</span>");
            sb.append("<span style=\"font-family:Arial,sans-serif;font-size:13px;color:#78716C;display:block;\">")
                    .append(esc(order.shippingDetails().warehouseDescription())).append("</span>");
        }
        sb.append("</td>");

        // Spacer
        sb.append("<td width=\"4%\"></td>");

        // Contacts card
        sb.append("<td width=\"48%\" valign=\"top\" style=\"background-color:#FFFFFF;border:1px solid #E7E5E4;border-radius:16px;padding:20px;\">");
        sb.append("<span style=\"font-family:Arial,sans-serif;font-size:10px;font-weight:700;text-transform:uppercase;letter-spacing:0.08em;color:#A8A29E;display:block;margin-bottom:12px;\">");
        sb.append("\u041A\u043E\u043D\u0442\u0430\u043A\u0442\u0438</span>");
        sb.append("<span style=\"font-family:Arial,sans-serif;font-size:14px;color:#1C1917;display:block;\">")
                .append(esc(order.firstName())).append(" ").append(esc(order.lastName())).append("</span>");
        sb.append("<span style=\"font-family:Arial,sans-serif;font-size:13px;color:#78716C;display:block;margin-top:4px;\">")
                .append(esc(order.email())).append("</span>");
        sb.append("<span style=\"font-family:Arial,sans-serif;font-size:13px;color:#78716C;display:block;\">")
                .append(esc(order.phone())).append("</span>");
        sb.append("</td>");

        sb.append("</tr></table>");

        // ── CTA BUTTON ──
        if (ctaText != null) {
            String ctaUrl = ctaText.contains("\u043C\u0430\u0433\u0430\u0437\u0438\u043D") ? frontendUrl : orderUrl;
            sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-bottom:8px;\"><tr><td align=\"center\" style=\"padding:8px 0 16px;\">");
            sb.append("<a href=\"").append(esc(ctaUrl)).append("\" target=\"_blank\" style=\"display:inline-block;background-color:#FF5A5F;color:#FFFFFF;font-family:Arial,sans-serif;font-size:12px;font-weight:700;text-transform:uppercase;letter-spacing:0.05em;text-decoration:none;padding:14px 32px;border-radius:50px;\">");
            sb.append(esc(ctaText));
            sb.append("</a></td></tr></table>");
        }

        sb.append("</td></tr>"); // end body

        // ── FOOTER ──
        sb.append("<tr><td style=\"padding:24px 32px;background-color:#1C1917;border-radius:0 0 16px 16px;\">");
        sb.append("<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\"><tr>");
        sb.append("<td style=\"font-family:Arial,sans-serif;font-size:11px;color:#78716C;\">");
        sb.append("\u00A9 2026 AFRICA SHOP. \u0423\u0441\u0456 \u043F\u0440\u0430\u0432\u0430 \u0437\u0430\u0445\u0438\u0449\u0435\u043D\u0456.</td>");
        sb.append("<td align=\"right\" style=\"font-family:Arial,sans-serif;font-size:11px;color:#78716C;\">");
        sb.append("\u0423\u043A\u0440\u0430\u0457\u043D\u0430</td>");
        sb.append("</tr></table></td></tr>");

        sb.append("</table>"); // end main container
        sb.append("</td></tr></table>"); // end outer wrapper
        sb.append("</body></html>");

        return sb.toString();
    }

    // --- Helpers ---

    private static String statusBadge(String bgColor, String borderColor, String textColor,
                                       String title, String subtitle) {
        return "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\" style=\"margin-bottom:24px;\">" +
                "<tr><td style=\"background-color:" + bgColor + ";border:1px solid " + borderColor + ";border-radius:16px;padding:20px;\">" +
                "<span style=\"font-family:Arial,sans-serif;font-size:14px;font-weight:600;color:" + textColor + ";\">" + title + "</span>" +
                (subtitle != null ? "<br><span style=\"font-family:Arial,sans-serif;font-size:12px;color:" + textColor + ";opacity:0.7;margin-top:4px;display:inline-block;\">" + subtitle + "</span>" : "") +
                "</td></tr></table>";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }

    private static String shortId(String id) {
        if (id == null) return "";
        return id.length() > 8 ? id.substring(0, 8) + "\u2026" : id;
    }

    private static String formatPrice(BigDecimal amount) {
        if (amount == null) return "0.00 UAH";
        return amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() + " UAH";
    }
}
