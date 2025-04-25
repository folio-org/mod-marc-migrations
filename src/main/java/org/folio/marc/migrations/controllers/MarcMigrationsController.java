package org.folio.marc.migrations.controllers;

import java.util.UUID;
import org.folio.marc.migrations.controllers.delegates.MarcMigrationsService;
import org.folio.marc.migrations.domain.dto.EntityType;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.MigrationOperationCollection;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.rest.resource.MarcMigrationsApi;
import org.folio.marc.migrations.services.ExpirationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class MarcMigrationsController implements MarcMigrationsApi {

  private final MarcMigrationsService migrationsService;
  private final ExpirationService expirationService;

  public MarcMigrationsController(MarcMigrationsService migrationsService, ExpirationService expirationService) {
    this.migrationsService = migrationsService;
    this.expirationService = expirationService;
  }

  @Override
  public ResponseEntity<MigrationOperation> createMarcMigrations(String tenantId,
                                                                 NewMigrationOperation newMigrationOperation) {
    var operation = migrationsService.createNewMigration(newMigrationOperation);
    return ResponseEntity.status(HttpStatus.CREATED).body(operation);
  }

  @Override
  public ResponseEntity<MigrationOperationCollection> getMarcMigrations(String tenantId, Integer offset, Integer limit,
                                                                        EntityType entityType) {
    return ResponseEntity.ok(migrationsService.getMarcMigrations(offset, limit, entityType));
  }

  @Override
  public ResponseEntity<MigrationOperation> getMarcMigrationById(UUID operationId, String tenantId) {
    return ResponseEntity.ok(migrationsService.getMarcMigrationById(operationId));
  }

  @Override
  public ResponseEntity<Void> saveMarcMigration(UUID operationId, String tenantId,
                                                SaveMigrationOperation saveMigrationOperation) {
    migrationsService.saveMigrationOperation(operationId, saveMigrationOperation);
    return ResponseEntity.noContent().build();
  }

  /**
   * POST /marc-migrations/expire.
   *
   * @return Successfully expired marc migration jobs (status code 202)
   *         or Internal server error. (status code 500)
   */
  @PostMapping(
    value = "/marc-migrations/expire",
    produces = { "application/json" }
  )
  public ResponseEntity<Void> expireMigrationJobs() {
    expirationService.deleteExpiredData();
    return ResponseEntity.status(HttpStatus.ACCEPTED).build();
  }
}
