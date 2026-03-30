/*
 *
 * Copyright (C) 2016-2024 the original author or authors.
 *
 *  This program is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.viglet.dumont.connector.aem.sample.beans;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DumAemSampleModel {
    private String fragmentPath;
    private String title;
    private String description;
    private String language;
    private String templateName;
    private Long lastModifiedDate;
    @JsonProperty(":items")
    private Map<String, DumAemSampleModelItem> items;

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DumAemSampleModelItem {
        @JsonProperty(":type")
        private String type;
        @JsonProperty(":items")
        private Map<String, DumAemSampleModelItem> items;
        private String title;
        private String text;
        private Boolean richText;
        private List<String> paragraphs;
        private String model;
        private String src;
        private String alt;
        private Map<String, DumAemSampleModelElement> elements;
    }

    @Getter
    @Setter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class DumAemSampleModelElement {
        private String value;
        private String title;
        @JsonProperty(":type")
        private String type;
        private String dataType;
    }
}
