package org.folio.marc.migrations;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.folio.spring.test.extension.EnablePostgres;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@IntegrationTest
@SpringBootTest
@EnablePostgres
class MarcMigrationsApplicationTest {

  @Autowired
  private MarcMigrationsApplication marcMigrationsApplication;

  @Test
  void contextLoads() {
    assertNotNull(marcMigrationsApplication);
  }
}
