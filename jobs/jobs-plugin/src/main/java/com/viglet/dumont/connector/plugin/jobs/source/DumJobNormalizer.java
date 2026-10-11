/*
 *
 * Copyright (C) 2016-2026 the original author or authors.
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

package com.viglet.dumont.connector.plugin.jobs.source;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Value normalization every job adapter shares (trabalharei <strong>TRA15</strong>): Brazilian state
 * names to UF codes, the monthly salary band, the job-page slug and URL, and the remote-work
 * signal. Pure and static; each method answers {@code null} when the input does not settle the
 * value, so an adapter never fills a field with a guess.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobNormalizer {

    private DumJobNormalizer() {
    }

    /** The job site's detail-page base when a source configures none. */
    public static final String DEFAULT_SITE_URL = "https://trabalharei.com.br/vaga/";

    /** The value {@code salaryPeriod} takes for a monthly salary. */
    public static final String MONTHLY = "mes";

    /** The value {@code remote} takes for a fully remote posting. */
    public static final String REMOTE = "remoto";

    /** The 27 federative units, keyed by their name folded by {@link #fold(String)}. */
    private static final Map<String, String> UF_BY_NAME = Map.ofEntries(
            Map.entry("acre", "AC"),
            Map.entry("alagoas", "AL"),
            Map.entry("amapa", "AP"),
            Map.entry("amazonas", "AM"),
            Map.entry("bahia", "BA"),
            Map.entry("ceara", "CE"),
            Map.entry("distrito federal", "DF"),
            Map.entry("espirito santo", "ES"),
            Map.entry("goias", "GO"),
            Map.entry("maranhao", "MA"),
            Map.entry("mato grosso", "MT"),
            Map.entry("mato grosso do sul", "MS"),
            Map.entry("minas gerais", "MG"),
            Map.entry("para", "PA"),
            Map.entry("paraiba", "PB"),
            Map.entry("parana", "PR"),
            Map.entry("pernambuco", "PE"),
            Map.entry("piaui", "PI"),
            Map.entry("rio de janeiro", "RJ"),
            Map.entry("rio grande do norte", "RN"),
            Map.entry("rio grande do sul", "RS"),
            Map.entry("rondonia", "RO"),
            Map.entry("roraima", "RR"),
            Map.entry("santa catarina", "SC"),
            Map.entry("sao paulo", "SP"),
            Map.entry("sergipe", "SE"),
            Map.entry("tocantins", "TO"));

    /** The 27 UF codes in alphabetical order (trabalharei <strong>TRA18</strong>). */
    public static final List<String> UFS = UF_BY_NAME.values().stream().sorted().toList();

    private static final Pattern MARKS = Pattern.compile("\\p{M}+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern TAGS = Pattern.compile("<[^>]*>");
    private static final Pattern SPACES = Pattern.compile("\\s+");
    private static final Pattern REMOTE_WORDS =
            Pattern.compile("\\b(remoto|remota|home[ -]?office|teletrabalho)\\b");
    private static final Pattern HYBRID_WORDS = Pattern.compile("\\bhibrid[oa]\\b");

    /** Lower case, accents stripped, whitespace collapsed — the form names are compared in. */
    public static String fold(String text) {
        if (text == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        return SPACES.matcher(MARKS.matcher(decomposed).replaceAll("").toLowerCase(Locale.ROOT))
                .replaceAll(" ").trim();
    }

    /**
     * The UF code for a Brazilian state given by name, with or without accents and in any case
     * ({@code "São Paulo"}, {@code "sao paulo"}), or by its code ({@code "sp"}); {@code null} when it
     * is not one of the 27 units.
     */
    public static String uf(String state) {
        String folded = fold(state);
        if (folded.isEmpty()) {
            return null;
        }
        String byName = UF_BY_NAME.get(folded);
        if (byName != null) {
            return byName;
        }
        String code = folded.toUpperCase(Locale.ROOT);
        return UF_BY_NAME.containsValue(code) ? code : null;
    }

    /**
     * The {@code salaryBand} for a monthly BRL amount. Each band includes its upper bound, as its
     * name reads: {@code ate-1.5k} is up to and including 1,500, {@code 1.5k-3k} is above 1,500 up to
     * 3,000, and so on; {@code 12k+} is above 12,000. {@code null} for a missing or non-positive amount.
     */
    public static String salaryBand(Double monthly) {
        if (monthly == null || monthly.isNaN() || monthly <= 0) {
            return null;
        }
        if (monthly <= 1_500) {
            return "ate-1.5k";
        }
        if (monthly <= 3_000) {
            return "1.5k-3k";
        }
        if (monthly <= 5_000) {
            return "3k-5k";
        }
        if (monthly <= 8_000) {
            return "5k-8k";
        }
        if (monthly <= 12_000) {
            return "8k-12k";
        }
        return "12k+";
    }

    /** An annual amount as a monthly one, rounded to cents. */
    public static double annualToMonthly(double annual) {
        return BigDecimal.valueOf(annual).divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    /** URL slug: lower case, accents stripped, every run of non-alphanumerics a single {@code -}. */
    public static String slug(String text) {
        String folded = fold(text);
        String dashed = NON_ALNUM.matcher(folded).replaceAll("-");
        int start = 0;
        int end = dashed.length();
        while (start < end && dashed.charAt(start) == '-') {
            start++;
        }
        while (end > start && dashed.charAt(end - 1) == '-') {
            end--;
        }
        return dashed.substring(start, end);
    }

    /**
     * The job's detail page on the site: {@code <base><slug(title)>-<id>/}. {@code null} when the
     * title has nothing to slug or there is no id.
     */
    public static String siteUrl(String base, String title, String id) {
        String slug = slug(title);
        if (slug.isEmpty() || id == null || id.isBlank()) {
            return null;
        }
        String root = base == null || base.isBlank() ? DEFAULT_SITE_URL : base;
        if (!root.endsWith("/")) {
            root = root + "/";
        }
        return root + slug + "-" + id + "/";
    }

    /**
     * The first 16 hex characters (8 bytes) of the SHA-256 of {@code value} in UTF-8 — a short, stable
     * id part for a partner key that is long or not URL-safe (trabalharei <strong>TRA19</strong>);
     * {@code null} for {@code null}.
     */
    public static String shortHash(String value) {
        if (value == null) {
            return null;
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 8);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", e);
        }
    }

    /** {@code text} with HTML tags removed and whitespace collapsed; {@code null} when nothing is left. */
    public static String plainText(String text) {
        if (text == null) {
            return null;
        }
        String plain = SPACES.matcher(TAGS.matcher(text).replaceAll(" ")).replaceAll(" ").trim();
        return plain.isEmpty() ? null : plain;
    }

    /**
     * {@link #REMOTE} when any of {@code texts} says the work is remote (remoto, remota, home office,
     * teletrabalho) and none says it is hybrid; otherwise {@code null}. Never answers
     * {@code presencial}: a posting that does not mention remote work has not stated it is on-site.
     */
    public static String remote(String... texts) {
        boolean remote = false;
        for (String text : texts) {
            String folded = fold(text);
            if (HYBRID_WORDS.matcher(folded).find()) {
                return null;
            }
            remote |= REMOTE_WORDS.matcher(folded).find();
        }
        return remote ? REMOTE : null;
    }
}
