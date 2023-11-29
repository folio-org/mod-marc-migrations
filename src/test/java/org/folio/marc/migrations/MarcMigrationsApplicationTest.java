package org.folio.marc.migrations;

import static org.junit.jupiter.api.Assertions.*;

import org.folio.spring.test.extension.EnablePostgres;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

@IntegrationTest
@SpringBootTest
@EnablePostgres
class MarcMigrationsApplicationTest {

  @Autowired
  private ApplicationContext applicationContext;

  @Test
  void contextLoads() {
    assertNotNull(applicationContext);
  }
}
