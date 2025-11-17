/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the
 * GNU General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without
 * even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this program. If
 * not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.plugin.aem;

import static com.viglet.dumont.connector.aem.commons.DumAemConstants.CQ_TAGS;

import java.util.List;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

import com.viglet.dumont.commons.cache.DumCustomClassCache;
import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemContext;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtAttributeInterface;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemSourceAttr;
import com.viglet.dumont.connector.aem.commons.mappers.DumAemTargetAttr;
import com.viglet.dumont.connector.plugin.aem.context.DumAemSession;
import com.viglet.turing.client.sn.job.TurSNAttributeSpec;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class DumAemAttrProcess {
        public static final String CQ_TAGS_PATH = "/content/_cq_tags";

        public DumAemTargetAttrValueMap prepareAttributeDefs(DumAemSession dumAemSession,
                        DumAemObject aemObject) {

                DumAemContext context = new DumAemContext(aemObject);
                DumAemTargetAttrValueMap dumAemTargetAttrValueMap = new DumAemTargetAttrValueMap();
                dumAemSession.getModel().getTargetAttrs().stream().filter(Objects::nonNull)
                                .forEach(targetAttr -> {
                                        log.debug("TargetAttr: {}", targetAttr);
                                        context.setDumAemTargetAttr(targetAttr);
                                        if (DumAemAttrUtils.hasCustomClass(targetAttr)) {
                                                dumAemTargetAttrValueMap.merge(process(
                                                                dumAemSession, context));
                                        } else {
                                                targetAttr.getSourceAttrs().stream()
                                                                .filter(Objects::nonNull)
                                                                .forEach(sourceAttr -> dumAemTargetAttrValueMap
                                                                                .merge(addTargetAttrValuesBySourceAttr(
                                                                                                dumAemSession,
                                                                                                targetAttr,
                                                                                                sourceAttr,
                                                                                                context)));
                                        }
                                });
                return dumAemTargetAttrValueMap;

        }

        public DumAemTargetAttrValueMap addTargetAttrValuesBySourceAttr(DumAemSession dumAemSession,
                        DumAemTargetAttr targetAttr, DumAemSourceAttr sourceAttr,
                        DumAemContext context) {

                context.setDumAemSourceAttr(sourceAttr);
                DumAemTargetAttrValueMap targetAttrValues = process(dumAemSession, context);
                return sourceAttr.isUniqueValues()
                                ? DumAemAttrUtils.getDumAttrDefUnique(targetAttr, targetAttrValues)
                                : targetAttrValues;
        }

        public DumAemTargetAttrValueMap process(DumAemSession dumAemSession,
                        DumAemContext context) {
                log.debug("Target Attribute Name: {} and Source Attribute Name: {}",
                                context.getDumAemTargetAttr().getName(),
                                context.getDumAemSourceAttr().getName());
                return DumAemAttrUtils.hasTextValue(context.getDumAemTargetAttr())
                                ? DumAemAttrUtils.getTextValue(context)
                                : getCustomClassValue(context, dumAemSession.getAttributeSpecs(),
                                                dumAemSession.getConfiguration());
        }

        private @NotNull DumAemTargetAttrValueMap getCustomClassValue(DumAemContext context,
                        List<TurSNAttributeSpec> dumSNAttributeSpecList,
                        DumAemConfiguration dumAemSourceContext) {
                DumAemTargetAttrValueMap dumAemTargetAttrValueMap = DumAemAttrUtils.hasCustomClass(context)
                                ? attributeByClass(context, dumAemSourceContext)
                                : attributeByCMS(context);
                dumAemTargetAttrValueMap
                                .merge(generateNewAttributesFromCqTags(context, dumAemSourceContext,
                                                dumSNAttributeSpecList, dumAemTargetAttrValueMap));
                return dumAemTargetAttrValueMap;
        }

        private DumAemTargetAttrValueMap attributeByCMS(DumAemContext context) {
                final Object jcrProperty = DumAemAttrUtils.getJcrProperty(context,
                                context.getDumAemSourceAttr().getName());
                return DumAemAttrUtils.hasJcrPropertyValue(jcrProperty)
                                ? DumAemAttrUtils.addValuesToAttributes(
                                                context.getDumAemTargetAttr(),
                                                context.getDumAemSourceAttr(), jcrProperty)
                                : new DumAemTargetAttrValueMap();
        }

        private DumAemTargetAttrValueMap generateNewAttributesFromCqTags(DumAemContext context,
                        DumAemConfiguration dumAemSourceContext,
                        List<TurSNAttributeSpec> dumSNAttributeSpecList,
                        DumAemTargetAttrValueMap dumAemTargetAttrValueMapFromClass) {
                DumAemTargetAttrValueMap dumAemTargetAttrValueMap = new DumAemTargetAttrValueMap();
                String attributeName = context.getDumAemSourceAttr().getName();
                if (CQ_TAGS.equals(attributeName)) {
                        String targetName = context.getDumAemTargetAttr().getName();
                        if (dumAemTargetAttrValueMapFromClass.containsKey(targetName)) {
                                DumAemAttrUtils.processTagsFromTargetAttr(context,
                                                dumAemSourceContext, dumSNAttributeSpecList,
                                                dumAemTargetAttrValueMapFromClass, targetName,
                                                dumAemTargetAttrValueMap);
                        } else {
                                DumAemAttrUtils.processTagsFromSourceAttr(context,
                                                dumAemSourceContext, dumSNAttributeSpecList,
                                                attributeName, dumAemTargetAttrValueMap);
                        }
                }
                return dumAemTargetAttrValueMap;
        }

        private DumAemTargetAttrValueMap attributeByClass(DumAemContext context,
                        DumAemConfiguration dumAemSourceContext) {
                String className = context.getDumAemSourceAttr().getClassName();
                log.debug("ClassName : {}", className);
                return DumCustomClassCache.getCustomClassMap(className)
                                .map(classInstance -> DumAemTargetAttrValueMap.singleItem(
                                                context.getDumAemTargetAttr().getName(),
                                                ((DumAemExtAttributeInterface) classInstance)
                                                                .consume(context.getDumAemTargetAttr(),
                                                                                context.getDumAemSourceAttr(),
                                                                                context.getCmsObjectInstance(),
                                                                                dumAemSourceContext),
                                                false))
                                .orElseGet(DumAemTargetAttrValueMap::new);

        }
}
