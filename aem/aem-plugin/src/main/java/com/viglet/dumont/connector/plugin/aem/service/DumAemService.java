package com.viglet.dumont.connector.plugin.aem.service;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.AEM;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CONTENT_FRAGMENT;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_PAGE;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.DAM_ASSET;
import static com.viglet.dumont.connector.aem.commons.DumAemConstants.STATIC_FILE;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Service;

import com.viglet.dumont.connector.aem.commons.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.DumAemObjectGeneric;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemModel;
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

    public @NotNull DumAemTargetAttrValueMap getTargetAttrValueMap(DumAemSession dumAemSession,
            DumAemObject aemObject) {
        DumAemTargetAttrValueMap dumAemTargetAttrValueMap = dumAemAttrProcess.prepareAttributeDefs(dumAemSession,
                aemObject);
        dumAemTargetAttrValueMap
                .merge(DumAemCommonsUtils.runCustomClassFromContentType(dumAemSession.getModel(),
                        aemObject, dumAemSession.getConfiguration()));
        return dumAemTargetAttrValueMap;
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
