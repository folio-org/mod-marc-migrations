package org.folio.marc.migrations;

import static org.folio.support.TestConstants.TENANT_ID;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import lombok.SneakyThrows;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.test.extension.EnablePostgres;
import org.folio.spring.test.type.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

@IntegrationTest
@SpringBootTest
@EnablePostgres
@AutoConfigureMockMvc
class MarcMigrationsApplicationTest {

  @Autowired
  private MarcMigrationsApplication marcMigrationsApplication;
  @Autowired
  private MockMvc mockMvc;

  @Test
  void contextLoads() {
    assertNotNull(marcMigrationsApplication);
  }

  @Test
  @SneakyThrows
  void healthEndpointWorks() {
    mockMvc.perform(MockMvcRequestBuilders.get("/admin/health"))
        .andExpect(status().is2xxSuccessful())
        .andExpect(jsonPath("$.status", equalTo("UP")));
  }

  @Test
  @SneakyThrows
  void postTenantEndpointWorks() {
    mockMvc.perform(MockMvcRequestBuilders.post("/_/tenant")
            .contentType(APPLICATION_JSON)
            .header(XOkapiHeaders.TENANT, TENANT_ID)
            .content("{\"module_to\": \"mod-1.0.0\"}"))
        .andExpect(status().is2xxSuccessful());
  }

}
