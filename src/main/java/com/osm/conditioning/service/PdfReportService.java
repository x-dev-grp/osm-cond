package com.osm.conditioning.service;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import com.osm.conditioning.dto.analytics.GlobalOfReportDto;
import com.osm.conditioning.dto.analytics.OfYieldDto;
import com.osm.conditioning.dto.analytics.QualityReportDto;
import com.osm.conditioning.dto.analytics.BomGapDto;
import com.osm.conditioning.dto.analytics.FiltrationReportDto;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class PdfReportService {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    // Bug #5 fix: null-safe createCell — prevents NPE when any field is null
    private PdfPCell createCell(String text) {
        PdfPCell cell = new PdfPCell(new Phrase(text != null ? text : ""));
        cell.setPadding(5);
        cell.setHorizontalAlignment(Element.ALIGN_CENTER);
        return cell;
    }

    // Bug #7 fix: shared helper to add period info header to all PDFs
    private void addPeriodHeader(Document document, LocalDateTime start, LocalDateTime end) throws DocumentException {
        String periodStr = (start != null && end != null)
                ? "Période : " + start.format(FORMATTER) + " au " + end.format(FORMATTER)
                : "Toutes les périodes";
        document.add(new Paragraph(periodStr));
        document.add(new Paragraph("Généré le : " + LocalDateTime.now().format(FORMATTER)));
        document.add(new Paragraph(" "));
    }

    public byte[] generateOfYieldReportPdf(List<OfYieldDto> data, LocalDateTime start, LocalDateTime end) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph("Rapport de Rendement des OF",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));
            addPeriodHeader(document, start, end);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);
            table.setWidths(new float[]{2f, 2f, 2f, 2f, 2f});

            for (String h : new String[]{"Code OF", "Statut", "Qté Cible", "Qté Bonne", "Rendement %"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
                cell.setBackgroundColor(Color.LIGHT_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);
            }

            for (OfYieldDto dto : data) {
                table.addCell(createCell(dto.getOfCode()));
                table.addCell(createCell(dto.getStatut()));
                table.addCell(createCell(dto.getQuantiteCible() != null ? dto.getQuantiteCible().toString() : "0"));
                table.addCell(createCell(dto.getQuantiteBonne() != null ? dto.getQuantiteBonne().toString() : "0"));
                table.addCell(createCell(dto.getYieldPercentage() != null ? dto.getYieldPercentage().toString() + "%" : "0%"));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF Rendements OF", e);
        }
    }

    // Bug #6 fix: added missing "OF Clôturés" row + Bug #7: period header added
    public byte[] generateGlobalOfReportPdf(GlobalOfReportDto data, LocalDateTime start, LocalDateTime end) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph("Rapport Global des OF",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));
            addPeriodHeader(document, start, end);

            PdfPTable table = new PdfPTable(2);
            table.setWidthPercentage(80);

            table.addCell(createCell("Total OF"));
            table.addCell(createCell(String.valueOf(data.getTotalOf())));

            table.addCell(createCell("OF Planifiés"));
            table.addCell(createCell(String.valueOf(data.getPlannedOf())));

            table.addCell(createCell("OF En Cours"));
            table.addCell(createCell(String.valueOf(data.getInProgressOf())));

            table.addCell(createCell("OF Terminés"));
            table.addCell(createCell(String.valueOf(data.getCompletedOf())));

            // Bug #6: this row existed in the DTO but was never written to the PDF
            table.addCell(createCell("OF Clôturés"));
            table.addCell(createCell(String.valueOf(data.getCanceledOf())));

            table.addCell(createCell("Quantité Cible Globale"));
            table.addCell(createCell(data.getTotalTargetQuantity().toString()));

            table.addCell(createCell("Quantité Produite Globale"));
            table.addCell(createCell(data.getTotalProducedQuantity().toString()));

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF Global OF", e);
        }
    }

    // Bug #7 fix: period header added
    public byte[] generateQualityReportPdf(List<QualityReportDto> data, LocalDateTime start, LocalDateTime end) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph("Rapport de Qualité (Non-conformité)",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));
            addPeriodHeader(document, start, end);

            PdfPTable table = new PdfPTable(4);
            table.setWidthPercentage(100);

            for (String h : new String[]{"Produit", "Contrôles Totaux", "Contrôles Échoués", "Taux Non-conformité %"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
                cell.setBackgroundColor(Color.LIGHT_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);
            }

            for (QualityReportDto dto : data) {
                table.addCell(createCell(dto.getProductName()));
                table.addCell(createCell(String.valueOf(dto.getTotalControls())));
                table.addCell(createCell(String.valueOf(dto.getFailedControls())));
                table.addCell(createCell(dto.getNonConformityRate() != null ? dto.getNonConformityRate().toString() + "%" : "0%"));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF Qualité", e);
        }
    }

    // Bug #7 fix: period header added
    public byte[] generateBomGapReportPdf(List<BomGapDto> data, LocalDateTime start, LocalDateTime end) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph("Rapport des Écarts BOM",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));
            addPeriodHeader(document, start, end);

            PdfPTable table = new PdfPTable(5);
            table.setWidthPercentage(100);

            for (String h : new String[]{"Matière", "Qté Prévue", "Qté Réelle", "Écart", "Écart %"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
                cell.setBackgroundColor(Color.LIGHT_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);
            }

            for (BomGapDto dto : data) {
                table.addCell(createCell(dto.getMaterialName()));
                table.addCell(createCell(dto.getPlannedQuantity() != null ? dto.getPlannedQuantity().toString() : "0"));
                table.addCell(createCell(dto.getActualQuantity() != null ? dto.getActualQuantity().toString() : "0"));
                table.addCell(createCell(dto.getGapQuantity() != null ? dto.getGapQuantity().toString() : "0"));
                table.addCell(createCell(dto.getGapPercentage() != null ? dto.getGapPercentage().toString() + "%" : "0%"));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF BOM", e);
        }
    }

    // Bug #7 fix: period header added
    public byte[] generateFiltrationReportPdf(List<FiltrationReportDto> data, LocalDateTime start, LocalDateTime end) {
        try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, out);
            document.open();

            Paragraph title = new Paragraph("Rapport Efficacité Filtrage",
                    FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18));
            title.setAlignment(Element.ALIGN_CENTER);
            document.add(title);
            document.add(new Paragraph(" "));
            addPeriodHeader(document, start, end);

            PdfPTable table = new PdfPTable(6);
            table.setWidthPercentage(100);

            for (String h : new String[]{"ID Opération", "Date", "Vol. Entrée", "Vol. Sortie", "Perte", "Efficacité %"}) {
                PdfPCell cell = new PdfPCell(new Phrase(h, FontFactory.getFont(FontFactory.HELVETICA_BOLD)));
                cell.setBackgroundColor(Color.LIGHT_GRAY);
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setPadding(5);
                table.addCell(cell);
            }

            for (FiltrationReportDto dto : data) {
                table.addCell(createCell(dto.getOperationId()));
                table.addCell(createCell(dto.getOperationDate() != null ? dto.getOperationDate().format(FORMATTER) : ""));
                table.addCell(createCell(dto.getInputVolume() != null ? dto.getInputVolume().toString() : "0"));
                table.addCell(createCell(dto.getOutputVolume() != null ? dto.getOutputVolume().toString() : "0"));
                table.addCell(createCell(dto.getLossVolume() != null ? dto.getLossVolume().toString() : "0"));
                table.addCell(createCell(dto.getEfficiencyRate() != null ? dto.getEfficiencyRate().toString() + "%" : "0%"));
            }

            document.add(table);
            document.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erreur génération PDF Filtrage", e);
        }
    }
}
