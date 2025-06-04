package org.folio.marc.migrations.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;

@Getter
@Setter
@Entity
@ToString
@Table(name = "operation_error")
public class OperationError {

  @Id
  @Column(name = "id")
  private UUID id;
  @Column(name = "report_id")
  private UUID reportId;
  @Column(name = "operation_chunk_id")
  private UUID chunkId;
  @Enumerated(EnumType.STRING)
  @Column(name = "operation_step")
  private OperationStep operationStep;
  @Enumerated(EnumType.STRING)
  @Column(name = "chunk_status")
  private StepStatus chunkStatus;
  @Column(name = "record_id")
  private String recordId;
  @Column(name = "error_message")
  private String errorMessage;
}
