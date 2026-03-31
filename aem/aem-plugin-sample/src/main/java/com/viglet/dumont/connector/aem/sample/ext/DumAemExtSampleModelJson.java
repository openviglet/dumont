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

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.bean.DumAemAttrMap;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtModelJsonBase;
import com.viglet.dumont.connector.aem.commons.ext.DumAemModelJsonQuery;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelElement;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel.DumAemSampleModelItem;

public class DumAemExtSampleModelJson extends DumAemExtModelJsonBase<DumAemSampleModel> {
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

    @Override
    protected Class<DumAemSampleModel> getModelClass() {
        return DumAemSampleModel.class;
    }

    @Override
    protected void extractAttributes(DumAemSampleModel model, DumAemModelJsonQuery query,
            DumAemObject aemObject, DumAemAttrMap attrValues) {
        attrValues.set(FRAGMENT_PATH, model.getFragmentPath())
                .set(PAGE_TITLE, model.getTitle())
                .set(PAGE_DESCRIPTION, model.getDescription())
                .set(PAGE_LANGUAGE, model.getLanguage())
                .set(PAGE_TEMPLATE, model.getTemplateName());

        if (model.getLastModifiedDate() != null) {
            attrValues.set(PAGE_LAST_MODIFIED, new Date(model.getLastModifiedDate()));
        }

        getContentFromItems(attrValues, model);
    }

    private static void getContentFromItems(DumAemAttrMap attrValues,
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

        attrValues.setAll(PARAGRAPHS, paragraphs)
                .setAll(TEXTS, texts)
                .setAll(IMAGES, images)
                .setAll(CONTENT_FRAGMENT_TITLES, cfTitles)
                .setAll(CONTENT_FRAGMENT_MODELS, cfModels)
                .setAll(CONTENT_FRAGMENT_ELEMENTS, cfElements);
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
        extractParagraphs(item, paragraphs);
        extractText(item, texts);
        extractImages(item, images);
        extractContentFragment(item, cfTitles, cfModels, cfElements);
    }

    private static void extractParagraphs(DumAemSampleModelItem item, List<String> paragraphs) {
        if (item.getParagraphs() == null) {
            return;
        }
        for (String paragraph : item.getParagraphs()) {
            String cleaned = stripHtml(paragraph);
            if (!cleaned.isBlank()) {
                paragraphs.add(cleaned);
            }
        }
    }

    private static void extractText(DumAemSampleModelItem item, List<String> texts) {
        if (item.getText() != null && !item.getText().isBlank()) {
            texts.add(stripHtml(item.getText()));
        }
    }

    private static void extractImages(DumAemSampleModelItem item, List<String> images) {
        if (item.getSrc() != null && !item.getSrc().isBlank()) {
            images.add(item.getSrc());
        }
    }

    private static void extractContentFragment(DumAemSampleModelItem item,
            List<String> cfTitles, List<String> cfModels, List<String> cfElements) {
        String type = item.getType();
        if (type == null || !type.contains("contentfragment")) {
            return;
        }
        if (item.getTitle() != null && !item.getTitle().isBlank()) {
            cfTitles.add(item.getTitle());
        }
        if (item.getModel() != null && !item.getModel().isBlank()) {
            cfModels.add(item.getModel());
        }
        extractElements(item, cfElements);
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
