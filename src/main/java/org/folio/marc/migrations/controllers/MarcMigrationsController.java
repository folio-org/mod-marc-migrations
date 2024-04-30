package org.folio.marc.migrations.controllers;

import java.util.UUID;
import org.folio.marc.migrations.controllers.delegates.MarcMigrationsService;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.dto.SaveMigrationOperation;
import org.folio.marc.migrations.rest.resource.MarcMigrationsApi;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
public class MarcMigrationsController implements MarcMigrationsApi {

  private final MarcMigrationsService migrationsService;

  public MarcMigrationsController(MarcMigrationsService migrationsService) {
    this.migrationsService = migrationsService;
  }

  @Override
  public ResponseEntity<MigrationOperation> createMarcMigrations(String tenantId,
                                                                 NewMigrationOperation newMigrationOperation) {
    var operation = migrationsService.createNewMigration(newMigrationOperation);
    return ResponseEntity.status(HttpStatus.CREATED).body(operation);
  }

  @Override
  public ResponseEntity<MigrationOperation> getMarcMigrationById(UUID operationId, String tenantId) {
    return ResponseEntity.ok(migrationsService.getMarcMigrationById(operationId));
  }

  @Override
  public ResponseEntity<Void> saveMarcMigration(UUID operationId, SaveMigrationOperation saveMigrationOperation) {
    migrationsService.saveMigrationOperation(operationId, saveMigrationOperation);
    return ResponseEntity.noContent().build();
  }
}
