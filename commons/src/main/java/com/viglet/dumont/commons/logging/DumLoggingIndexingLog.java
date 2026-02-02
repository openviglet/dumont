package com.viglet.dumont.commons.logging;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumLoggingIndexingLog {

   // Private constructor to prevent instantiation
   private DumLoggingIndexingLog() {
      throw new UnsupportedOperationException("Indexing Log class");
   }

   public static void setStatus(DumLoggingIndexing status) {
      log.info("{}", status);

   }
}
