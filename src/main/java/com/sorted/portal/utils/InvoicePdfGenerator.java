package com.sorted.portal.utils;

import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sorted.commons.beans.InvoiceItem;
import com.sorted.commons.entity.mongo.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;

public class InvoicePdfGenerator {

    private static final Logger logger = LoggerFactory.getLogger(InvoicePdfGenerator.class);
    private static final Color HEADER_COLOR = new Color(41, 128, 185);
    private static final Color LIGHT_GRAY = new Color(245, 245, 245);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    public static byte[] generateInvoicePdf(Invoice invoice) throws DocumentException, IOException {
        // First generate and log the HTML version
        String htmlContent = generateInvoiceHtml(invoice);
        logger.info("=== INVOICE HTML CONTENT START ===");
        logger.info(htmlContent);
        logger.info("=== INVOICE HTML CONTENT END ===");

        // Then generate the PDF
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 50, 50, 50, 50);
        PdfWriter writer = PdfWriter.getInstance(document, baos);

        document.open();

        // Set fonts
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12);
        Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20, HEADER_COLOR);

        // Add header
        addHeader(document, titleFont);

        // Add invoice details
        addInvoiceDetails(document, invoice, boldFont, regularFont);

        // Add seller and buyer info
        addSellerBuyerInfo(document, invoice, boldFont, regularFont);

        // Add items table
        addItemsTable(document, invoice, boldFont, regularFont);

        // Add totals
        addTotals(document, invoice, boldFont, regularFont);

        // Add payment info
        addPaymentInfo(document, invoice, boldFont, regularFont);

        // Add footer
        addFooter(document, regularFont);

        document.close();
        return baos.toByteArray();
    }

    public static String generateInvoiceHtml(Invoice invoice) {
        StringBuilder html = new StringBuilder();

        html.append("<!DOCTYPE html>")
                .append("<html>")
                .append("<head>")
                .append("<meta charset='UTF-8'>")
                .append("<title>Invoice - ").append(invoice.getInvoiceId()).append("</title>")
                .append("<style>")
                .append(getInvoiceStyles())
                .append("</style>")
                .append("</head>")
                .append("<body>");

        // Header
        html.append("<div class='header'>")
                .append("<h1>TAX INVOICE</h1>")
                .append("</div>");

        // Invoice Details
        html.append("<div class='invoice-details'>")
                .append("<table class='details-table'>")
                .append("<tr><td class='label'>Invoice No:</td><td>").append(invoice.getInvoiceId()).append("</td></tr>")
                .append("<tr><td class='label'>Invoice Date:</td><td>").append(invoice.getInvoiceDate().format(DATE_FORMATTER)).append("</td></tr>")
                .append("</table>")
                .append("</div>");

        // Seller and Buyer Info
        html.append("<div class='party-info'>")
                .append("<div class='seller-info'>")
                .append("<h3>SOLD BY:</h3>")
                .append("<p><strong>").append(invoice.getSeller().getName()).append("</strong></p>")
                .append("<p>").append(invoice.getSeller().getAddress()).append("</p>")
                .append("<p>Phone: ").append(invoice.getSeller().getPhoneNo()).append("</p>")
                .append("<p>GST No: ").append(invoice.getSeller().getGstNo()).append("</p>")
                .append("<p>Seller ID: ").append(invoice.getSeller().getSellerId()).append("</p>")
                .append("</div>")
                .append("<div class='buyer-info'>")
                .append("<h3>BILLING ADDRESS:</h3>")
                .append("<p><strong>").append(invoice.getBuyer().getName()).append("</strong></p>")
                .append("<p>").append(invoice.getBuyer().getAddress()).append("</p>")
                .append("<p>Email: ").append(invoice.getBuyer().getEmail()).append("</p>")
                .append("</div>")
                .append("</div>");

        // Items Table
        html.append("<div class='items-section'>")
                .append("<table class='items-table'>")
                .append("<thead>")
                .append("<tr>")
                .append("<th>S.No</th>")
                .append("<th>Product Name</th>")
                .append("<th>HSN Code</th>")
                .append("<th>Qty</th>")
                .append("<th>Unit Price</th>")
                .append("<th>Total Price</th>")
                .append("</tr>")
                .append("</thead>")
                .append("<tbody>");

        int serialNo = 1;
        for (InvoiceItem item : invoice.getItems()) {
            html.append("<tr>")
                    .append("<td>").append(serialNo++).append("</td>")
                    .append("<td>").append(item.getProductName()).append("</td>")
                    .append("<td>").append(item.getHsnCode()).append("</td>")
                    .append("<td>").append(item.getQuantity()).append("</td>")
                    .append("<td>₹").append(item.getUnitPrice()).append("</td>")
                    .append("<td>₹").append(item.getTotalPrice()).append("</td>")
                    .append("</tr>");
        }

        html.append("</tbody>")
                .append("</table>")
                .append("</div>");

        // Totals
        html.append("<div class='totals-section'>")
                .append("<table class='totals-table'>")
                .append("<tr><td>Sub Total:</td><td>₹").append(invoice.getTotalAmount()).append("</td></tr>")
                .append("<tr><td>GST:</td><td>₹").append(invoice.getTotalGstAmount()).append("</td></tr>")
                .append("<tr class='total-row'><td><strong>TOTAL AMOUNT:</strong></td><td><strong>₹").append(invoice.getTotalNetAmount()).append("</strong></td></tr>")
                .append("</table>")
                .append("</div>");

        // Amount in Words
        html.append("<div class='amount-words'>")
                .append("<strong>Amount in Words: ").append(invoice.getTotalAmountInWords()).append("</strong>")
                .append("</div>");

        // Payment Info
        html.append("<div class='payment-section'>")
                .append("<h3>PAYMENT INFORMATION</h3>")
                .append("<table class='payment-table'>")
                .append("<tr><td class='label'>Payment Method:</td><td>").append(invoice.getPaymentInfo().getPaymentMethod()).append("</td></tr>")
                .append("<tr><td class='label'>Transaction ID:</td><td>").append(invoice.getPaymentInfo().getTransactionId()).append("</td></tr>")
                .append("<tr><td class='label'>Payment Date:</td><td>").append(invoice.getPaymentInfo().getPaymentDate().format(DATE_FORMATTER)).append("</td></tr>")
                .append("</table>")
                .append("</div>");

        // Footer
        html.append("<div class='footer'>")
                .append("<p>This is a computer generated invoice and does not require signature.</p>")
                .append("</div>");

        html.append("</body></html>");

        return html.toString();
    }

    private static String getInvoiceStyles() {
        return """
            body {
                font-family: Arial, sans-serif;
                margin: 0;
                padding: 20px;
                font-size: 12px;
                line-height: 1.4;
                background-color: #fff;
            }
            
            .header {
                text-align: center;
                margin-bottom: 30px;
                border-bottom: 2px solid #2980b9;
                padding-bottom: 10px;
            }
            
            .header h1 {
                color: #2980b9;
                font-size: 28px;
                margin: 0;
                font-weight: bold;
            }
            
            .invoice-details {
                margin-bottom: 20px;
                width: 100%;
            }
            
            .details-table {
                width: 100%;
                border-collapse: collapse;
                margin-bottom: 20px;
            }
            
            .details-table td.label {
                font-weight: bold;
                width: 150px;
                padding: 8px 0;
                font-size: 14px;
            }
            
            .details-table td {
                padding: 8px 0;
                font-size: 14px;
            }
            
            .party-info {
                display: flex;
                gap: 20px;
                margin-bottom: 30px;
                width: 100%;
            }
            
            .seller-info, .buyer-info {
                flex: 1;
                border: 2px solid #000;
                padding: 15px;
                background-color: #f9f9f9;
            }
            
            .seller-info h3, .buyer-info h3 {
                margin: 0 0 15px 0;
                font-size: 16px;
                color: #2980b9;
                border-bottom: 1px solid #2980b9;
                padding-bottom: 5px;
            }
            
            .seller-info p, .buyer-info p {
                margin: 8px 0;
                line-height: 1.6;
            }
            
            .items-section {
                margin-bottom: 20px;
            }
            
            .items-table {
                width: 100%;
                border-collapse: collapse;
                border: 2px solid #000;
                margin-bottom: 20px;
            }
            
            .items-table th {
                background-color: #2980b9;
                color: white;
                padding: 12px 8px;
                text-align: center;
                border: 1px solid #000;
                font-weight: bold;
                font-size: 14px;
            }
            
            .items-table td {
                padding: 10px 8px;
                text-align: center;
                border: 1px solid #000;
                font-size: 13px;
            }
            
            .items-table tbody tr:nth-child(even) {
                background-color: #f8f9fa;
            }
            
            .items-table tbody tr:hover {
                background-color: #e8f4fd;
            }
            
            .totals-section {
                margin-bottom: 25px;
                text-align: right;
            }
            
            .totals-table {
                width: 350px;
                margin-left: auto;
                border-collapse: collapse;
                border: 2px solid #000;
            }
            
            .totals-table td {
                padding: 8px 15px;
                border: 1px solid #000;
                font-size: 14px;
            }
            
            .totals-table .total-row {
                background-color: #f5f5f5;
                font-weight: bold;
                font-size: 16px;
            }
            
            .totals-table .total-row td {
                padding: 12px 15px;
                border: 2px solid #000;
            }
            
            .amount-words {
                border: 2px solid #000;
                padding: 15px;
                margin-bottom: 25px;
                background-color: #f9f9f9;
                font-size: 14px;
                font-weight: bold;
            }
            
            .payment-section {
                margin-bottom: 30px;
            }
            
            .payment-section h3 {
                margin-bottom: 15px;
                color: #2980b9;
                border-bottom: 2px solid #2980b9;
                padding-bottom: 5px;
                font-size: 16px;
            }
            
            .payment-table {
                width: 60%;
                border-collapse: collapse;
                border: 1px solid #000;
            }
            
            .payment-table td.label {
                font-weight: bold;
                width: 180px;
                padding: 10px 15px;
                background-color: #f5f5f5;
                border: 1px solid #000;
            }
            
            .payment-table td {
                padding: 10px 15px;
                border: 1px solid #000;
                font-size: 14px;
            }
            
            .footer {
                text-align: center;
                margin-top: 40px;
                font-size: 12px;
                color: #666;
                font-style: italic;
                border-top: 1px solid #ccc;
                padding-top: 15px;
            }
            
            @media print {
                body {
                    margin: 0;
                    padding: 15px;
                }
                
                .party-info {
                    page-break-inside: avoid;
                }
                
                .items-table {
                    page-break-inside: avoid;
                }
            }
            
            @page {
                margin: 20mm;
            }
            """;
    }

    // Your existing PDF methods remain the same...
    private static void addHeader(Document document, Font titleFont) throws DocumentException {
        Paragraph header = new Paragraph("TAX INVOICE", titleFont);
        header.setAlignment(Element.ALIGN_CENTER);
        header.setSpacingAfter(20);
        document.add(header);
    }

    private static void addInvoiceDetails(Document document, Invoice invoice, Font boldFont, Font regularFont)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        addTableRow(table, "Invoice No:", invoice.getInvoiceId(), boldFont, regularFont);
        addTableRow(table, "Invoice Date:", invoice.getInvoiceDate().format(DATE_FORMATTER), boldFont, regularFont);

        document.add(table);
    }

    private static void addSellerBuyerInfo(Document document, Invoice invoice, Font boldFont, Font regularFont)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        // Seller cell
        PdfPCell sellerCell = new PdfPCell();
        sellerCell.setPadding(10);
        sellerCell.setBorder(Rectangle.BOX);

        Paragraph sellerHeader = new Paragraph("SOLD BY:", boldFont);
        sellerCell.addElement(sellerHeader);
        sellerCell.addElement(new Paragraph(invoice.getSeller().getName(), regularFont));
        sellerCell.addElement(new Paragraph(invoice.getSeller().getAddress(), regularFont));
        sellerCell.addElement(new Paragraph("Phone: " + invoice.getSeller().getPhoneNo(), regularFont));
        sellerCell.addElement(new Paragraph("GST No: " + invoice.getSeller().getGstNo(), regularFont));
        sellerCell.addElement(new Paragraph("Seller ID: " + invoice.getSeller().getSellerId(), regularFont));

        // Buyer cell
        PdfPCell buyerCell = new PdfPCell();
        buyerCell.setPadding(10);
        buyerCell.setBorder(Rectangle.BOX);

        Paragraph buyerHeader = new Paragraph("BILLING ADDRESS:", boldFont);
        buyerCell.addElement(buyerHeader);
        buyerCell.addElement(new Paragraph(invoice.getBuyer().getName(), regularFont));
        buyerCell.addElement(new Paragraph(invoice.getBuyer().getAddress(), regularFont));
        buyerCell.addElement(new Paragraph("Email: " + invoice.getBuyer().getEmail(), regularFont));

        table.addCell(sellerCell);
        table.addCell(buyerCell);

        document.add(table);
    }

    private static void addItemsTable(Document document, Invoice invoice, Font boldFont, Font regularFont)
            throws DocumentException {

        PdfPTable table = new PdfPTable(6);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        // Set column widths
        float[] columnWidths = {1f, 3f, 1.5f, 1f, 2f, 2f};
        table.setWidths(columnWidths);

        // Header row
        addHeaderCell(table, "S.No", boldFont);
        addHeaderCell(table, "Product Name", boldFont);
        addHeaderCell(table, "HSN Code", boldFont);
        addHeaderCell(table, "Qty", boldFont);
        addHeaderCell(table, "Unit Price", boldFont);
        addHeaderCell(table, "Total Price", boldFont);

        // Data rows
        int serialNo = 1;
        for (InvoiceItem item : invoice.getItems()) {
            addDataCell(table, String.valueOf(serialNo++), regularFont);
            addDataCell(table, item.getProductName(), regularFont);
            addDataCell(table, item.getHsnCode(), regularFont);
            addDataCell(table, String.valueOf(item.getQuantity()), regularFont);
            addDataCell(table, "₹" + item.getUnitPrice().toString(), regularFont);
            addDataCell(table, "₹" + item.getTotalPrice().toString(), regularFont);
        }

        document.add(table);
    }

    private static void addTotals(Document document, Invoice invoice, Font boldFont, Font regularFont)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(50);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingAfter(20);

        addTableRow(table, "Sub Total:", "₹" + invoice.getTotalAmount().toString(), boldFont, regularFont);
        addTableRow(table, "GST:", "₹" + invoice.getTotalGstAmount().toString(), boldFont, regularFont);

        // Total row with background
        PdfPCell totalLabelCell = new PdfPCell(new Phrase("TOTAL AMOUNT:", boldFont));
        totalLabelCell.setBackgroundColor(LIGHT_GRAY);
        totalLabelCell.setPadding(5);

        PdfPCell totalValueCell = new PdfPCell(new Phrase("₹" + invoice.getTotalNetAmount().toString(), boldFont));
        totalValueCell.setBackgroundColor(LIGHT_GRAY);
        totalValueCell.setPadding(5);

        table.addCell(totalLabelCell);
        table.addCell(totalValueCell);

        document.add(table);

        // Amount in words
        Paragraph amountInWords = new Paragraph("Amount in Words: " + invoice.getTotalAmountInWords(), boldFont);
        amountInWords.setSpacingBefore(10);
        amountInWords.setSpacingAfter(20);
        document.add(amountInWords);
    }

    private static void addPaymentInfo(Document document, Invoice invoice, Font boldFont, Font regularFont)
            throws DocumentException {

        Paragraph paymentHeader = new Paragraph("PAYMENT INFORMATION", boldFont);
        paymentHeader.setSpacingAfter(10);
        document.add(paymentHeader);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(60);
        table.setSpacingAfter(20);

        addTableRow(table, "Payment Method:", invoice.getPaymentInfo().getPaymentMethod(), boldFont, regularFont);
        addTableRow(table, "Transaction ID:", invoice.getPaymentInfo().getTransactionId(), boldFont, regularFont);
        addTableRow(table, "Payment Date:", invoice.getPaymentInfo().getPaymentDate().format(DATE_FORMATTER), boldFont, regularFont);

        document.add(table);
    }

    private static void addFooter(Document document, Font regularFont) throws DocumentException {
        Paragraph footer = new Paragraph("This is a computer generated invoice and does not require signature.", regularFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(30);
        document.add(footer);
    }

    private static void addTableRow(PdfPTable table, String label, String value, Font boldFont, Font regularFont) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, boldFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(5);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, regularFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(5);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private static void addHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setBackgroundColor(HEADER_COLOR);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(8);
        table.addCell(cell);
    }

    private static void addDataCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        table.addCell(cell);
    }

    // Method to save PDF to file
    public static void saveInvoicePdfToFile(Invoice invoice, String filePath) throws DocumentException, IOException {
        byte[] pdfBytes = generateInvoicePdf(invoice);
        try (FileOutputStream fos = new FileOutputStream(filePath)) {
            fos.write(pdfBytes);
        }
    }

    // Method to only generate HTML (without PDF)
    public static String generateOnlyHtml(Invoice invoice) {
        String htmlContent = generateInvoiceHtml(invoice);
        logger.info("=== INVOICE HTML CONTENT START ===");
        logger.info(htmlContent);
        logger.info("=== INVOICE HTML CONTENT END ===");
        return htmlContent;
    }
}