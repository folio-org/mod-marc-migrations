package org.folio.marc.migrations.services.batch.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import java.io.ByteArrayInputStream;
import java.util.List;
import org.folio.marc.migrations.config.RemoteStorageConfig;
import org.folio.s3.client.FolioS3Client;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@UnitTest
@EnableRetry
@Import(RemoteStorageConfig.class)
@SpringBootTest(classes = FolioS3Service.class, webEnvironment = NONE)
class FolioS3ServiceTest {

  private static final String PATH = "test-path";
  private static final List<String> LINES = List.of("line1", "line2", "line3");

  private @MockitoBean FolioS3Client s3Client;
  private @Autowired FolioS3Service service;

  @Test
  void uploadFile_shouldRetryOnException() {
    when(s3Client.upload(any(), any()))
      .thenThrow(IllegalStateException.class)
      .thenReturn("");

    service.uploadFile("test", "test");

    verify(s3Client, times(2)).upload(any(), any());
  }

  @Test
  void readFile_shouldReturnFileContent() {
    String content = "line1\nline2\nline3";
    when(s3Client.read(any())).thenReturn(new ByteArrayInputStream(content.getBytes()));

    List<String> result = service.readFile(PATH);

    assertThat(result)
      .hasSize(3)
      .containsExactly("line1", "line2", "line3");
  }

  @Test
  void readFile_shouldThrowException_whenIoExceptionOccurs() {
    when(s3Client.read(any())).thenThrow(new RuntimeException("S3 error"));

    assertThatThrownBy(() -> service.readFile(PATH))
      .isInstanceOf(IllegalStateException.class)
      .hasMessageContaining("Error reading file: test-path");
  }

  @Test
  void writeFile_shouldWriteContentSuccessfully() {
    // Act
    service.writeFile(PATH, LINES);

    // Assert
    verify(s3Client, times(1)).write(any(), any());
  }

  @Test
  void writeFile_shouldRetryOnException() {
    when(s3Client.write(any(), any())).thenThrow(new RuntimeException("S3 error"))
      .thenReturn(null);

    // Act
    service.writeFile(PATH, LINES);

    // Assert
    verify(s3Client, times(2)).write(any(), any());
  }

  @Test
  void writeFile_shouldThrowExceptionAfterRetries() {
    when(s3Client.write(any(), any())).thenThrow(new RuntimeException("S3 error"));

    // Act & Assert
    assertThatThrownBy(() -> service.writeFile(PATH, LINES)).isInstanceOf(RuntimeException.class)
      .hasMessageContaining("S3 error");

    verify(s3Client, times(3)).write(any(), any()); // Assuming maxAttempts is 3
  }
}
