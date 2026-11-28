package pe.edu.utec.devutec.assets;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.action.PDActionJavaScript;
import org.junit.jupiter.api.Test;
import pe.edu.utec.devutec.exceptions.ApiException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AssetFileProcessorTest {

    private final AssetFileProcessor processor = new AssetFileProcessor();

    @Test
    void pngWithTransparencyStaysPngAndIsReencoded() throws IOException {
        byte[] original = image(300, 200, BufferedImage.TYPE_INT_ARGB, "png");
        byte[] withTrailer = concat(original, "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8));

        AssetFileProcessor.ProcessedFile file = processor.process(withTrailer);

        assertThat(file.kind()).isEqualTo(ProjectAssetKind.IMAGE);
        assertThat(file.contentType()).isEqualTo("image/png");
        assertThat(new String(file.data(), StandardCharsets.ISO_8859_1)).doesNotContain("<script>");
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(file.data()));
        assertThat(read.getWidth()).isEqualTo(300);
    }

    @Test
    void photoBecomesJpegAndBigOnesAreResized() throws IOException {
        AssetFileProcessor.ProcessedFile file = processor.process(image(3000, 1500, BufferedImage.TYPE_INT_RGB, "jpeg"));

        assertThat(file.contentType()).isEqualTo("image/jpeg");
        BufferedImage read = ImageIO.read(new ByteArrayInputStream(file.data()));
        assertThat(read.getWidth()).isEqualTo(2000);
        assertThat(read.getHeight()).isEqualTo(1000);
    }

    @Test
    void renamedExecutableIsRejected() {
        byte[] exe = concat("MZ".getBytes(StandardCharsets.US_ASCII), new byte[100]);
        assertThatThrownBy(() -> processor.process(exe)).isInstanceOf(ApiException.class)
                .hasMessageContaining("JPG o PNG, o un PDF");
    }

    @Test
    void svgAndHtmlAreRejected() {
        byte[] svg = "<svg xmlns='http://www.w3.org/2000/svg'><script>alert(1)</script></svg>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> processor.process(svg)).isInstanceOf(ApiException.class);
    }

    @Test
    void brokenImageWithValidHeaderIsRejected() {
        byte[] fake = concat(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}, new byte[50]);
        assertThatThrownBy(() -> processor.process(fake)).isInstanceOf(ApiException.class)
                .hasMessageContaining("No pudimos leer la imagen");
    }

    @Test
    void tooLargeFileIsRejected() {
        byte[] big = new byte[(int) AssetFileProcessor.MAX_BYTES + 1];
        big[0] = '%'; big[1] = 'P'; big[2] = 'D'; big[3] = 'F'; big[4] = '-';
        assertThatThrownBy(() -> processor.process(big)).isInstanceOf(ApiException.class).hasMessageContaining("5 MB");
    }

    @Test
    void pdfWithTwoPagesIsAcceptedAndItsTextExtracted() throws IOException {
        AssetFileProcessor.ProcessedFile file = processor.process(pdf(2, false, false));

        assertThat(file.kind()).isEqualTo(ProjectAssetKind.PDF);
        assertThat(file.pages()).isEqualTo(2);
        assertThat(file.text()).contains("Pizzeria Ramon pagina 1").contains("pagina 2");
    }

    @Test
    void pdfWithThreePagesIsRejected() throws IOException {
        byte[] pdf = pdf(3, false, false);
        assertThatThrownBy(() -> processor.process(pdf)).isInstanceOf(ApiException.class).hasMessageContaining("2 páginas");
    }

    @Test
    void javascriptIsRemovedFromPdf() throws IOException {
        AssetFileProcessor.ProcessedFile file = processor.process(pdf(1, true, false));

        try (PDDocument clean = Loader.loadPDF(file.data())) {
            assertThat(clean.getDocumentCatalog().getCOSObject().containsKey(COSName.OPEN_ACTION)).isFalse();
            assertThat(clean.getDocumentCatalog().getCOSObject().containsKey(COSName.NAMES)).isFalse();
        }
        assertThat(new String(file.data(), StandardCharsets.ISO_8859_1)).doesNotContain("app.alert");
    }

    @Test
    void encryptedPdfIsRejected() throws IOException {
        byte[] pdf = pdf(1, false, true);
        assertThatThrownBy(() -> processor.process(pdf)).isInstanceOf(ApiException.class).hasMessageContaining("contraseña");
    }

    @Test
    void brokenPdfIsRejected() {
        byte[] pdf = "%PDF-1.7\nnada que ver".getBytes(StandardCharsets.US_ASCII);
        assertThatThrownBy(() -> processor.process(pdf)).isInstanceOf(ApiException.class);
    }

    private static byte[] image(int width, int height, int type, String format) throws IOException {
        BufferedImage image = new BufferedImage(width, height, type);
        image.setRGB(0, 0, 0xFFC0392B);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }

    private static byte[] pdf(int pages, boolean javascript, boolean encrypted) throws IOException {
        try (PDDocument document = new PDDocument()) {
            for (int i = 1; i <= pages; i++) {
                PDPage page = new PDPage();
                document.addPage(page);
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(50, 700);
                    content.showText("Pizzeria Ramon pagina " + i);
                    content.endText();
                }
            }
            if (javascript) {
                document.getDocumentCatalog().setOpenAction(new PDActionJavaScript("app.alert('hola')"));
            }
            if (encrypted) {
                StandardProtectionPolicy policy = new StandardProtectionPolicy("dueno", "clave", new AccessPermission());
                document.protect(policy);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        }
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
