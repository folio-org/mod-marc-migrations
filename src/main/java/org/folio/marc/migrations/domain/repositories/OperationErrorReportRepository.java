package org.folio.marc.migrations.domain.repositories;

import java.util.UUID;
import org.folio.marc.migrations.domain.entities.OperationErrorReport;
import org.folio.marc.migrations.domain.entities.types.ErrorReportStatus;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import org.springframework.transaction.annotation.Transactional;

public interface OperationErrorReportRepository extends CrudRepository<OperationErrorReport, UUID> {

  @Transactional
  @Modifying
  @Query("update OperationErrorReport o set o.status = ?1 where o.id = ?2")
  int updateStatusById(ErrorReportStatus status, UUID id);
}
