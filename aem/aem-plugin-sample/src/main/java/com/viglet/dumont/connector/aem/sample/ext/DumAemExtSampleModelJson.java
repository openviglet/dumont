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

package com.viglet.dumont.connector.aem.sample.ext;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemTargetAttrValueMap;
import com.viglet.dumont.connector.aem.commons.context.DumAemConfiguration;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtContentInterface;
import com.viglet.dumont.connector.aem.commons.utils.DumAemCommonsUtils;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelElement;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelItem;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DumAemExtSampleModelJson implements DumAemExtContentInterface {
    public static final String FRAGMENT_PATH = "fragmentPath";
    public static final String PAGE_TITLE = "pageTitle";
    public static final String PAGE_DESCRIPTION = "pageDescription";
    public static final String PAGE_LANGUAGE = "pageLanguage";
    public static final String PAGE_TEMPLATE = "pageTemplate";
    public static final String PAGE_LAST_MODIFIED = "pageLastModified";
    public static final String PARAGRAPHS = "paragraphs";
    public static final String TEXTS = "texts";
    public static final String IMAGES = "pageImages";
    public static final String CONTENT_FRAGMENT_TITLES = "contentFragmentTitles";
    public static final String CONTENT_FRAGMENT_MODELS = "contentFragmentModels";
    public static final String CONTENT_FRAGMENT_ELEMENTS = "contentFragmentElements";
    public static final String MODEL_JSON_EXTENSION = ".model.json";

    @Override
    public DumAemTargetAttrValueMap consume(DumAemObject aemObject,
            DumAemConfiguration dumAemSourceContext) {
        log.debug("Executing DumAemExtSampleModelJson");
        String url = dumAemSourceContext.getUrl() + aemObject.getPath() + MODEL_JSON_EXTENSION;

        try {
            return DumAemCommonsUtils
                    .getResponseBody(url, dumAemSourceContext, DumAemSampleModel.class, false)
                    .map(model -> {
                        DumAemTargetAttrValueMap attrValues = new DumAemTargetAttrValueMap();
                        getFragmentData(attrValues, model);
                        getPageMetadata(attrValues, model);
                        getContentFromItems(attrValues, model);
                        return attrValues;
                    })
                    .orElseGet(DumAemTargetAttrValueMap::new);
        } catch (IOException e) {
            log.error("Error consuming AEM model JSON from: {}", url, e);
            return new DumAemTargetAttrValueMap();
        }
    }

    private static void getFragmentData(DumAemTargetAttrValueMap attrValues,
            DumAemSampleModel model) {
        attrValues.addWithSingleValue(FRAGMENT_PATH, model.getFragmentPath(), true);
    }

    private static void getPageMetadata(DumAemTargetAttrValueMap attrValues,
            DumAemSampleModel model) {
        attrValues.addWithSingleValue(PAGE_TITLE, model.getTitle(), true);
        attrValues.addWithSingleValue(PAGE_DESCRIPTION, model.getDescription(), true);
        attrValues.addWithSingleValue(PAGE_LANGUAGE, model.getLanguage(), true);
        attrValues.addWithSingleValue(PAGE_TEMPLATE, model.getTemplateName(), true);
        if (model.getLastModifiedDate() != null) {
            attrValues.addWithSingleValue(PAGE_LAST_MODIFIED,
                    new Date(model.getLastModifiedDate()), true);
        }
    }

    private static void getContentFromItems(DumAemTargetAttrValueMap attrValues,
            DumAemSampleModel model) {
        if (model.getItems() == null) {
            return;
        }
        List<String> paragraphs = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        List<String> images = new ArrayList<>();
        List<String> cfTitles = new ArrayList<>();
        List<String> cfModels = new ArrayList<>();
        List<String> cfElements = new ArrayList<>();

        collectContentRecursively(model.getItems(), paragraphs, texts, images,
                cfTitles, cfModels, cfElements);

        attrValues.addWithStringCollectionValue(PARAGRAPHS, paragraphs, true);
        attrValues.addWithStringCollectionValue(TEXTS, texts, true);
        attrValues.addWithStringCollectionValue(IMAGES, images, true);
        attrValues.addWithStringCollectionValue(CONTENT_FRAGMENT_TITLES, cfTitles, true);
        attrValues.addWithStringCollectionValue(CONTENT_FRAGMENT_MODELS, cfModels, true);
        attrValues.addWithStringCollectionValue(CONTENT_FRAGMENT_ELEMENTS, cfElements, true);
    }

    private static void collectContentRecursively(Map<String, DumAemSampleModelItem> items,
            List<String> paragraphs, List<String> texts, List<String> images,
            List<String> cfTitles, List<String> cfModels, List<String> cfElements) {
        for (DumAemSampleModelItem item : items.values()) {
            extractContentFromItem(item, paragraphs, texts, images,
                    cfTitles, cfModels, cfElements);
            if (item.getItems() != null) {
                collectContentRecursively(item.getItems(), paragraphs, texts, images,
                        cfTitles, cfModels, cfElements);
            }
        }
    }

    private static void extractContentFromItem(DumAemSampleModelItem item,
            List<String> paragraphs, List<String> texts, List<String> images,
            List<String> cfTitles, List<String> cfModels, List<String> cfElements) {
        if (item.getParagraphs() != null) {
            for (String paragraph : item.getParagraphs()) {
                String cleaned = stripHtml(paragraph);
                if (!cleaned.isBlank()) {
                    paragraphs.add(cleaned);
                }
            }
        }
        if (item.getText() != null && !item.getText().isBlank()) {
            texts.add(stripHtml(item.getText()));
        }
        if (item.getSrc() != null && !item.getSrc().isBlank()) {
            images.add(item.getSrc());
        }
        String type = item.getType();
        if (type != null && type.contains("contentfragment")) {
            if (item.getTitle() != null && !item.getTitle().isBlank()) {
                cfTitles.add(item.getTitle());
            }
            if (item.getModel() != null && !item.getModel().isBlank()) {
                cfModels.add(item.getModel());
            }
            extractElements(item, cfElements);
        }
    }

    private static void extractElements(DumAemSampleModelItem item,
            List<String> cfElements) {
        if (item.getElements() == null) {
            return;
        }
        for (DumAemSampleModelElement element : item.getElements().values()) {
            if (element.getValue() != null && !element.getValue().isBlank()) {
                cfElements.add(stripHtml(element.getValue()));
            }
        }
    }

    private static String stripHtml(String html) {
        if (html == null) {
            return "";
        }
        return html.replaceAll("<[^>]+>", "")
                .replaceAll("&[a-zA-Z]+;", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
