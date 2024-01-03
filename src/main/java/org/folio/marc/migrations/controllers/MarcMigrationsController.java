package org.folio.marc.migrations.controllers;

import org.folio.entlinks.domain.dto.MigrationOperation;
import org.folio.entlinks.domain.dto.NewMigrationOperation;
import org.folio.entlinks.rest.resource.MarcMigrationsApi;
import org.folio.marc.migrations.controllers.delegates.MarcMigrationsService;
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
    MigrationOperation operation = migrationsService.createNewMigration(newMigrationOperation);
    return ResponseEntity.status(HttpStatus.CREATED).body(operation);
  }
}
