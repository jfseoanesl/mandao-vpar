package com.mandao.evidence;

import com.mandao.shared.ApiException;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

/**
 * Valida archivos por su firma binaria (no por extensión ni por el Content-Type del cliente)
 * y re-codifica imágenes, lo que elimina metadatos EXIF y cualquier contenido embebido.
 */
public final class FileInspector {

    public record CleanFile(byte[] data, String contentType, String extension) {
    }

    static final long MAX_BYTES = 8L * 1024 * 1024;
    private static final int MAX_DIMENSION = 2048;
    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};

    private FileInspector() {
    }

    public static CleanFile inspect(MultipartFile file, boolean allowPdf) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Archivo requerido");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("El archivo supera 8 MB");
        }
        try {
            return inspect(file.getBytes(), allowPdf);
        } catch (IOException e) {
            throw ApiException.badRequest("No se pudo leer el archivo");
        }
    }

    public static CleanFile inspect(byte[] data, boolean allowPdf) {
        if (startsWith(data, JPEG)) {
            return new CleanFile(reencode(data, "jpg"), "image/jpeg", "jpg");
        }
        if (startsWith(data, PNG)) {
            return new CleanFile(reencode(data, "png"), "image/png", "png");
        }
        if (allowPdf && startsWith(data, PDF)) {
            return new CleanFile(data, "application/pdf", "pdf");
        }
        throw ApiException.badRequest(allowPdf ? "Formato no permitido. Usa JPG, PNG o PDF."
                : "Formato no permitido. Usa JPG o PNG.");
    }

    private static byte[] reencode(byte[] data, String format) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(data));
            if (image == null) {
                throw ApiException.badRequest("La imagen está dañada o no es válida");
            }
            image = downscale(image, format);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (!ImageIO.write(image, format, out)) {
                throw ApiException.badRequest("No se pudo procesar la imagen");
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw ApiException.badRequest("La imagen está dañada o no es válida");
        }
    }

    private static BufferedImage downscale(BufferedImage src, String format) {
        int w = src.getWidth();
        int h = src.getHeight();
        double scale = Math.min(1.0, (double) MAX_DIMENSION / Math.max(w, h));
        boolean jpeg = "jpg".equals(format);
        if (scale >= 1.0 && !(jpeg && src.getColorModel().hasAlpha())) {
            return src;
        }
        int nw = Math.max(1, (int) Math.round(w * scale));
        int nh = Math.max(1, (int) Math.round(h * scale));
        BufferedImage dst = new BufferedImage(nw, nh, jpeg ? BufferedImage.TYPE_INT_RGB : BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = dst.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(src, 0, 0, nw, nh, null);
        g.dispose();
        return dst;
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        return data.length >= prefix.length && Arrays.equals(data, 0, prefix.length, prefix, 0, prefix.length);
    }
}
