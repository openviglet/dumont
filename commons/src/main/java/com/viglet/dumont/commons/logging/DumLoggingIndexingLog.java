package com.viglet.dumont.commons.logging;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumLoggingIndexingLog {

   public static void setStatus(DumLoggingIndexing status) {
      log.info("{}", status);

   }
}
