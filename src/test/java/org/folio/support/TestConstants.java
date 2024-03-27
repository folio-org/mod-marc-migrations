package org.folio.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.experimental.UtilityClass;
import org.folio.spring.FolioModuleMetadata;

@UtilityClass
public class TestConstants {

  public static final String TENANT_ID = "test_tenant";
  public static final String USER_ID = "38d3a441-c100-5e8d-bd12-71bde492b723";

  public static final ObjectMapper MAPPER = new ObjectMapper();
  public static final FolioModuleMetadata METADATA = new FolioModuleMetadata() {
    @Override
    public String getModuleName() {
      return "mod-marc-migrations";
    }

    @Override
    public String getDBSchemaName(String tenantId) {
      return tenantId;
    }
  };
}
