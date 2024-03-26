package org.folio.marc.migrations.services.domain;

import java.util.List;

public record MappingComposite<T>(RecordsMappingData mappingData, List<T> records) {
}
