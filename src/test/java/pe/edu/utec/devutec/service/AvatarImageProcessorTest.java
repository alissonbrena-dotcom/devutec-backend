package pe.edu.utec.devutec.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import pe.edu.utec.devutec.exceptions.ApiException;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class AvatarImageProcessorTest {

    private final AvatarImageProcessor processor = new AvatarImageProcessor();

    private static byte[] encode(BufferedImage image, String format) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        assertTrue(ImageIO.write(image, format, output), "no se pudo crear la imagen de prueba en " + format);
        return output.toByteArray();
    }

    // Imagen apaisada de 800 × 400: 200 px rojo, 400 px verde en el centro y 200 px azul
    private static BufferedImage threeStripes() {
        BufferedImage image = new BufferedImage(800, 400, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, 200, 400);
        g.setColor(Color.GREEN);
        g.fillRect(200, 0, 400, 400);
        g.setColor(Color.BLUE);
        g.fillRect(600, 0, 200, 400);
        g.dispose();
        return image;
    }

    private static String formatOf(byte[] bytes) throws IOException {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            ImageReader reader = ImageIO.getImageReaders(input).next();
            return reader.getFormatName().toLowerCase();
        }
    }

    private static boolean isGreenish(int rgb) {
        Color c = new Color(rgb);
        return c.getGreen() > 200 && c.getRed() < 60 && c.getBlue() < 60;
    }

    // TEST 1: un PNG apaisado sale como JPEG cuadrado de 400 × 400, recortado desde el centro
    @Test
    void process_pngApaisado_deberiaRecortarElCentroA400x400() throws IOException {
        byte[] result = processor.process(encode(threeStripes(), "png"));

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals("jpeg", formatOf(result));
        assertEquals(400, image.getWidth());
        assertEquals(400, image.getHeight());
        // Las esquinas son de la franja verde del centro: el rojo y el azul de los costados se recortaron
        assertTrue(isGreenish(image.getRGB(5, 5)));
        assertTrue(isGreenish(image.getRGB(394, 394)));
    }

    // TEST 2: un JPEG vertical también queda cuadrado
    @Test
    void process_jpegVertical_deberiaQuedarCuadrado() throws IOException {
        BufferedImage portrait = new BufferedImage(300, 900, BufferedImage.TYPE_INT_RGB);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(processor.process(encode(portrait, "jpg"))));

        assertEquals(400, image.getWidth());
        assertEquals(400, image.getHeight());
    }

    // TEST 3: lo transparente de un PNG queda blanco (JPEG no tiene transparencia)
    @Test
    void process_pngTransparente_deberiaQuedarConFondoBlanco() throws IOException {
        BufferedImage transparent = new BufferedImage(500, 500, BufferedImage.TYPE_INT_ARGB);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(processor.process(encode(transparent, "png"))));

        Color corner = new Color(image.getRGB(10, 10));
        assertTrue(corner.getRed() > 245 && corner.getGreen() > 245 && corner.getBlue() > 245);
    }

    // TEST 4: un GIF de verdad se rechaza aunque sea una imagen válida
    @Test
    void process_gif_deberiaRechazarlo() throws IOException {
        byte[] gif = encode(new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB), "gif");

        ApiException ex = assertThrows(ApiException.class, () -> processor.process(gif));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getMessage().contains("JPG o PNG"));
    }

    // TEST 5: un archivo que no es imagen (por ejemplo, un .png que en realidad es texto) se rechaza
    @Test
    void process_textoConExtensionDeImagen_deberiaRechazarlo() {
        byte[] fake = "esto no es una foto".getBytes(StandardCharsets.UTF_8);

        ApiException ex = assertThrows(ApiException.class, () -> processor.process(fake));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // TEST 6: sin archivo se pide elegir una imagen
    @Test
    void process_vacio_deberiaPedirUnaImagen() {
        ApiException ex = assertThrows(ApiException.class, () -> processor.process(new byte[0]));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    // TEST 7: más de 5 MB responde 413
    @Test
    void process_masDe5MB_deberiaResponder413() {
        byte[] huge = new byte[(int) AvatarImageProcessor.MAX_BYTES + 1];

        ApiException ex = assertThrows(ApiException.class, () -> processor.process(huge));

        assertEquals(HttpStatus.CONTENT_TOO_LARGE, ex.getStatus());
    }
}
