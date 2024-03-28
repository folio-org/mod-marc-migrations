package org.folio.marc.migrations.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.folio.marc.migrations.domain.entities.types.OperationStep;
import org.folio.marc.migrations.domain.entities.types.StepStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@ToString
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "operation_chunk_step")
public class ChunkStep {

  @Id
  @Column(nullable = false)
  private UUID id;

  @Column(nullable = false)
  private UUID operationId;

  @Column(nullable = false)
  private UUID operationChunkId;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private OperationStep operationStep;

  @Column
  private String entityErrorChunkFileName;

  @Column
  private String errorChunkFileName;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private StepStatus status;

  private Timestamp stepStartTime;

  private Timestamp stepEndTime;

  @Column(nullable = false)
  private Integer numOfErrors;

}
