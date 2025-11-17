/*
 * Copyright (C) 2016-2021 the original author or authors. 
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.viglet.dumont.commons.sn.bean.spellcheck;

import java.io.Serializable;
import java.net.URI;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.dumont.commons.sn.search.DumSNParamType;
import com.viglet.dumont.commons.utils.DumCommonsUtils;
import lombok.Getter;
import lombok.Setter;

/**
 * Spell Check Text of Dumont ES Semantic Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.5
 */

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DumSNSiteSpellCheckText implements Serializable {
	private static final String TRUE = "1";
	private String text;
	private String link;

	public DumSNSiteSpellCheckText(URI uri, String text, boolean isOriginal) {
		super();
		URI uriModified = DumCommonsUtils.addOrReplaceParameter(uri, DumSNParamType.QUERY, text, true);
		if (isOriginal) {
			uriModified = DumCommonsUtils.addOrReplaceParameter(uriModified, DumSNParamType.AUTO_CORRECTION_DISABLED,
					TRUE, false);
		}
		this.text = text;
		this.link = uriModified.toString();
	}

}
