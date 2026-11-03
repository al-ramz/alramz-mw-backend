package com.alramz.service;

import com.alramz.config.DmsProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UploadSpoolerTest {

    @TempDir
    Path tempDir;

    private UploadSpooler spooler;

    @BeforeEach
    void setUp() {
        DmsProperties properties = new DmsProperties();
        properties.getUpload().setTempDir(tempDir);
        spooler = new UploadSpooler(properties);
    }

    @Test
    void spool_computesLowercaseSha256AndUsesARandomName() throws Exception {
        byte[] bytes = "%PDF-1.7 synthetic passport".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile("file", "passport-FIT-100234.pdf", "application/pdf", bytes);

        SpooledUpload spooled = spooler.spool(file);

        String expected = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        assertThat(spooled.sha256Hex()).isEqualTo(expected).isLowerCase().hasSize(64);
        assertThat(spooled.size()).isEqualTo(bytes.length);
        assertThat(Files.readAllBytes(spooled.path())).isEqualTo(bytes);
        assertThat(spooled.path().getParent()).isEqualTo(tempDir);
        String tempName = spooled.path().getFileName().toString();
        assertThat(tempName).endsWith(".upload").doesNotContain("passport").doesNotContain("FIT-100234");

        UploadSpooler.deleteQuietly(spooled.path());
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void spool_createsAMissingTempDir() {
        DmsProperties properties = new DmsProperties();
        properties.getUpload().setTempDir(tempDir.resolve("nested/spool"));

        SpooledUpload spooled = new UploadSpooler(properties).spool(new MockMultipartFile("file", new byte[]{1}));

        assertThat(spooled.path()).exists();
    }

    @Test
    void ioFailure_deletesTheTempFileAndRethrows() throws Exception {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getInputStream()).thenReturn(new InputStream() {
            private int reads;

            @Override
            public int read() throws IOException {
                if (reads++ > 3) {
                    throw new IOException("client went away");
                }
                return 'x';
            }
        });

        assertThatThrownBy(() -> spooler.spool(file)).isInstanceOf(UncheckedIOException.class);
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void deleteQuietly_toleratesNullAndMissing() {
        UploadSpooler.deleteQuietly(null);
        UploadSpooler.deleteQuietly(tempDir.resolve("missing.upload"));
        assertThat(tempDir).isEmptyDirectory();
    }

    @Test
    void deleteQuietly_swallowsDeleteFailures() throws Exception {
        Path nonEmpty = Files.createDirectories(tempDir.resolve("busy"));
        Files.writeString(nonEmpty.resolve("x.upload"), "x");

        UploadSpooler.deleteQuietly(nonEmpty); // DirectoryNotEmptyException is swallowed

        assertThat(nonEmpty).exists();
    }

    @Test
    void unusableTempDir_isAnIoFailure() throws Exception {
        Path aFile = Files.writeString(tempDir.resolve("not-a-dir"), "x");
        DmsProperties properties = new DmsProperties();
        properties.getUpload().setTempDir(aFile);

        assertThatThrownBy(() -> new UploadSpooler(properties).spool(new MockMultipartFile("file", new byte[]{1})))
                .isInstanceOf(UncheckedIOException.class)
                .hasMessage("Could not spool the upload to the temp directory");
    }
}
