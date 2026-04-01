package com.viglet.dumont.connector.aem.commons.config;

import java.util.Locale;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

@RequiredArgsConstructor
@Accessors(chain = true)
@Getter
@Setter
public class DumAemSNSiteConfig {
	private String name;
	private Locale locale;

	public DumAemSNSiteConfig(String name, Locale locale) {
		super();
		this.name = name;
		this.locale = locale;
	}
}