package org.folio.marc.migrations.domain.repositories;

import java.util.UUID;
import org.folio.marc.migrations.domain.entities.OperationChunk;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OperationChunkRepository extends JpaRepository<OperationChunk, UUID> {
}
