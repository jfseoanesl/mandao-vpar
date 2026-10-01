package com.mandao.evidence;

import com.mandao.shared.ApiException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileInspectorTest {

    static byte[] png(int w, int h) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), "png", out);
        return out.toByteArray();
    }

    @Test
    @DisplayName("AC-005-06 · contenido que no es imagen se rechaza aunque diga .jpg")
    void rejectsFakeImage() {
        byte[] fake = "<script>alert(1)</script>".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> FileInspector.inspect(fake, false)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("Imagen PNG válida se acepta y se re-codifica")
    void acceptsPng() throws IOException {
        FileInspector.CleanFile clean = FileInspector.inspect(png(10, 10), false);
        assertThat(clean.contentType()).isEqualTo("image/png");
        assertThat(clean.extension()).isEqualTo("png");
    }

    @Test
    @DisplayName("Imágenes grandes se reducen a máx. 2048 px")
    void downscales() throws IOException {
        FileInspector.CleanFile clean = FileInspector.inspect(png(3000, 1000), false);
        BufferedImage img = ImageIO.read(new java.io.ByteArrayInputStream(clean.data()));
        assertThat(img.getWidth()).isEqualTo(2048);
    }

    @Test
    @DisplayName("PDF solo donde se permite (facturas, documentos)")
    void pdfOnlyWhenAllowed() {
        byte[] pdf = "%PDF-1.4 test".getBytes(StandardCharsets.US_ASCII);
        assertThat(FileInspector.inspect(pdf, true).contentType()).isEqualTo("application/pdf");
        assertThatThrownBy(() -> FileInspector.inspect(pdf, false)).isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("PNG con firma válida pero cuerpo dañado se rechaza")
    void rejectsCorruptPng() {
        byte[] corrupt = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2, 3};
        assertThatThrownBy(() -> FileInspector.inspect(corrupt, false)).isInstanceOf(ApiException.class);
    }
}
