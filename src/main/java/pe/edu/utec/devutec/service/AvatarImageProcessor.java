package pe.edu.utec.devutec.service;

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
import java.util.Set;

// Convierte la foto que sube una persona en su foto de perfil: comprueba que de verdad sea JPG o PNG (leyendo la
// imagen, no la extensión), la recorta cuadrada desde el centro, la achica a 400 × 400 y la guarda como JPEG.
// Al volver a codificarla se pierden los metadatos de la foto original (por ejemplo, la ubicación GPS).
@Component
public class AvatarImageProcessor {

    public static final int SIZE = 400;
    public static final long MAX_BYTES = 5L * 1024 * 1024;
    // Evita que una imagen chica en bytes pero gigante en píxeles llene la memoria al abrirla
    private static final long MAX_PIXELS = 40_000_000L;
    private static final Set<String> FORMATS = Set.of("jpeg", "png");

    public byte[] process(byte[] original) {
        if (original == null || original.length == 0) {
            throw badRequest("Elige una imagen para tu foto de perfil");
        }
        if (original.length > MAX_BYTES) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "La foto puede pesar como máximo 5 MB");
        }
        return encodeJpeg(cropAndResize(read(original)));
    }

    private BufferedImage read(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) {
                throw badRequest("No pudimos leer la imagen. Prueba con otra foto JPG o PNG");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                throw badRequest("La foto tiene que ser una imagen JPG o PNG");
            }
            ImageReader reader = readers.next();
            try {
                if (!FORMATS.contains(reader.getFormatName().toLowerCase())) {
                    throw badRequest("La foto tiene que ser una imagen JPG o PNG");
                }
                reader.setInput(input, true, true);
                if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_PIXELS) {
                    throw badRequest("La foto es demasiado grande. Usa una de menos de 40 megapíxeles");
                }
                return reader.read(0);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof ApiException apiException) throw apiException;
            throw badRequest("No pudimos leer la imagen. Prueba con otra foto JPG o PNG");
        }
    }

    // Recorta el cuadrado más grande del centro y lo achica a SIZE × SIZE.
    // El fondo blanco cubre las partes transparentes de un PNG, porque JPEG no tiene transparencia.
    private BufferedImage cropAndResize(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;

        BufferedImage result = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, SIZE, SIZE);
            g.drawImage(source, 0, 0, SIZE, SIZE, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    private byte[] encodeJpeg(BufferedImage image) {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ImageOutputStream stream = ImageIO.createImageOutputStream(output)) {
            writer.setOutput(stream);
            ImageWriteParam params = writer.getDefaultWriteParam();
            params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            params.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), params);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo guardar la foto de perfil", e);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }

    private static ApiException badRequest(String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, message);
    }
}
