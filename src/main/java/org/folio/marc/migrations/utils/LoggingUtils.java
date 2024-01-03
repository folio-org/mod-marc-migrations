package org.folio.marc.migrations.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.log4j.Log4j2;

@Log4j2
@UtilityClass
public class LoggingUtils {

  public static void throwExceptionAndLog(RuntimeException e) {
    log.throwing(e);
    throw e;
  }
}
