package org.folio.marc.migrations.services.batch.support;

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
}
