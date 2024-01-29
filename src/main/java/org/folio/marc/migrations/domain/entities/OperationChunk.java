package org.folio.marc.migrations.domain.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.folio.marc.migrations.domain.entities.types.OperationStatusType;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Setter
@Entity
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "operation_chunk")
public class OperationChunk {

  @Id
  @Column(nullable = false)
  private UUID id;

  @ToString.Exclude
  @ManyToOne
  @JoinColumn(name = "operation_id", nullable = false)
  private Operation operation;

  /**
   * Interval start record id, inclusive.
   */
  @Column(nullable = false)
  private UUID startRecordId;

  /**
   * Interval end record id, inclusive.
   */
  @Column(nullable = false)
  private UUID endRecordId;

  @Column
  private String sourceChunkFileName;

  @Column
  private String marcChunkFileName;

  @Column
  private String entityChunkFileName;

  @Enumerated(EnumType.STRING)
  @JdbcTypeCode(SqlTypes.NAMED_ENUM)
  @Column(nullable = false)
  private OperationStatusType status;

  @Column(nullable = false)
  private Integer numOfRecords;

}
