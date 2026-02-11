package org.folio.support;

import java.util.UUID;
import lombok.experimental.UtilityClass;
import org.folio.spring.FolioModuleMetadata;
import tools.jackson.databind.json.JsonMapper;

@UtilityClass
public class TestConstants {

  public static final String TENANT_ID = "test_tenant";
  public static final String USER_ID = "38d3a441-c100-5e8d-bd12-71bde492b723";

  public static final JsonMapper MAPPER = JsonMapper.builder().build();
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

  private static final String MARC_MIGRATION_ENDPOINT_PATH = "/marc-migrations";

  public static String marcMigrationEndpoint() {
    return MARC_MIGRATION_ENDPOINT_PATH;
  }

  public static String marcMigrationEndpoint(UUID id) {
    return MARC_MIGRATION_ENDPOINT_PATH + "/" + id;
  }

  public static String retryMarcMigrationEndpoint(UUID id) {
    return MARC_MIGRATION_ENDPOINT_PATH + "/" + id + "/retry";
  }

  public static String retrySaveMarcMigrationEndpoint(UUID id) {
    return MARC_MIGRATION_ENDPOINT_PATH + "/" + id + "/save/retry";
  }
}
