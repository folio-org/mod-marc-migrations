package org.folio.marc.migrations.services.batch.support;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.folio.s3.client.FolioS3Client;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Component;

@Log4j2
@Component
@RequiredArgsConstructor
public class FolioS3Service {

  private final FolioS3Client s3Client;

  @Retryable(
    retryFor = Exception.class,
    maxAttemptsExpression = "${folio.remote-storage.retryCount}",
    backoff = @Backoff(delayExpression = "${folio.remote-storage.retryDelayMs}"))
  public void uploadFile(String localPath, String remotePath) {
    s3Client.upload(localPath, remotePath);
  }

  @Retryable(
    retryFor = Exception.class,
    maxAttemptsExpression = "${folio.remote-storage.retryCount}",
    backoff = @Backoff(delayExpression = "${folio.remote-storage.retryDelayMs}"))
  public void writeFile(String path, List<String> lines) {
    var content = String.join("\n", lines);
    var inputStream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    s3Client.write(path, inputStream);
  }

  public List<String> readFile(String remotePath) {
    try (var inputStream = s3Client.read(remotePath);
         var reader = new BufferedReader(new InputStreamReader(inputStream))) {
      return reader.lines().toList();
    } catch (Exception e) {
      log.error("readFile::Error reading file [filename: {}]", remotePath, e);
      throw new IllegalStateException("Error reading file: " + remotePath, e);
    }
  }
}
