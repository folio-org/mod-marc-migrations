package org.folio.marc.migrations.services.batch.support;

import static java.util.Collections.singletonList;
import static java.util.Objects.requireNonNull;
import static java.util.Optional.ofNullable;
import static org.assertj.core.api.Assertions.assertThat;
import static org.folio.marc.migrations.domain.entities.types.EntityType.AUTHORITY;
import static org.folio.spring.integration.XOkapiHeaders.TENANT;
import static org.folio.support.TestConstants.MAPPER;
import static org.folio.support.TestConstants.TENANT_ID;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.NONE;

import io.vertx.core.json.JsonObject;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import lombok.SneakyThrows;
import org.folio.IdentifierType;
import org.folio.marc.migrations.client.MappingMetadataClient;
import org.folio.processing.mapping.defaultmapper.processor.parameters.MappingParameters;
import org.folio.spring.DefaultFolioExecutionContext;
import org.folio.spring.FolioExecutionContext;
import org.folio.spring.exception.NotFoundException;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

@UnitTest
@Import(MappingMetadataProviderTest.TestContextConfiguration.class)
@SpringBootTest(classes = MappingMetadataProvider.class, webEnvironment = NONE)
class MappingMetadataProviderTest {


  private @Autowired CacheManager cacheManager;
  private @Autowired MappingMetadataProvider provider;
  private @MockBean MappingMetadataClient client;

  @BeforeEach
  void cleanUpCaches() {
    cacheManager.getCacheNames().forEach(name -> requireNonNull(cacheManager.getCache(name)).clear());
  }

  @Test
  @SneakyThrows
  void getMappingData_positive() {
    var mappingRules = new JsonObject("{\"test\": \"test\"}");
    var mappingParams = new MappingParameters().withIdentifierTypes(List.of(new IdentifierType().withId("test")));
    when(client.getMappingMetadata(AUTHORITY.getMappingMetadataRecordType())).thenReturn(
      new MappingMetadataClient.MappingMetadata(mappingRules.encode(), MAPPER.writeValueAsString(mappingParams)));

    var metadata = provider.getMappingData(AUTHORITY);

    assertThat(metadata.mappingRules()).isEqualTo(mappingRules);
    assertThat(metadata.mappingParameters().getIdentifierTypes()).isEqualTo(mappingParams.getIdentifierTypes());
    var cachedValue = getCachedValue();
    assertThat(cachedValue).isPresent().get().matches(cached ->
      cached.mappingRules().equals(mappingRules)
        && cached.mappingParameters().getIdentifierTypes().equals(mappingParams.getIdentifierTypes()));
  }

  @Test
  void getMappingData_negative_serverError() {
    when(client.getMappingMetadata(AUTHORITY.getMappingMetadataRecordType())).thenThrow(NotFoundException.class);
    var metadata = provider.getMappingData(AUTHORITY);
    assertThat(metadata).isNull();
    var cachedValue = getCachedValue();
    assertThat(cachedValue).isEmpty();
  }

  @Test
  void getMappingData_negative_nullServerResponse() {
    when(client.getMappingMetadata(AUTHORITY.getMappingMetadataRecordType())).thenReturn(null);
    var metadata = provider.getMappingData(AUTHORITY);
    assertThat(metadata).isNull();
    var cachedValue = getCachedValue();
    assertThat(cachedValue).isEmpty();
  }

  @ParameterizedTest
  @MethodSource("emptyMappingMetadataArguments")
  void getMappingData_negative_emptyServerResponse(String mappingRules, String mappingParams) {
    var mappingMetadata = new MappingMetadataClient.MappingMetadata(mappingRules, mappingParams);
    when(client.getMappingMetadata(AUTHORITY.getMappingMetadataRecordType())).thenReturn(mappingMetadata);
    var metadata = provider.getMappingData(AUTHORITY);
    assertThat(metadata).isNull();
    var cachedValue = getCachedValue();
    assertThat(cachedValue).isEmpty();
  }

  static Stream<Arguments> emptyMappingMetadataArguments() {
    return Stream.of(
      arguments("", ""),
      arguments("", " "),
      arguments("", "a"),
      arguments("", null),
      arguments(" ", " "),
      arguments(" ", ""),
      arguments(" ", "a"),
      arguments(" ", null),
      arguments("a", ""),
      arguments("a", " "),
      arguments("a", null),
      arguments(null, null),
      arguments(null, " "),
      arguments(null, ""),
      arguments(null, "a"));
  }

  private Optional<MappingMetadataProvider.MappingData> getCachedValue() {
    return ofNullable(cacheManager.getCache("mapping-metadata-cache"))
      .map(cache -> cache.get(TENANT_ID + AUTHORITY))
      .map(Cache.ValueWrapper::get)
      .map(cached -> (MappingMetadataProvider.MappingData) cached);
  }

  @EnableCaching
  @TestConfiguration
  static class TestContextConfiguration {

    @Bean
    CacheManager cacheManager() {
      return new ConcurrentMapCacheManager("mapping-metadata-cache");
    }

    @Bean
    FolioExecutionContext folioExecutionContext() {
      return new DefaultFolioExecutionContext(null, Map.of(TENANT, singletonList(TENANT_ID)));
    }
  }
}
