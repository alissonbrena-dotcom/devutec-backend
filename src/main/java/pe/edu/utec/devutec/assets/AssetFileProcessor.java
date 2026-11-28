package pe.edu.utec.devutec.assets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentCatalog;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import pe.edu.utec.devutec.exceptions.ApiException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

// Revisa y limpia lo que sube el cliente para que lo que descarga el estudiante sea seguro.
// - Imágenes: solo JPG o PNG, comprobado leyendo el archivo (no la extensión). Se vuelven a dibujar en una imagen
//   nueva, así se pierde cualquier cosa escondida en el archivo y los metadatos (por ejemplo, la ubicación GPS).
//   Las que tienen transparencia (un logo) quedan en PNG; las demás en JPEG. Máximo 2000 px de lado.
// - PDF: máximo 2 páginas, sin contraseña. Se le quita todo lo que puede ejecutar algo al abrirlo (JavaScript,
//   acciones automáticas, archivos incrustados, formularios y links) y se guarda de nuevo. Se saca su texto para la IA.
@Component
public class AssetFileProcessor {

    public static final long MAX_BYTES = 5L * 1024 * 1024;
    public static final int MAX_SIDE = 2000;
    public static final int MAX_PDF_PAGES = 2;
    public static final int MAX_PDF_TEXT = 4000;
    // Evita que una imagen chica en bytes pero gigante en píxeles llene la memoria al abrirla
    private static final long MAX_PIXELS = 40_000_000L;

    public record ProcessedFile(ProjectAssetKind kind, byte[] data, String contentType, String extension,
                                Integer pages, String text) {
    }

    public ProcessedFile process(byte[] original) {
        if (original == null || original.length == 0) {
            throw badRequest("Elige un archivo");
        }
        if (original.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "El archivo puede pesar como máximo 5 MB");
        }
        if (isPdf(original)) {
            return processPdf(original);
        }
        if (isPng(original) || isJpeg(original)) {
            return processImage(original);
        }
        throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Solo se pueden subir imágenes JPG o PNG, o un PDF");
    }

    // ---------- Imágenes ----------

    private ProcessedFile processImage(byte[] original) {
        BufferedImage source = readImage(original);
        boolean transparent = source.getColorModel().hasAlpha();
        double scale = Math.min(1.0, (double) MAX_SIDE / Math.max(source.getWidth(), source.getHeight()));
        int width = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int height = Math.max(1, (int) Math.round(source.getHeight() * scale));

        BufferedImage clean = new BufferedImage(width, height, transparent ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB);
        Graphics2D g = clean.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            if (!transparent) {
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, width, height);
            }
            g.drawImage(source, 0, 0, width, height, null);
        } finally {
            g.dispose();
        }
        return transparent
                ? new ProcessedFile(ProjectAssetKind.IMAGE, encode(clean, "png"), "image/png", "png", null, null)
                : new ProcessedFile(ProjectAssetKind.IMAGE, encode(clean, "jpeg"), "image/jpeg", "jpg", null, null);
    }

    private BufferedImage readImage(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw badRequest("No pudimos leer la imagen. Prueba con otra JPG o PNG");
            }
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase();
                if (!format.equals("jpeg") && !format.equals("png")) {
                    throw badRequest("La imagen tiene que ser JPG o PNG");
                }
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw badRequest("La imagen es demasiado grande. Usa una de menos de 40 megapíxeles");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException apiException) throw apiException;
            throw badRequest("No pudimos leer la imagen. Prueba con otra JPG o PNG");
        }
    }

    private static byte[] encode(BufferedImage image, String format) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName(format).next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            if (format.equals("jpeg")) {
                params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                params.setCompressionQuality(0.88f);
            }
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la imagen", e);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    // ---------- PDF ----------

    private ProcessedFile processPdf(byte[] original) {
        try (PDDocument document = Loader.loadPDF(original)) {
            if (document.isEncrypted()) {
                throw badRequest("El PDF tiene contraseña. Sube uno sin contraseña");
            }
            int pages = document.getNumberOfPages();
            if (pages == 0) {
                throw badRequest("El PDF está vacío");
            }
            if (pages > MAX_PDF_PAGES) {
                throw badRequest("El PDF puede tener como máximo " + MAX_PDF_PAGES + " páginas");
            }
            removeActiveContent(document);
            String text = new PDFTextStripper().getText(document).replaceAll("\\s+", " ").trim();
            if (text.length() > MAX_PDF_TEXT) text = text.substring(0, MAX_PDF_TEXT);
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            document.save(output);
            return new ProcessedFile(ProjectAssetKind.PDF, output.toByteArray(), "application/pdf", "pdf", pages, text);
        } catch (InvalidPasswordException e) {
            throw badRequest("El PDF tiene contraseña. Sube uno sin contraseña");
        } catch (IOException e) {
            throw badRequest("No pudimos leer el PDF. Prueba con otro archivo");
        }
    }

    // Quita lo que un visor de PDF podría ejecutar al abrirlo: acciones automáticas, JavaScript y archivos incrustados
    // (árbol Names), formularios (AcroForm, incluido XFA), y en cada página sus anotaciones (links, adjuntos) y acciones
    static void removeActiveContent(PDDocument document) {
        PDDocumentCatalog catalog = document.getDocumentCatalog();
        catalog.getCOSObject().removeItem(COSName.OPEN_ACTION);
        catalog.getCOSObject().removeItem(COSName.AA);
        catalog.getCOSObject().removeItem(COSName.NAMES);
        catalog.getCOSObject().removeItem(COSName.ACRO_FORM);
        catalog.getCOSObject().removeItem(COSName.URI);
        for (PDPage page : document.getPages()) {
            page.getCOSObject().removeItem(COSName.ANNOTS);
            page.getCOSObject().removeItem(COSName.AA);
        }
    }

    // ---------- Firmas de archivo (los primeros bytes, no la extensión) ----------

    static boolean isPdf(byte[] b) {
        return b.length >= 5 && b[0] == '%' && b[1] == 'P' && b[2] == 'D' && b[3] == 'F' && b[4] == '-';
    }

    static boolean isPng(byte[] b) {
        return b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G';
    }

    static boolean isJpeg(byte[] b) {
        return b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF;
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
