package org.folio.marc.migrations.services;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.folio.spring.FolioModuleMetadata;
import org.folio.spring.testing.type.UnitTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@UnitTest
@ExtendWith(MockitoExtension.class)
class TenantContextRunnerTest {

  @Mock
  private FolioModuleMetadata moduleMetadata;

  private TenantContextRunner tenantContextRunner;

  @BeforeEach
  void setUp() {
    tenantContextRunner = new TenantContextRunner(moduleMetadata);
  }

  @Test
  void shouldRunInTenantContext() {
    // given
    String tenantId = "test_tenant";
    Runnable mockRunnable = mock(Runnable.class);

    // when
    tenantContextRunner.runInContext(tenantId, mockRunnable);

    // then
    verify(mockRunnable, times(1)).run();
  }

  @Test
  void shouldRunInTenantContextWithNullTenant() {
    // given
    Runnable mockRunnable = mock(Runnable.class);

    // when
    tenantContextRunner.runInContext(null, mockRunnable);

    // then
    verify(mockRunnable, times(1)).run();
  }

  @Test
  void shouldRunInTenantContextWithEmptyTenant() {
    // given
    Runnable mockRunnable = mock(Runnable.class);

    // when
    tenantContextRunner.runInContext("", mockRunnable);

    // then
    verify(mockRunnable, times(1)).run();
  }
}
