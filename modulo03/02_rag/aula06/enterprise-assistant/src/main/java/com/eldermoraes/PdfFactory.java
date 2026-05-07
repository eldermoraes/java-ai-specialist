package com.eldermoraes;

import jakarta.enterprise.context.ApplicationScoped;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@ApplicationScoped
public class PdfFactory {

    private static final int FONT_SIZE = 12;
    private static final float MARGIN = 50f;
    private static final float LEADING = 14.5f;
    private static final int MAX_CHARS_PER_LINE = 90;

    public void generate(String docName, String content, Path target) {
        try {
            Files.createDirectories(target.getParent());
            if (Files.exists(target)) {
                System.out.println(">> [PdfFactory] PDF já existe, pulando geração: " + target);
                return;
            }
            try (PDDocument doc = new PDDocument()) {
                List<String> lines = wrap(docName, content);
                writePages(doc, lines);
                doc.save(target.toFile());
                System.out.println(">> [PdfFactory] PDF gerado em " + target);
            }
        } catch (IOException e) {
            throw new RuntimeException("Falha ao gerar PDF " + target, e);
        }
    }

    private List<String> wrap(String docName, String content) {
        List<String> out = new ArrayList<>();
        out.add(docName);
        out.add("");
        for (String paragraph : content.split("\\R\\R+")) {
            String para = paragraph.strip();
            if (para.isEmpty()) {
                out.add("");
                continue;
            }
            wrapParagraph(para, out);
            out.add("");
        }
        return out;
    }

    private void wrapParagraph(String para, List<String> out) {
        StringBuilder line = new StringBuilder();
        for (String word : para.split("\\s+")) {
            if (line.length() == 0) {
                line.append(word);
            } else if (line.length() + 1 + word.length() <= MAX_CHARS_PER_LINE) {
                line.append(' ').append(word);
            } else {
                out.add(line.toString());
                line.setLength(0);
                line.append(word);
            }
        }
        if (line.length() > 0) out.add(line.toString());
    }

    private void writePages(PDDocument doc, List<String> lines) throws IOException {
        PDType1Font font = PDType1Font.HELVETICA;
        PDPage page = new PDPage();
        doc.addPage(page);
        PDPageContentStream cs = new PDPageContentStream(doc, page);
        try {
            cs.setFont(font, FONT_SIZE);
            cs.beginText();
            float y = page.getMediaBox().getHeight() - MARGIN;
            cs.newLineAtOffset(MARGIN, y);
            float current = y;
            for (String line : lines) {
                if (current < MARGIN + LEADING) {
                    cs.endText();
                    cs.close();
                    page = new PDPage();
                    doc.addPage(page);
                    cs = new PDPageContentStream(doc, page);
                    cs.setFont(font, FONT_SIZE);
                    cs.beginText();
                    current = page.getMediaBox().getHeight() - MARGIN;
                    cs.newLineAtOffset(MARGIN, current);
                }
                cs.showText(line);
                cs.newLineAtOffset(0, -LEADING);
                current -= LEADING;
            }
            cs.endText();
        } finally {
            cs.close();
        }
    }
}
