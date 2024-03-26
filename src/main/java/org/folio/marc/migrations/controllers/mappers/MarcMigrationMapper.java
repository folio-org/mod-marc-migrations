package org.folio.marc.migrations.controllers.mappers;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import org.folio.marc.migrations.domain.dto.MigrationOperation;
import org.folio.marc.migrations.domain.dto.NewMigrationOperation;
import org.folio.marc.migrations.domain.entities.Operation;
import org.folio.marc.migrations.utils.DateUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

@Mapper(
    unmappedTargetPolicy = ReportingPolicy.IGNORE,
    componentModel = MappingConstants.ComponentModel.SPRING
)
public interface MarcMigrationMapper {

  @Mapping(target = "id", ignore = true)
  @Mapping(target = "userId", ignore = true)
  @Mapping(target = "totalNumOfRecords", ignore = true)
  @Mapping(target = "startTimeSaving", ignore = true)
  @Mapping(target = "startTimeMapping", ignore = true)
  @Mapping(target = "mappedNumOfRecords", ignore = true)
  @Mapping(target = "savedNumOfRecords", ignore = true)
  @Mapping(target = "status", ignore = true)
  @Mapping(target = "endTimeSaving", ignore = true)
  @Mapping(target = "endTimeMapping", ignore = true)
  Operation toEntity(NewMigrationOperation migrationOperation);

  MigrationOperation toDto(Operation operation);

  default OffsetDateTime map(Timestamp value) {
    return DateUtils.fromTimestamp(value);
  }
}
