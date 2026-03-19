package com.sorted.common.utils;

import com.lowagie.text.Font;
import com.lowagie.text.Rectangle;
import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.sorted.common.beans.InvoiceItem;
import com.sorted.common.entity.mongo.Invoice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.ByteArrayOutputStream;
import java.time.format.DateTimeFormatter;

public class InvoicePdfGenerator {

    private static final Logger logger = LoggerFactory.getLogger(InvoicePdfGenerator.class);
    private static final Color HEADER_COLOR = new Color(142, 81, 255); // #8e51ff
    private static final Color LIGHT_PURPLE = new Color(200, 178, 255); // Light version of #8e51ff
    private static final Color LIGHT_GRAY = new Color(245, 245, 245);
    private static final Color BORDER_COLOR = new Color(189, 195, 199);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");

    public static byte[] generateInvoicePdf(Invoice invoice) throws DocumentException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        Document document = new Document(PageSize.A4, 40, 40, 40, 40);
        PdfWriter writer = PdfWriter.getInstance(document, baos);
        document.open();

        // Set fonts
        Font titleFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 24, Color.WHITE);
        Font headerFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, HEADER_COLOR);
        Font boldFont = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11);
        Font regularFont = FontFactory.getFont(FontFactory.HELVETICA, 10);
        Font smallFont = FontFactory.getFont(FontFactory.HELVETICA, 9);

        // Add header
        addHeader(document, titleFont);

        // Add invoice details
        addInvoiceDetails(document, invoice, boldFont, regularFont);

        // Add seller and buyer info
        addSellerBuyerInfo(document, invoice, headerFont, regularFont);

        // Add items table
        addItemsTable(document, invoice, boldFont, regularFont);

        // Add totals
        addTotals(document, invoice, boldFont, regularFont);

        // Add payment info
        addPaymentInfo(document, invoice, headerFont, regularFont);

        // Add support info
        addSupportInfo(document, headerFont, regularFont);

        // Add company info and footer
        addCompanyInfoAndFooter(document, boldFont, smallFont);

        document.close();
        return baos.toByteArray();
    }

    private static void addHeader(Document document, Font titleFont) throws DocumentException {
        // Create header table with background
        PdfPTable headerTable = new PdfPTable(1);
        headerTable.setWidthPercentage(100);
        headerTable.setSpacingAfter(25);

        PdfPCell headerCell = new PdfPCell(new Phrase("TAX INVOICE", titleFont));
        headerCell.setBackgroundColor(HEADER_COLOR);
        headerCell.setHorizontalAlignment(Element.ALIGN_CENTER);
        headerCell.setPadding(15);
        headerCell.setBorder(Rectangle.NO_BORDER);

        headerTable.addCell(headerCell);
        document.add(headerTable);
    }

    private static void addInvoiceDetails(Document document, Invoice invoice, Font boldFont, Font regularFont)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(20);

        // First row
        PdfPTable topRow = new PdfPTable(2);
        topRow.setWidthPercentage(100);
        addDetailCell(topRow, "Order No:", invoice.getOrderCode(), boldFont, regularFont);
        addDetailCell(topRow, "Invoice No:", invoice.getInvoiceId(), boldFont, regularFont);

        PdfPCell topRowCell = new PdfPCell(topRow);
        topRowCell.setBorder(Rectangle.NO_BORDER);
        topRowCell.setColspan(2);
        table.addCell(topRowCell);

        // Second row with date and time
        addDetailCell(table, "Invoice Date:", invoice.getInvoiceDate().format(DATE_FORMATTER), boldFont, regularFont);
        addDetailCell(table, "Invoice Time:", invoice.getInvoiceDate().format(TIME_FORMATTER), boldFont, regularFont);

        document.add(table);
    }

    private static void addSellerBuyerInfo(Document document, Invoice invoice, Font headerFont, Font regularFont)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(100);
        table.setSpacingAfter(25);

        // Seller cell with improved styling
        PdfPCell sellerCell = new PdfPCell();
        sellerCell.setPadding(15);
        sellerCell.setBorder(Rectangle.BOX);
        sellerCell.setBorderColor(BORDER_COLOR);
        sellerCell.setBackgroundColor(new Color(252, 252, 252));

        Paragraph sellerHeader = new Paragraph("SOLD BY", headerFont);
        sellerHeader.setSpacingAfter(8);
        sellerCell.addElement(sellerHeader);
        sellerCell.addElement(new Paragraph(invoice.getSeller().getName(), regularFont));
        sellerCell.addElement(new Paragraph(invoice.getSeller().getAddress(), regularFont));
        if (invoice.getSeller().getGstNo() != null) {
            sellerCell.addElement(new Paragraph("GST No: " + invoice.getSeller().getGstNo(), regularFont));
        }

        // Buyer cell with improved styling
        PdfPCell buyerCell = new PdfPCell();
        buyerCell.setPadding(15);
        buyerCell.setBorder(Rectangle.BOX);
        buyerCell.setBorderColor(BORDER_COLOR);
        buyerCell.setBackgroundColor(new Color(252, 252, 252));

        Paragraph buyerHeader = new Paragraph("BILLING ADDRESS", headerFont);
        buyerHeader.setSpacingAfter(8);
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

        PdfPTable table = new PdfPTable(5);
        table.setWidthPercentage(100);
        table.setSpacingAfter(25);

        // Set column widths for better proportion
        float[] columnWidths = {0.8f, 3f, 1f, 1.5f, 1.5f};
        table.setWidths(columnWidths);

        // Header row with better styling
        addStyledHeaderCell(table, "S.No", boldFont);
        addStyledHeaderCell(table, "Product Name", boldFont);
        addStyledHeaderCell(table, "Qty", boldFont);
        addStyledHeaderCell(table, "Unit Price", boldFont);
        addStyledHeaderCell(table, "Total Price", boldFont);

        // Data rows with alternating colors
        int serialNo = 1;
        for (InvoiceItem item : invoice.getItems()) {
            Color rowColor = (serialNo % 2 == 0) ? LIGHT_GRAY : Color.WHITE;

            addStyledDataCell(table, String.valueOf(serialNo++), regularFont, rowColor);
            addStyledDataCell(table, item.getProductName(), regularFont, rowColor);
            addStyledDataCell(table, String.valueOf(item.getQuantity()), regularFont, rowColor);
            addStyledDataCell(table, "₹" + item.getUnitPrice().toString(), regularFont, rowColor);
            addStyledDataCell(table, "₹" + item.getTotalPrice().toString(), regularFont, rowColor);
        }

        document.add(table);
    }

    private static void addTotals(Document document, Invoice invoice, Font boldFont, Font regularFont)
            throws DocumentException {

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(60);
        table.setHorizontalAlignment(Element.ALIGN_RIGHT);
        table.setSpacingAfter(20);

        // Subtotal and tax rows
        addTotalRow(table, "Sub Total:", "₹" + invoice.getTotalAmount().toString(), regularFont, Color.WHITE);
        addTotalRow(table, "Delivery Charges:", "₹" + invoice.getDeliveryCharge().toString(), regularFont, Color.WHITE);

        // Final total with emphasis
        addTotalRow(table, "TOTAL AMOUNT:", "₹" + invoice.getTotalNetAmount().toString(), boldFont, LIGHT_PURPLE);

        document.add(table);

        // Amount in words with better formatting
        Paragraph amountInWords = new Paragraph("Amount in Words: " + invoice.getTotalAmountInWords(), boldFont);
        amountInWords.setSpacingBefore(15);
        amountInWords.setSpacingAfter(25);
        amountInWords.setAlignment(Element.ALIGN_RIGHT);
        document.add(amountInWords);
    }

    private static void addPaymentInfo(Document document, Invoice invoice, Font headerFont, Font regularFont)
            throws DocumentException {

        // Payment header
        Paragraph paymentHeader = new Paragraph("PAYMENT INFORMATION", headerFont);
        paymentHeader.setSpacingAfter(12);
        paymentHeader.setAlignment(Element.ALIGN_CENTER);
        document.add(paymentHeader);

        PdfPTable table = new PdfPTable(2);
        table.setWidthPercentage(70);
        table.setSpacingAfter(25);

        addStyledTableRow(table, "Payment Method:", invoice.getPaymentInfo().getPaymentMethod(), regularFont);
        addStyledTableRow(table, "Transaction ID:", invoice.getPaymentInfo().getTransactionId(), regularFont);
        addStyledTableRow(table, "Payment Date:", invoice.getPaymentInfo().getPaymentDate().format(DATE_FORMATTER), regularFont);

        document.add(table);
    }

    private static void addSupportInfo(Document document, Font headerFont, Font regularFont)
            throws DocumentException {

        // Support header
        Paragraph supportHeader = new Paragraph("Need Help?", headerFont);
        supportHeader.setSpacingAfter(12);
        document.add(supportHeader);

        PdfPTable table = new PdfPTable(1);
        table.setWidthPercentage(100);
        table.setSpacingAfter(25);

        PdfPCell supportCell = new PdfPCell();
        supportCell.setPadding(12);
        supportCell.setBorder(Rectangle.BOX);
        supportCell.setBorderColor(BORDER_COLOR);
        supportCell.setBackgroundColor(new Color(249, 249, 249));

        supportCell.addElement(new Paragraph("Customer Support: support@studeaze.in | +91 90041-80031", regularFont));
        supportCell.addElement(new Paragraph("Return/Exchange: Contact us for any product issues - we'll handle everything", regularFont));
        supportCell.addElement(new Paragraph("Return Policy: 7-day return for unused books in original condition", regularFont));
        supportCell.addElement(new Paragraph("Track Your Order: Visit www.studeaze.in and use Order ID above", regularFont));

        table.addCell(supportCell);
        document.add(table);
    }

    private static void addCompanyInfoAndFooter(Document document, Font boldFont, Font smallFont)
            throws DocumentException {

        // Thank you message
        Paragraph thankYou = new Paragraph("Thank you for choosing Studeaze!", boldFont);
        thankYou.setAlignment(Element.ALIGN_CENTER);
        thankYou.setSpacingAfter(8);
        document.add(thankYou);

        Paragraph tagline = new Paragraph("Making academic resources accessible to every student", smallFont);
        tagline.setAlignment(Element.ALIGN_CENTER);
        tagline.setSpacingAfter(20);
        document.add(tagline);

        // Company information table
        PdfPTable companyTable = new PdfPTable(2);
        companyTable.setWidthPercentage(100);
        companyTable.setSpacingAfter(20);

        // Company details
        PdfPCell companyCell = new PdfPCell();
        companyCell.setBorder(Rectangle.NO_BORDER);
        companyCell.addElement(new Paragraph("Studeaze (Acumnx Solutions Private Limited)", boldFont));
        companyCell.addElement(new Paragraph("Thane - 400605", smallFont));

        // Platform info
        PdfPCell platformCell = new PdfPCell();
        platformCell.setBorder(Rectangle.NO_BORDER);
        platformCell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        platformCell.addElement(new Paragraph("Platform Info:", boldFont));
        platformCell.addElement(new Paragraph("Website: www.studeaze.in", smallFont));
        platformCell.addElement(new Paragraph("Email: support@studeaze.in", smallFont));

        companyTable.addCell(companyCell);
        companyTable.addCell(platformCell);

        document.add(companyTable);

        // Final footer
        Paragraph footer = new Paragraph("This is a computer-generated receipt. No signature required.", smallFont);
        footer.setAlignment(Element.ALIGN_CENTER);
        footer.setSpacingBefore(15);
        document.add(footer);

        // All prices inclusive note
        Paragraph priceNote = new Paragraph("*All prices are inclusive of applicable taxes", smallFont);
        priceNote.setAlignment(Element.ALIGN_CENTER);
        priceNote.setSpacingAfter(10);
        document.add(priceNote);
    }

    // Helper methods for better cell styling
    private static void addDetailCell(PdfPTable table, String label, String value, Font boldFont, Font regularFont) {
        // Label cell
        PdfPCell labelCell = new PdfPCell(new Phrase(label, boldFont));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(6);
        labelCell.setBackgroundColor(LIGHT_GRAY);

        // Value cell
        PdfPCell valueCell = new PdfPCell(new Phrase(value, regularFont));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(6);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private static void addStyledHeaderCell(PdfPTable table, String text, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text, new Font(font.getBaseFont(), font.getSize(), Font.BOLD, Color.WHITE)));
        cell.setBackgroundColor(HEADER_COLOR);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setPadding(10);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(Color.WHITE);
        table.addCell(cell);
    }

    private static void addStyledDataCell(PdfPTable table, String text, Font font, Color backgroundColor) {
        PdfPCell cell = new PdfPCell(new Phrase(text, font));
        cell.setPadding(8);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        cell.setBackgroundColor(backgroundColor);
        cell.setBorder(Rectangle.BOX);
        cell.setBorderColor(BORDER_COLOR);
        table.addCell(cell);
    }

    private static void addTotalRow(PdfPTable table, String label, String value, Font font, Color backgroundColor) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, font));
        labelCell.setBackgroundColor(backgroundColor);
        labelCell.setPadding(8);
        labelCell.setBorder(Rectangle.BOX);
        labelCell.setBorderColor(BORDER_COLOR);
        labelCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBackgroundColor(backgroundColor);
        valueCell.setPadding(8);
        valueCell.setBorder(Rectangle.BOX);
        valueCell.setBorderColor(BORDER_COLOR);
        valueCell.setHorizontalAlignment(Element.ALIGN_RIGHT);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }

    private static void addStyledTableRow(PdfPTable table, String label, String value, Font font) {
        PdfPCell labelCell = new PdfPCell(new Phrase(label, new Font(font.getBaseFont(), font.getSize(), Font.BOLD)));
        labelCell.setBorder(Rectangle.NO_BORDER);
        labelCell.setPadding(6);
        labelCell.setBackgroundColor(LIGHT_GRAY);

        PdfPCell valueCell = new PdfPCell(new Phrase(value, font));
        valueCell.setBorder(Rectangle.NO_BORDER);
        valueCell.setPadding(6);

        table.addCell(labelCell);
        table.addCell(valueCell);
    }
}