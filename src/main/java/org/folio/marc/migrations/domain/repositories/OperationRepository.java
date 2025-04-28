package org.folio.marc.migrations.domain.repositories;

import java.util.UUID;
import org.folio.marc.migrations.domain.entities.Operation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface OperationRepository extends JpaRepository<Operation, UUID>, JpaSpecificationExecutor<Operation> {
}
