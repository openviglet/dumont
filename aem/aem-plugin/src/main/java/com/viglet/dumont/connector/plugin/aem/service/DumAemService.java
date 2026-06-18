package com.viglet.dumont.connector.plugin.aem.service;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.AEM;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CONTENT_FRAGMENT;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_PAGE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DAM_ASSET;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DATA_MASTER;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.METADATA;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.STATIC_FILE;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.plugin.aem.DumAemAttrProcess;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;

@Service
public class DumAemService {
    private final DumAemAttrProcess dumAemAttrProcess;

    public DumAemService(DumAemAttrProcess dumAemAttrProcess) {
        this.dumAemAttrProcess = dumAemAttrProcess;
    }

    public String getProviderName() {
        return AEM;
    }

    public @NotNull DumAemAttrMap getTargetAttrValueMap(DumAemSession dumAemSession,
            DumAemObject aemObject) {
        // Resolve the content-fragment / static-file data sub-tree onto the object that is actually
        // extracted, so its nested properties (data/master, metadata) become readable. Applied here —
        // the single field-extraction boundary — so both the legacy job pipeline and the structured
        // path (DumAemStructuredSource) get it uniformly and stay at parity.
        configureObjectDataPath(dumAemSession.getModel(),
                dumAemSession.getConfiguration().getContentType(), aemObject);
        DumAemAttrMap dumAemTargetAttrValueMap = dumAemAttrProcess.prepareAttributeDefs(dumAemSession,
                aemObject);
        dumAemTargetAttrValueMap
                .merge(DumAemCommonsUtils.runCustomClassFromContentType(dumAemSession.getModel(),
                        aemObject, dumAemSession.getConfiguration()));
        return dumAemTargetAttrValueMap;
    }

    /**
     * Point the object at the data sub-tree its content type stores attributes under, so the field
     * extractor can read them: a Content Fragment exposes {@code data/master}, a static file exposes
     * {@code metadata}. A {@code cq:Page} (or any other type) is left untouched. No-op when the model
     * or content type is unresolved.
     */
    public void configureObjectDataPath(DumAemModel dumAemModel, String contentType,
            DumAemObject aemObject) {
        if (dumAemModel == null || StringUtils.isBlank(contentType)) {
            return;
        }
        if (isContentFragment(dumAemModel, contentType, aemObject)) {
            aemObject.setDataPath(DATA_MASTER);
        } else if (isStaticFile(dumAemModel, contentType)) {
            aemObject.setDataPath(METADATA);
        }
    }

    public boolean isNotValidType(DumAemModel dumAemModel, DumAemObjectGeneric aemObject,
            String type) {
        return !isPage(type) && !isContentFragment(dumAemModel, type, aemObject)
                && !isStaticFile(dumAemModel, type);
    }

    public boolean isPage(String type) {
        return type.equals(CQ_PAGE);
    }

    public boolean isStaticFile(DumAemModel dumAemModel, String type) {
        return isAsset(dumAemModel, type) && dumAemModel.getSubType().equals(STATIC_FILE);
    }

    public boolean isContentFragment(DumAemModel dumAemModel, String type,
            DumAemObjectGeneric aemObject) {
        return isAsset(dumAemModel, type) && dumAemModel.getSubType().equals(CONTENT_FRAGMENT)
                && aemObject.isContentFragment();
    }

    public boolean isAsset(DumAemModel dumAemModel, String type) {
        return type.equals(DAM_ASSET) && StringUtils.isNotEmpty(dumAemModel.getSubType());
    }
}
