package com.viglet.dumont.commons.se.result.spellcheck;

import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class DumSESpellCheckResult {

	private boolean corrected;
	private String correctedText;
	private boolean usingCorrected;

	public DumSESpellCheckResult() {
		super();
		this.corrected = false;
		this.correctedText = "";
	}

	public DumSESpellCheckResult(boolean isCorrected, String correctedText) {
		super();
		this.corrected = isCorrected;
		this.correctedText = correctedText;
	}

	@Override
	public String toString() {
		return "DumSESpellCheckResult{" +
				"corrected=" + corrected +
				", correctedText='" + correctedText + '\'' +
				", usingCorrected=" + usingCorrected +
				'}';
	}
}