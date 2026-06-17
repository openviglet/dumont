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

import java.util.List;
import java.util.Map;

import org.pf4j.Extension;

import com.viglet.dumont.connector.aem.commons.DumAemObject;
import com.viglet.dumont.connector.aem.commons.ext.DumAemComponentExtractor;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentType;
import com.viglet.dumont.connector.aem.commons.ext.DumAemContentTypeField;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtModelJsonBase;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;
import com.viglet.turing.commons.se.field.TurSEFieldType;

/**
 * Sample model.json content extractor. It declares <em>content types</em> (roadmap
 * <strong>D25</strong>): each AEM template — collapsed through {@link #templateNameAliases() the
 * alias map} — routes to a content type that owns its schema and its
 * {@link DumAemComponentExtractor} composition, and {@code extract(id)} runs only that type's
 * composition and binds the record to that type's schema. The flat {@link #componentExtractors()}
 * stays as the fallback for a page whose template maps to no declared type.
 */
@Extension
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

    /** Canonical content types (the values templates resolve to). */
    public static final String TYPE_ARTICLE = "article";
    public static final String TYPE_EVENT = "event";

    @Override
    protected Class<DumAemSampleModel> getModelClass() {
        return DumAemSampleModel.class;
    }

    @Override
    protected Map<String, String> templateNameAliases() {
        return Map.of(
                "news", TYPE_ARTICLE,
                "blog", TYPE_ARTICLE,
                "article", TYPE_ARTICLE,
                "event", TYPE_EVENT);
    }

    @Override
    protected String templateName(DumAemSampleModel model, DumAemObject aemObject) {
        return model.getTemplateName();
    }

    @Override
    protected List<DumAemContentType<DumAemSampleModel>> contentTypes() {
        return List.of(
                DumAemContentType.<DumAemSampleModel>builder(TYPE_ARTICLE)
                        .fields(
                                DumAemContentTypeField.text(PAGE_TITLE).asMandatory(),
                                DumAemContentTypeField.text(PAGE_DESCRIPTION),
                                DumAemContentTypeField.string(PAGE_LANGUAGE),
                                DumAemContentTypeField.string(PAGE_TEMPLATE),
                                DumAemContentTypeField.string(FRAGMENT_PATH),
                                DumAemContentTypeField.of(PAGE_LAST_MODIFIED, TurSEFieldType.DATE),
                                DumAemContentTypeField.text(PARAGRAPHS).asMultiValued(),
                                DumAemContentTypeField.text(TEXTS).asMultiValued())
                        .extractors(
                                new DumAemSamplePageExtractor(),
                                new DumAemSampleParagraphExtractor(),
                                new DumAemSampleTextExtractor())
                        .build(),
                DumAemContentType.<DumAemSampleModel>builder(TYPE_EVENT)
                        .fields(
                                DumAemContentTypeField.text(PAGE_TITLE).asMandatory(),
                                DumAemContentTypeField.text(PAGE_DESCRIPTION),
                                DumAemContentTypeField.string(PAGE_TEMPLATE),
                                DumAemContentTypeField.string(IMAGES).asMultiValued(),
                                DumAemContentTypeField.text(CONTENT_FRAGMENT_TITLES).asMultiValued(),
                                DumAemContentTypeField.string(CONTENT_FRAGMENT_MODELS).asMultiValued(),
                                DumAemContentTypeField.text(CONTENT_FRAGMENT_ELEMENTS).asMultiValued())
                        .extractors(
                                new DumAemSamplePageExtractor(),
                                new DumAemSampleImageExtractor(),
                                new DumAemSampleContentFragmentExtractor())
                        .build());
    }

    @Override
    protected List<DumAemComponentExtractor<DumAemSampleModel>> componentExtractors() {
        return List.of(
                new DumAemSamplePageExtractor(),
                new DumAemSampleParagraphExtractor(),
                new DumAemSampleTextExtractor(),
                new DumAemSampleImageExtractor(),
                new DumAemSampleContentFragmentExtractor());
    }
}
