package org.folio.marc.migrations.domain.converters;

import org.folio.marc.migrations.domain.dto.EntityType;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
public class EntityNameToEntityTypeConverter implements Converter<String, EntityType> {

  @Override
  public EntityType convert(String source) {
    return EntityType.fromValue(source);
  }
}
