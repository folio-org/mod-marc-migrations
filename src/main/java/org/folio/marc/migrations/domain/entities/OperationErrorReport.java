package org.folio.marc.migrations.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.folio.marc.migrations.domain.entities.types.ErrorReportStatus;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@ToString
@Table(name = "operation_error_report")
public class OperationErrorReport {

  @Id
  @Column(name = "id")
  private UUID id;
  @Column(name = "operation_id")
  private UUID operationId;
  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status")
  private ErrorReportStatus status;
  @Column(name = "started_at", insertable = false, updatable = false)
  private Timestamp startedAt;
  @Column(name = "finished_at", insertable = false, updatable = false)
  private Timestamp finishedAt;
}
