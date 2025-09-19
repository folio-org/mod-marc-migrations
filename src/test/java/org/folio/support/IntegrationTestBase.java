package org.folio.support;

import static org.awaitility.Awaitility.await;
import static org.awaitility.Durations.FIVE_SECONDS;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.spring.integration.XOkapiHeaders.URL;
import static org.folio.support.TestConstants.MAPPER;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.folio.support.TestConstants.USER_ID;
import static org.hamcrest.Matchers.is;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.jdbc.datasource.init.ScriptUtils.EOF_STATEMENT_SEPARATOR;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.log;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.time.Duration;
import lombok.SneakyThrows;
import org.awaitility.core.ThrowingRunnable;
import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.integration.XOkapiHeaders;
import org.folio.spring.testing.extension.EnableMinio;
import org.folio.spring.testing.extension.EnableOkapi;
import org.folio.spring.testing.extension.EnablePostgres;
import org.folio.spring.testing.extension.impl.OkapiConfiguration;
import org.folio.tenant.domain.dto.Parameter;
import org.folio.tenant.domain.dto.TenantAttributes;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.jupiter.api.BeforeAll;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

@EnableOkapi
@EnableMinio
@EnablePostgres
@SpringBootTest
@ActiveProfiles("test")
@AutoConfigureMockMvc
@Import(IntegrationTestBase.TestConfig.class)
@Sql(scripts = {
  "classpath:sql/mod-source-record-storage-init.sql",
  "classpath:sql/mod-entities-links-init.sql",
  "classpath:sql/mod-inventory-storage-init.sql"
  },
     config = @SqlConfig(separator = EOF_STATEMENT_SEPARATOR), executionPhase = Sql.ExecutionPhase.BEFORE_TEST_CLASS)
@Sql(scripts = "classpath:sql/cleanup.sql", executionPhase = Sql.ExecutionPhase.AFTER_TEST_CLASS)
public class IntegrationTestBase {

  protected static @Autowired MockMvc mockMvc;
  protected static DatabaseHelper databaseHelper;
  protected static OkapiConfiguration okapi;

  @NotNull
  protected static ResultMatcher errorParameterValueMatches(Matcher<String> matcher) {
    return jsonPath("parameters.[0].value", matcher);
  }

  @NotNull
  protected static ResultMatcher errorParameterKeyMatches(Matcher<String> matcher) {
    return jsonPath("parameters.[0].key", matcher);
  }

  @NotNull
  protected static ResultMatcher errorTypeMatches(Class<?> exceptionClass) {
    return jsonPath("type", is(exceptionClass.getSimpleName()));
  }

  @NotNull
  protected static ResultMatcher errorMessageMatches(Matcher<String> matcher) {
    return jsonPath("message", matcher);
  }

  @BeforeAll
  static void setUpClass(@Autowired MockMvc mockMvc, @Autowired DatabaseHelper databaseHelper) {
    IntegrationTestBase.mockMvc = mockMvc;
    IntegrationTestBase.databaseHelper = databaseHelper;
  }

  @SneakyThrows
  protected static void setUpTenant() {
    setUpTenant(false);
  }

  @SneakyThrows
  protected static void setUpTenant(boolean loadReference) {
    setUpTenant(TENANT_ID, loadReference);
  }

  @SneakyThrows
  protected static void setUpTenant(String tenantId, boolean loadReference) {
    var httpHeaders = new HttpHeaders();
    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(XOkapiHeaders.TENANT, tenantId);
    httpHeaders.add(XOkapiHeaders.USER_ID, USER_ID);
    httpHeaders.add(XOkapiHeaders.URL, okapi.getOkapiUrl());

    var tenantAttributes = new TenantAttributes().moduleTo("mod-marc-migrations")
        .addParametersItem(new Parameter("loadReference").value(String.valueOf(loadReference)));
    doPost("/_/tenant", tenantAttributes, httpHeaders);
  }

  protected static HttpHeaders defaultHeaders() {
    var httpHeaders = new HttpHeaders();

    httpHeaders.setContentType(APPLICATION_JSON);
    httpHeaders.add(TENANT, TENANT_ID);
    httpHeaders.add(XOkapiHeaders.USER_ID, USER_ID);
    httpHeaders.add(URL, okapi.getOkapiUrl());

    return httpHeaders;
  }

  @SneakyThrows
  protected static ResultActions tryDelete(String uri, Object... args) {
    return tryDelete(uri, defaultHeaders(), args);
  }

  @SneakyThrows
  protected static ResultActions tryDelete(String uri, HttpHeaders headers, Object... args) {
    return tryDoHttpMethod(delete(uri, args), null, headers);
  }

  @SneakyThrows
  protected static ResultActions doDelete(String uri, HttpHeaders headers, Object... args) {
    return tryDelete(uri, headers, args).andExpect(status().is2xxSuccessful());
  }

  @SneakyThrows
  protected static ResultActions doDelete(String uri, Object... args) {
    return doDelete(uri, defaultHeaders(), args);
  }

  @SneakyThrows
  protected static ResultActions tryGet(String uri, Object... args) {
    return tryGet(uri, defaultHeaders(), args);
  }

  @SneakyThrows
  protected static ResultActions tryGet(String uri, HttpHeaders headers, Object... args) {
    return tryDoHttpMethod(get(uri, args), null, headers);
  }

  @SneakyThrows
  protected static ResultActions doGet(String uri, Object... args) {
    return doGet(uri, defaultHeaders(), args);
  }

  @SneakyThrows
  protected static ResultActions doGet(String uri, HttpHeaders headers, Object... args) {
    return tryGet(uri, headers, args).andExpect(status().isOk());
  }

  protected static void doGetUntilMatches(String uri, ResultMatcher matcher, Object... args) {
    awaitUntilAsserted(() -> doGet(uri, args).andExpect(matcher));
  }

  @SneakyThrows
  protected static ResultActions tryPut(String uri, Object body, HttpHeaders headers, Object... args) {
    return tryDoHttpMethod(put(uri, args), body, headers);
  }

  @SneakyThrows
  protected static ResultActions tryPut(String uri, Object body, Object... args) {
    return tryPut(uri, body, defaultHeaders(), args);
  }

  @SneakyThrows
  protected static ResultActions doPut(String uri, Object body, HttpHeaders headers, Object... args) {
    return tryPut(uri, body, headers, args).andExpect(status().is2xxSuccessful());
  }

  @SneakyThrows
  protected static ResultActions doPut(String uri, Object body, Object... args) {
    return doPut(uri, body, defaultHeaders(), args);
  }

  @SneakyThrows
  protected static ResultActions tryPatch(String uri, Object body, Object... args) {
    return tryDoHttpMethod(patch(uri, args), body);
  }

  @SneakyThrows
  protected static ResultActions doPatch(String uri, Object body, Object... args) {
    return tryPatch(uri, body, args).andExpect(status().is2xxSuccessful());
  }

  @SneakyThrows
  protected static ResultActions tryPost(String uri, Object body, Object... args) {
    return tryPost(uri, body, defaultHeaders(), args);
  }

  @SneakyThrows
  protected static ResultActions tryPost(String uri, Object body, HttpHeaders headers, Object... args) {
    return tryDoHttpMethod(post(uri, args), body, headers);
  }

  @SneakyThrows
  protected static ResultActions doPost(String uri, Object body, Object... args) {
    return doPost(uri, body, defaultHeaders(), args);
  }

  @SneakyThrows
  protected static ResultActions doPost(String uri, Object body, HttpHeaders headers, Object... args) {
    return tryPost(uri, body, headers, args).andExpect(status().is2xxSuccessful());
  }

  @NotNull
  private static ResultActions tryDoHttpMethod(MockHttpServletRequestBuilder builder, Object body,
                                               HttpHeaders headers) throws Exception {
    return mockMvc.perform(builder
            .content(body == null ? "" : asJson(body))
            .headers(headers))
        .andDo(log());
  }

  @NotNull
  private static ResultActions tryDoHttpMethod(MockHttpServletRequestBuilder builder, Object body) throws Exception {
    return tryDoHttpMethod(builder, body, defaultHeaders());
  }

  @SneakyThrows
  protected static String asJson(Object value) {
    return MAPPER.writeValueAsString(value);
  }

  @SneakyThrows
  protected static <T> T contentAsObj(MvcResult result, Class<T> objectClass) {
    var contentAsBytes = result.getResponse().getContentAsByteArray();
    MAPPER.registerModule(new JavaTimeModule());
    return MAPPER.readValue(contentAsBytes, objectClass);
  }

  protected static void awaitUntilAsserted(ThrowingRunnable throwingRunnable) {
    await().pollInterval(Duration.ofMillis(100)).atMost(FIVE_SECONDS).untilAsserted(throwingRunnable);
  }

  @TestConfiguration
  public static class TestConfig {
    @Bean
    public DatabaseHelper databaseHelper(JdbcTemplate jdbcTemplate, FolioModuleMetadata metadata) {
      return new DatabaseHelper(metadata, jdbcTemplate);
    }
  }

}
