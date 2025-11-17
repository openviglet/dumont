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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.viglet.dumont.commons.se.result.spellcheck.DumSESpellCheckResult;
import com.viglet.dumont.commons.sn.search.DumSNSiteSearchContext;

import lombok.Getter;
import lombok.Setter;

/**
 * Spell Check of Dumont ES Semantic Navigation response.
 * 
 * @author Alexandre Oliveira
 * 
 * @since 0.3.5
 */

@Setter
@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class DumSNSiteSpellCheckBean implements Serializable {

	private boolean correctedText;
	private boolean usingCorrectedText;
	private DumSNSiteSpellCheckText original;
	private DumSNSiteSpellCheckText corrected;

	public DumSNSiteSpellCheckBean() {
		super();
	}

	public DumSNSiteSpellCheckBean(DumSNSiteSearchContext context, DumSESpellCheckResult dumSESpellCheckResult) {
		super();
		this.correctedText = dumSESpellCheckResult.isCorrected();
		this.original = new DumSNSiteSpellCheckText(context.getUri(), context.getDumSEParameters().getQuery(), true);
		this.corrected = new DumSNSiteSpellCheckText(context.getUri(), dumSESpellCheckResult.getCorrectedText(), false);
		this.usingCorrectedText = dumSESpellCheckResult.isUsingCorrected();
	}

}