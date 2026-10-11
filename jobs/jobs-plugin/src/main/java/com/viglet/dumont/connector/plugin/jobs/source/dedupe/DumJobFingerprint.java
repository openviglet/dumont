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

package com.viglet.dumont.connector.plugin.jobs.source.dedupe;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.IsoFields;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobsStructuredSource;

/**
 * The cross-source identity of a job opening (trabalharei <strong>TRA23</strong>): the same opening
 * reached through several partners (Adzuna, Jooble, BNE, JSearch) yields the same fingerprint, and is
 * published once, as {@code job-<hash>}.
 *
 * <p>
 * The key is {@code title|company|city|week}, each part normalized as follows:
 *
 * <ul>
 * <li><strong>title</strong> &mdash; accents and case folded ({@link DumJobNormalizer#fold}); the
 * gendered markers {@code (m/f)}, {@code h/m}, {@code (a)} removed and {@code PL/SQL} protected as
 * one word; every run of characters other than {@code a-z 0-9 + #} becomes a space (so {@code C++}
 * and {@code C#} survive); then the {@link #SENIORITY_WORDS seniority words} (junior, jr, pleno, pl,
 * senior, sr, and the roman levels i&ndash;v) and the {@link #FILLER_WORDS filler words} (articles and
 * prepositions, "vaga", "oportunidade", "urgente", "contrata", and the work-mode words "remoto",
 * "hibrido", "presencial", "home office", which the city part already carries) are dropped, and the
 * remaining words are joined by single spaces. Word order is kept. A title left with no word yields
 * <em>no</em> fingerprint &mdash; "Vaga urgente" would otherwise merge every such posting of a company
 * in a week &mdash; and the posting is published on its own id.</li>
 * <li><strong>company</strong> &mdash; folded the same way, punctuation removed, trailing legal-form
 * words ({@code ltda}, {@code s/a}, {@code sa}, {@code eireli}, {@code me}, {@code epp}, {@code inc},
 * {@code llc}, {@code ltd}, {@code corp}) dropped, and the names partners give a hidden employer
 * ("confidencial", "empresa confidencial", "sigilosa", "nao informada", ...) read as no company.</li>
 * <li><strong>city</strong> &mdash; the canonical place the location enricher (TRA20) publishes, read
 * through the same resolver <em>before</em> the chain runs: {@code <folded IBGE name>/<UF>}; the word
 * {@value #REMOTE_CITY} for a remote posting; empty when the place resolved to no municipality (a UF
 * alone does not count).</li>
 * <li><strong>week</strong> &mdash; the ISO-8601 week of {@code publishedAt} in {@value #ZONE}, as
 * {@code 2026-W41}. A posting with no readable {@code publishedAt} yields no fingerprint (the
 * mandatory-field check skips it anyway).</li>
 * </ul>
 *
 * <p>
 * <strong>Trade-offs.</strong> A missing company or city is part of the key as an empty string, so a
 * posting without a company (BNE never sends one) only matches other postings without one: the same
 * opening shown with its employer on Adzuna and anonymously on BNE stays two documents. Letting an
 * empty part match anything instead would fold every anonymous "Vendedor" of a city and week into
 * whichever named one came first &mdash; a false merge hides an opening, where a missed merge only
 * shows it twice. The week bucket has the same shape: one opening published on a Sunday by one partner
 * and on the Monday by another falls into two weeks and is not merged.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.3
 */
public final class DumJobFingerprint {

    private DumJobFingerprint() {
    }

    /** The id prefix of a group document. No adapter id ({@code <source>-<partnerId>}) starts so. */
    public static final String GROUP_PREFIX = "job-";

    /** The zone a posting's week is read in (the job site is Brazilian). */
    public static final String ZONE = "America/Sao_Paulo";

    /** The value the city part takes for a remote posting. */
    public static final String REMOTE_CITY = "remoto";

    /** Seniority words dropped from a title: one opening is posted as "Dev" and as "Dev Pleno". */
    public static final Set<String> SENIORITY_WORDS = Set.of("junior", "jr", "pleno", "plena", "pl",
            "senior", "sr", "i", "ii", "iii", "iv", "v");

    /** Words that never tell one opening from another. */
    public static final Set<String> FILLER_WORDS = Set.of("a", "o", "as", "os", "ao", "aos", "um", "uma",
            "de", "da", "do", "das", "dos", "d", "para", "pra", "p", "com", "em", "na", "no", "nas", "nos",
            "e", "ou", "vaga", "vagas", "oportunidade", "oportunidades", "urgente", "imediato",
            "imediata", "contrata", "contratamos", "contratando", "contratacao", "remoto", "remota",
            "hibrido", "hibrida", "presencial", "home", "office", "homeoffice", "teletrabalho");

    private static final Set<String> COMPANY_SUFFIXES = Set.of("ltda", "ltd", "sa", "eireli", "me",
            "epp", "inc", "llc", "corp");

    private static final Set<String> HIDDEN_COMPANY = Set.of("confidencial", "empresa confidencial",
            "sigilosa", "empresa sigilosa", "nao informada", "nao informado", "anonima", "anonimo");

    private static final ZoneId WEEK_ZONE = ZoneId.of(ZONE);
    private static final Pattern GENDER =
            Pattern.compile("\\(?\\b[mhf]\\s*/\\s*[mhf]\\b\\)?|\\(as?\\)");
    private static final Pattern PL_SQL = Pattern.compile("\\bpl\\s*/\\s*sql\\b");
    private static final Pattern TITLE_SEPARATORS = Pattern.compile("[^a-z0-9+#]+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");

    /** The four normalized parts of a fingerprint. */
    public record Key(String title, String company, String city, String week) {

        /** {@code title|company|city|week} &mdash; what is hashed. */
        public String text() {
            return String.join("|", title, company, city, week);
        }

        /** The SHA-256 of {@link #text()}: its first 16 bytes, in hex (32 characters). */
        public String hash() {
            return DumJobFingerprint.hash(text());
        }

        /** The id the group document is published under: {@code job-<hash>}. */
        public String groupId() {
            return GROUP_PREFIX + hash();
        }
    }

    /**
     * The fingerprint of a posting, or empty when its title normalizes to nothing or its
     * {@code publishedAt} cannot be read.
     *
     * @param title       the posting title
     * @param company     the employer, may be {@code null}
     * @param city        the canonical municipality name, may be {@code null}
     * @param uf          the canonical UF, may be {@code null}
     * @param remote      whether the place resolved to remote work
     * @param publishedAt the posting's {@code publishedAt} (a {@code java.time} value or ISO-8601 text)
     */
    public static Optional<Key> of(String title, String company, String city, String uf, boolean remote,
            Object publishedAt) {
        String normalizedTitle = title(title);
        String week = isoWeek(publishedAt);
        if (normalizedTitle.isEmpty() || week == null) {
            return Optional.empty();
        }
        return Optional.of(new Key(normalizedTitle, company(company), city(city, uf, remote), week));
    }

    /** The title part (see the class comment); empty when nothing distinguishing is left. */
    public static String title(String title) {
        String folded = DumJobNormalizer.fold(title);
        folded = PL_SQL.matcher(folded).replaceAll(" plsql ");
        folded = GENDER.matcher(folded).replaceAll(" ");
        List<String> kept = new ArrayList<>();
        for (String word : TITLE_SEPARATORS.split(folded)) {
            if (!word.isEmpty() && !SENIORITY_WORDS.contains(word) && !FILLER_WORDS.contains(word)) {
                kept.add(word);
            }
        }
        return String.join(" ", kept);
    }

    /** The company part (see the class comment); empty for a missing or hidden employer. */
    public static String company(String company) {
        String folded = DumJobNormalizer.fold(company).replace("s/a", "sa").replace("s.a.", "sa");
        List<String> words = new ArrayList<>(Arrays.asList(NON_ALNUM.split(folded)));
        words.removeIf(String::isEmpty);
        while (words.size() > 1 && COMPANY_SUFFIXES.contains(words.getLast())) {
            words.removeLast();
        }
        String joined = String.join(" ", words);
        return HIDDEN_COMPANY.contains(joined) ? "" : joined;
    }

    /** The city part (see the class comment). */
    public static String city(String city, String uf, boolean remote) {
        if (remote) {
            return REMOTE_CITY;
        }
        String folded = DumJobNormalizer.fold(city);
        if (folded.isEmpty()) {
            return "";
        }
        return uf == null || uf.isBlank() ? folded : folded + "/" + uf.trim().toUpperCase(Locale.ROOT);
    }

    /** {@code publishedAt} as its ISO week ({@code 2026-W41}) in {@value #ZONE}; else {@code null}. */
    public static String isoWeek(Object publishedAt) {
        Instant instant = DumJobsStructuredSource.toInstant(publishedAt);
        if (instant == null) {
            return null;
        }
        ZonedDateTime at = instant.atZone(WEEK_ZONE);
        return "%d-W%02d".formatted(at.get(IsoFields.WEEK_BASED_YEAR),
                at.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR));
    }

    /** Whether {@code id} names a group document rather than an adapter posting. */
    public static boolean isGroupId(String id) {
        return id != null && id.startsWith(GROUP_PREFIX);
    }

    /** The fingerprint hash a group id carries, or {@code null} when {@code id} is not one. */
    public static String hashOf(String id) {
        return isGroupId(id) ? id.substring(GROUP_PREFIX.length()) : null;
    }

    static String hash(String text) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JVM", e);
        }
    }
}
