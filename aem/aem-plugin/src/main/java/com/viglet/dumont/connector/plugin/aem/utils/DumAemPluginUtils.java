package com.viglet.dumont.connector.plugin.aem.utils;

import static com.viglet.dumont.connector.aem.commons.bean.DumAemEnv.PUBLISHING;

import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.commons.DumConnectorSession;
import com.viglet.dumont.connector.commons.domain.DumConnectorIndexing;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemPluginUtils {
        private DumAemPluginUtils() {
                throw new IllegalStateException("Plugin AEM Utility class");
        }

        public static String getObjectDetailForLogs(DumAemSession dumAemSession,
                        DumAemObjectGeneric aemObject) {
                return "%s object (%s - %s - %s: %s)".formatted(aemObject.getPath(),
                                dumAemSession.getConfiguration().getId(), PUBLISHING,
                                DumAemCommonsUtils
                                                .getLocaleFromAemObject(
                                                                dumAemSession.getConfiguration(),
                                                                aemObject),
                                dumAemSession.getTransactionId());
        }

        public static String getObjectDetailForLogs(String contentId, DumConnectorIndexing indexing,
                        DumConnectorSession session) {
                return "%s object (%s - %s - %s: %s)".formatted(contentId, session.getSource(),
                                indexing.getEnvironment(), indexing.getLocale(),
                                session.getTransactionId());
        }
}
