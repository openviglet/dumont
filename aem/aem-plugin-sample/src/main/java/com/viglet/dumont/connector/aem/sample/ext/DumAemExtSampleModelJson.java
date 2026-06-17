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

import org.pf4j.Extension;

import com.viglet.dumont.connector.aem.commons.ext.DumAemComponentExtractor;
import com.viglet.dumont.connector.aem.commons.ext.DumAemExtModelJsonBase;
import com.viglet.dumont.connector.aem.sample.beans.DumAemSampleModel;

/**
 * Sample model.json content extractor, composed from single-concern
 * {@link DumAemComponentExtractor}s instead of one monolithic method. Each
 * registered extractor (page attributes, paragraphs, texts, images, content
 * fragments) is independently unit-testable; this class only declares the model
 * class and the composition order.
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

    @Override
    protected Class<DumAemSampleModel> getModelClass() {
        return DumAemSampleModel.class;
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
