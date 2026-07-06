
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.service.PdfReceiptService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
public class PdfReceiptServiceImpl implements PdfReceiptService {

    private static final float MARGIN        = 50f;
    private static final float PAGE_WIDTH     = PDRectangle.A4.getWidth();
    private static final float PAGE_HEIGHT    = PDRectangle.A4.getHeight();
    private static final float CONTENT_RIGHT  = PAGE_WIDTH - MARGIN;
    private static final float CONTENT_WIDTH  = CONTENT_RIGHT - MARGIN;

    // Same palette as the email templates, for a consistent look.
    private static final Color TEXT_DARK  = new Color(24, 24, 27);
    private static final Color TEXT_GRAY  = new Color(113, 113, 122);
    private static final Color HEADER_BG  = new Color(12, 12, 16);
    private static final Color LINE_LIGHT = new Color(228, 228, 231);
    private static final Color ACCENT     = new Color(255, 92, 0);

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("d MMMM yyyy, h:mm a");

    @Override
    public byte[] generateReceiptPdf(Long donationId, String backerName, String projectTitle, Double amount,
                                     String transactionId, String rewardTierTitle, LocalDateTime paidAt) {
        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);

            PDFont bold    = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDFont regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDFont italic  = new PDType1Font(Standard14Fonts.FontName.HELVETICA_OBLIQUE);

            double safeAmount = amount == null ? 0.0 : amount;
            String amountFormatted = formatInr(safeAmount);
            String receiptNo = "CS-" + String.format("%06d", donationId == null ? 0L : donationId);
            String dateStr = (paidAt == null ? LocalDateTime.now() : paidAt).format(DATE_FORMAT);

            try (PDPageContentStream cs = new PDPageContentStream(document, page)) {

                float y = PAGE_HEIGHT - 70;

                // ── Brand header ────────────────────────────────────────────
                drawText(cs, bold, 20, MARGIN, y, "CrowdSpark", ACCENT);
                drawTextRightAligned(cs, bold, 11, CONTENT_RIGHT, y + 3, "PAYMENT RECEIPT", TEXT_DARK);
                y -= 10;
                drawLine(cs, y, HEADER_BG, 1.2f);
                y -= 28;

                // ── Receipt No. / Date ──────────────────────────────────────
                drawText(cs, regular, 9, MARGIN, y, "RECEIPT NO.", TEXT_GRAY);
                drawText(cs, regular, 9, MARGIN + 260, y, "DATE", TEXT_GRAY);
                y -= 15;
                drawText(cs, bold, 12, MARGIN, y, receiptNo, TEXT_DARK);
                drawText(cs, bold, 12, MARGIN + 260, y, dateStr, TEXT_DARK);
                y -= 30;

                // ── Received from ───────────────────────────────────────────
                drawText(cs, regular, 9, MARGIN, y, "RECEIVED FROM", TEXT_GRAY);
                y -= 15;
                drawText(cs, bold, 12, MARGIN, y, nullSafe(backerName, "Backer"), TEXT_DARK);
                y -= 26;

                drawLine(cs, y, LINE_LIGHT, 0.75f);
                y -= 20;

                // ── Line item ────────────────────────────────────────────────
                drawText(cs, regular, 9, MARGIN, y, "DESCRIPTION", TEXT_GRAY);
                drawTextRightAligned(cs, regular, 9, CONTENT_RIGHT, y, "AMOUNT", TEXT_GRAY);
                y -= 18;

                float descMaxWidth = 330f;
                List<String> titleLines = wrapText(regular, 12, "Contribution to \"" + nullSafe(projectTitle, "a CrowdSpark project") + "\"", descMaxWidth);
                for (int i = 0; i < titleLines.size(); i++) {
                    drawText(cs, regular, 12, MARGIN, y, titleLines.get(i), TEXT_DARK);
                    if (i == 0) {
                        drawTextRightAligned(cs, bold, 12, CONTENT_RIGHT, y, amountFormatted, TEXT_DARK);
                    }
                    y -= 16;
                }

                if (rewardTierTitle != null && !rewardTierTitle.isBlank()) {
                    for (String line : wrapText(italic, 10, "Reward tier: " + rewardTierTitle, descMaxWidth)) {
                        drawText(cs, italic, 10, MARGIN, y, line, TEXT_GRAY);
                        y -= 14;
                    }
                }

                y -= 6;
                drawLine(cs, y, LINE_LIGHT, 0.75f);
                y -= 24;

                // ── Total ────────────────────────────────────────────────────
                drawText(cs, bold, 12, MARGIN, y, "TOTAL PAID", TEXT_DARK);
                drawTextRightAligned(cs, bold, 15, CONTENT_RIGHT, y - 1, amountFormatted, ACCENT);
                y -= 28;

                drawText(cs, regular, 9, MARGIN, y, "Transaction ID: " + nullSafe(transactionId, "\u2014"), TEXT_GRAY);
                y -= 50;

                // ── Footer / disclaimer ──────────────────────────────────────
                drawLine(cs, y, LINE_LIGHT, 0.75f);
                y -= 20;

                List<String> disclaimerLines = wrapText(italic, 8.5f,
                        "This receipt confirms a contribution processed through CrowdSpark. It is not a GST tax invoice — "
                                + "please consult a tax advisor regarding the tax treatment of this contribution.",
                        CONTENT_WIDTH);
                for (String line : disclaimerLines) {
                    drawText(cs, italic, 8.5f, MARGIN, y, line, TEXT_GRAY);
                    y -= 12;
                }

                y -= 10;
                drawText(cs, regular, 9, MARGIN, y, "Thank you for supporting this campaign on CrowdSpark.", TEXT_DARK);
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            log.info("Generated PDF receipt {} for donationId={}", receiptNo, donationId);
            return out.toByteArray();

        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate PDF receipt for donation " + donationId, e);
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drawing helpers
    // ─────────────────────────────────────────────────────────────────────────

    private void drawText(PDPageContentStream cs, PDFont font, float size, float x, float y, String text, Color color) throws IOException {
        cs.setNonStrokingColor(color);
        cs.beginText();
        cs.setFont(font, size);
        cs.newLineAtOffset(x, y);
        cs.showText(text);
        cs.endText();
    }

    private void drawTextRightAligned(PDPageContentStream cs, PDFont font, float size, float rightEdge, float y, String text, Color color) throws IOException {
        float width = font.getStringWidth(text) / 1000 * size;
        drawText(cs, font, size, rightEdge - width, y, text, color);
    }

    private void drawLine(PDPageContentStream cs, float y, Color color, float lineWidth) throws IOException {
        cs.setStrokingColor(color);
        cs.setLineWidth(lineWidth);
        cs.moveTo(MARGIN, y);
        cs.lineTo(CONTENT_RIGHT, y);
        cs.stroke();
    }

    private List<String> wrapText(PDFont font, float size, String text, float maxWidth) throws IOException {
        List<String> lines = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : text.split(" ")) {
            String candidate = current.isEmpty() ? word : current + " " + word;
            if (font.getStringWidth(candidate) / 1000 * size > maxWidth && !current.isEmpty()) {
                lines.add(current.toString());
                current = new StringBuilder(word);
            } else {
                current = new StringBuilder(candidate);
            }
        }
        if (!current.isEmpty()) {
            lines.add(current.toString());
        }
        return lines;
    }

    private static String nullSafe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }

    private static String formatInr(double amount) {
        NumberFormat nf = NumberFormat.getInstance(Locale.of("en", "IN"));
        nf.setMinimumFractionDigits(2);
        nf.setMaximumFractionDigits(2);
        return "₹" + nf.format(amount);
    }
}