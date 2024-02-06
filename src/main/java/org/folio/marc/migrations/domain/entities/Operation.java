package org.folio.marc.migrations.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.sql.Timestamp;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.folio.marc.migrations.domain.entities.types.EntityType;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.folio.marc.migrations.domain.entities.types.OperationType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Table(name = "operation")
public class Operation {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", nullable = false)
  private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Enumerated(EnumType.STRING)
  @Column(name = "entity_type", nullable = false, length = 20)
  private EntityType entityType;

  @Enumerated(EnumType.STRING)
  @Column(name = "operation_type", nullable = false, length = 20)
  private OperationType operationType;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(name = "status", nullable = false)
  private OperationStatusType status;

  @Column(name = "total_num_of_records", nullable = false)
  private Integer totalNumOfRecords;

  @Column(name = "processed_num_of_records", nullable = false)
  private Integer processedNumOfRecords;

  @Column(name = "start_time_mapping")
  private Timestamp startTimeMapping;

  @Column(name = "end_time_mapping")
  private Timestamp endTimeMapping;

  @Column(name = "start_time_saving")
  private Timestamp startTimeSaving;

  @Column(name = "end_time_saving")
  private Timestamp endTimeSaving;

}
