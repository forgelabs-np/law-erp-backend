package com.lawfirm.erp.modules.invoice.service;

import com.lawfirm.erp.firm.entity.Firm;
import com.lawfirm.erp.modules.invoice.entity.Invoice;
import com.lawfirm.erp.modules.invoice.entity.InvoiceItem;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class InvoicePdfService {

    private final TemplateEngine templateEngine;

    public byte[] generatePdf(Invoice invoice, List<InvoiceItem> items, Firm firm) {
        // 1. Build Thymeleaf context
        Context context = new Context();
        context.setVariable("invoice", invoice);
        context.setVariable("items", items);
        context.setVariable("firm", firm);
        context.setVariable("platformName", "NepalCRM Platform");
        context.setVariable("platformAddress", "Kathmandu, Nepal");
        context.setVariable("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));

        // 2. Render HTML from template
        String htmlContent = templateEngine.process("invoice-template", context);

        // 3. Convert HTML to PDF
        try {
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(htmlContent, null)
                    .toStream(outputStream)
                    .run();
            return outputStream.toByteArray();
        } catch (Exception e) {
            log.error("PDF generation failed for invoice {}: {}", invoice.getInvoiceNumber(), e.getMessage(), e);
            throw new RuntimeException("Failed to generate PDF", e);
        }
    }
}
