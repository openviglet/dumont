package com.viglet.dumont.commons.sn.search;

import java.io.Serializable;
import java.net.URI;
import java.util.Locale;

import com.viglet.dumont.commons.se.DumSEParameters;
import com.viglet.dumont.commons.sn.DumSNConfig;
import com.viglet.dumont.commons.sn.bean.DumSNSitePostParamsBean;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DumSNSiteSearchContext implements Serializable {
	private DumSNConfig dumSNConfig;
	private String siteName;
	private DumSEParameters dumSEParameters;
	private Locale locale;
	private DumSNSitePostParamsBean dumSNSitePostParamsBean;
	private URI uri;

	public DumSNSiteSearchContext(String siteName, DumSNConfig dumSNConfig, DumSEParameters dumSEParameters,
			Locale locale, URI uri, DumSNSitePostParamsBean dumSNSitePostParamsBean) {
		super();
		this.siteName = siteName;
		this.dumSNConfig = dumSNConfig;
		this.dumSEParameters = dumSEParameters;
		this.locale = locale;
		this.uri = uri;
		this.dumSNSitePostParamsBean = dumSNSitePostParamsBean == null ? new DumSNSitePostParamsBean()
				: dumSNSitePostParamsBean;
	}

	public DumSNSiteSearchContext(String siteName, DumSNConfig dumSNConfig, DumSEParameters dumSEParameters,
			Locale locale, URI uri) {
		this(siteName, dumSNConfig, dumSEParameters, locale, uri, null);
	}

	@Override
	public String toString() {
		return "DumSNSiteSearchContext{" +
				"siteName='" + siteName + '\'' +
				", dumSEParameters=" + dumSEParameters +
				", locale=" + locale +
				", dumSNSitePostParamsBean=" + dumSNSitePostParamsBean +
				", uri=" + uri +
				'}';
	}
}
