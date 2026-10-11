package com.rewin.itsm.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.rewin.itsm.common.BadRequestException;
import com.rewin.itsm.service.AttachmentFileTypes.FileType;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class AttachmentFileTypesTest {

    @Test
    void theContentTypeComesFromTheExtensionWhateverItsCase() {
        FileType type = AttachmentFileTypes.fromFileName("Report.PDF");
        assertThat(type.contentType()).isEqualTo("application/pdf");
        assertThat(type.extension()).isEqualTo("pdf");
        assertThat(type.fileName()).isEqualTo("Report.PDF");
    }

    @Test
    void extensionsOffTheListAreRejected() {
        for (String name : new String[] {"run.exe", "page.html", "image.svg", "script.js", "archive.zip", "README"}) {
            assertThatThrownBy(() -> AttachmentFileTypes.fromFileName(name))
                .as(name)
                .isInstanceOf(BadRequestException.class);
        }
    }

    @Test
    void aPathSentWithTheNameIsStripped() {
        assertThat(AttachmentFileTypes.cleanName("C:\\Users\\me\\Desktop\\shot.png")).isEqualTo("shot.png");
        assertThat(AttachmentFileTypes.cleanName("../../etc/passwd.txt")).isEqualTo("passwd.txt");
    }

    @Test
    void controlCharactersAreRemovedAndABlankNameGetsADefault() {
        assertThat(AttachmentFileTypes.cleanName("bad\r\nname.txt")).isEqualTo("badname.txt");
        assertThat(AttachmentFileTypes.cleanName("   ")).isEqualTo("attachment");
        assertThat(AttachmentFileTypes.cleanName(null)).isEqualTo("attachment");
    }

    @Test
    void aVeryLongNameIsShortenedButKeepsItsExtension() {
        String cleaned = AttachmentFileTypes.cleanName("a".repeat(400) + ".png");
        assertThat(cleaned).hasSize(255).endsWith(".png");
    }

    @Test
    void realFilesPassTheContentCheck() {
        assertMatches("a.png", new byte[] {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A});
        assertMatches("a.jpg", new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
        assertMatches("a.gif", "GIF89a...".getBytes(StandardCharsets.US_ASCII));
        assertMatches("a.webp", "RIFF\0\0\0\0WEBPVP8 ".getBytes(StandardCharsets.US_ASCII));
        assertMatches("a.pdf", "%PDF-1.7".getBytes(StandardCharsets.US_ASCII));
        assertMatches("a.docx", new byte[] {'P', 'K', 3, 4, 20, 0});
        assertMatches("a.csv", "id,name\n1,disk".getBytes(StandardCharsets.UTF_8));
        assertMatches("a.log", "2026-10-11 ERROR boom".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void contentThatDoesNotMatchTheExtensionIsRejected() {
        byte[] windowsExecutable = {'M', 'Z', (byte) 0x90, 0};
        for (String name : new String[] {"a.png", "a.jpg", "a.gif", "a.webp", "a.pdf", "a.docx", "a.txt"}) {
            FileType type = AttachmentFileTypes.fromFileName(name);
            assertThatThrownBy(() -> AttachmentFileTypes.requireContentMatches(type, windowsExecutable))
                .as(name)
                .isInstanceOf(BadRequestException.class);
        }
    }

    private void assertMatches(String name, byte[] head) {
        FileType type = AttachmentFileTypes.fromFileName(name);
        assertThatCode(() -> AttachmentFileTypes.requireContentMatches(type, head)).as(name).doesNotThrowAnyException();
    }
}
