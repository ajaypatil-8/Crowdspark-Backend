// src/main/java/Crowdspark/Crowdspark/service/impl/PdfReceiptServiceImpl.java
// Feature #10: Generate PDF tax receipt using Apache PDFBox.
// Produces a single-page A4 receipt with donation details, project name,
// transaction ID, and CrowdSpark branding.

package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.service.PdfReceiptService;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

@Slf4j
@Service
public class PdfReceiptServiceImpl implements PdfReceiptService {

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

    @Override
    public byte[] generateReceipt(Donation donation) {
        try (PDDocument doc = new PDDocument()) {
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            float pageWidth  = page.getMediaBox().getWidth();   // 595 pts
            float margin     = 50f;
            float contentWidth = pageWidth - 2 * margin;
            float yStart     = page.getMediaBox().getHeight() - margin; // 792 pts

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {

                float y = yStart;

                // ── Header ─────────────────────────────────────────────────
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 22);
                cs.newLineAtOffset(margin, y);
                cs.showText("CrowdSpark");
                cs.endText();

                y -= 18;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 10);
                cs.newLineAtOffset(margin, y);
                cs.showText("India's Crowdfunding Platform  |  support@crowdspark.in");
                cs.endText();

                // ── Divider ────────────────────────────────────────────────
                y -= 16;
                cs.setLineWidth(1f);
                cs.moveTo(margin, y);
                cs.lineTo(margin + contentWidth, y);
                cs.stroke();

                // ── Title ─────────────────────────────────────────────────
                y -= 30;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
                cs.newLineAtOffset(margin, y);
                cs.showText("PAYMENT RECEIPT");
                cs.endText();

                // ── Receipt number ─────────────────────────────────────────
                y -= 24;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Receipt No:   CS-" + String.format("%08d", donation.getId()));
                cs.endText();

                // ── Date ──────────────────────────────────────────────────
                y -= 18;
                String date = donation.getPaidAt() != null
                        ? donation.getPaidAt().format(DATE_FMT)
                        : donation.getCreatedAt().format(DATE_FMT);
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Date:         " + date);
                cs.endText();

                // ── Backer details ─────────────────────────────────────────
                y -= 30;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("Backer Details");
                cs.endText();

                y -= 18;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Name:         " + safeStr(donation.getBacker().getName()));
                cs.endText();

                y -= 16;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Email:        " + safeStr(donation.getBacker().getEmail()));
                cs.endText();

                // ── Project details ────────────────────────────────────────
                y -= 28;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("Project Details");
                cs.endText();

                y -= 18;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                // truncate long project titles so they fit in the field width
                String title = safeStr(donation.getProject().getTitle());
                if (title.length() > 55) title = title.substring(0, 52) + "...";
                cs.showText("Project:      " + title);
                cs.endText();

                y -= 16;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Creator:      " + safeStr(donation.getProject().getCreator().getName()));
                cs.endText();

                // ── Payment details ────────────────────────────────────────
                y -= 28;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 12);
                cs.newLineAtOffset(margin, y);
                cs.showText("Payment Details");
                cs.endText();

                y -= 18;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Amount:       Rs. " + String.format("%.2f", donation.getAmount()));
                cs.endText();

                y -= 16;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Currency:     INR (Indian Rupee)");
                cs.endText();

                y -= 16;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Status:       CONFIRMED");
                cs.endText();

                y -= 16;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA, 11);
                cs.newLineAtOffset(margin, y);
                cs.showText("Transaction:  " + safeStr(donation.getTransactionId()));
                cs.endText();

                if (donation.getRewardTier() != null) {
                    y -= 16;
                    cs.beginText();
                    cs.setFont(PDType1Font.HELVETICA, 11);
                    cs.newLineAtOffset(margin, y);
                    cs.showText("Reward Tier:  " + safeStr(donation.getRewardTier().getTitle()));
                    cs.endText();
                }

                // ── Second divider ─────────────────────────────────────────
                y -= 28;
                cs.moveTo(margin, y);
                cs.lineTo(margin + contentWidth, y);
                cs.stroke();

                // ── Total box ─────────────────────────────────────────────
                y -= 22;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_BOLD, 14);
                cs.newLineAtOffset(margin, y);
                cs.showText("TOTAL PAID:   Rs. " + String.format("%.2f", donation.getAmount()));
                cs.endText();

                // ── Footer ─────────────────────────────────────────────────
                y -= 60;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("This is a system-generated receipt and does not require a signature.");
                cs.endText();

                y -= 14;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("CrowdSpark is not responsible for the delivery of products/services offered by campaign creators.");
                cs.endText();

                y -= 14;
                cs.beginText();
                cs.setFont(PDType1Font.HELVETICA_OBLIQUE, 9);
                cs.newLineAtOffset(margin, y);
                cs.showText("For support: support@crowdspark.in | www.crowdspark.in");
                cs.endText();
            }

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            doc.save(out);
            return out.toByteArray();

        } catch (Exception e) {
            log.error("PDF receipt generation failed for donationId={}: {}",
                    donation.getId(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF receipt", e);
        }
    }

    private String safeStr(String s) {
        return s != null ? s : "";
    }
}