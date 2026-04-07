package service;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PdfPageRenderingService {
    private static final float RENDER_DPI = 144.0f;

    public List<File> renderPdfPages(File pdfFile, Path outputDirectory) {
        try {
            Files.createDirectories(outputDirectory);
            List<File> renderedPages = new ArrayList<>();

            try (PDDocument document = Loader.loadPDF(pdfFile)) {
                PDFRenderer renderer = new PDFRenderer(document);
                String baseFileName = baseFileName(pdfFile.getName());

                for (int pageIndex = 0; pageIndex < document.getNumberOfPages(); pageIndex++) {
                    BufferedImage pageImage = renderer.renderImageWithDPI(pageIndex, RENDER_DPI, ImageType.RGB);
                    Path pagePath = outputDirectory.resolve("%s-page-%03d.png".formatted(baseFileName, pageIndex + 1));
                    ImageIO.write(pageImage, "png", pagePath.toFile());
                    renderedPages.add(pagePath.toFile());
                }
            }

            return renderedPages;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to render PDF pages.", exception);
        }
    }

    private String baseFileName(String fileName) {
        int extensionIndex = fileName.lastIndexOf('.');
        String baseName = extensionIndex < 0 ? fileName : fileName.substring(0, extensionIndex);
        return baseName.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}
