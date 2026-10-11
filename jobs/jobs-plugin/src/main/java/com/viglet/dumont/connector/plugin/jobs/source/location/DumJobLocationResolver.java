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

package com.viglet.dumont.connector.plugin.jobs.source.location;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.viglet.dumont.connector.commons.spi.structured.recipe.DumTaxonomy;
import com.viglet.dumont.connector.plugin.jobs.source.DumJobNormalizer;

/**
 * Turns whatever a partner wrote for a job's place into one canonical IBGE city and UF (trabalharei
 * <strong>TRA20</strong>). Pure; built once per taxonomy version.
 *
 * <p>
 * Inputs are the adapter's {@code city} and {@code state} plus an optional raw location text. Each
 * text is split into parts ({@code "Sao Paulo, SP"}, {@code "São Paulo - SP"}, {@code "Campinas/SP"},
 * {@code "Belo Horizonte (MG)"}, {@code "Rio de Janeiro - Brasil"}); the country and work-mode words
 * are dropped; the last part naming a UF (code or name) is the state and the parts before it,
 * nearest first, are the city candidates. Names are compared with accents, case and punctuation
 * folded away, and the answer is always the official IBGE spelling.
 *
 * <ul>
 * <li>A city name unique in Brazil settles its UF. An ambiguous one ({@code "Bom Jesus"}, in five
 * states) with no UF given keeps the city and leaves the UF absent.</li>
 * <li>A city that does not exist in the stated UF is dropped; the UF is kept.</li>
 * <li>A remote marker (remoto, home office, anywhere, 100% remoto, teletrabalho) answers
 * {@code remote = remoto} and no city; a UF stated next to it is kept.</li>
 * <li>A lone {@code "São Paulo"} or {@code "Rio de Janeiro"} is read as the capital, since a job ad
 * naming only the state by its capital's name almost always means the city.</li>
 * <li>A few nicknames ({@link #ALIASES}) resolve to their city, unless the stated UF contradicts
 * them; they are matched case-insensitively but with accents, so {@code "Poa"} is Porto Alegre while
 * {@code "Poá"} is the municipality in SP.</li>
 * </ul>
 *
 * <p>
 * Text that names something but resolves to neither a city, a UF nor a remote marker, and a city that
 * was dropped, come back in {@link Resolution#unresolved()} as the raw text — never as a guess.
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
public final class DumJobLocationResolver {

    /** The outcome for one job: canonical city and UF (each may be absent), remote, raw leftovers. */
    public record Resolution(String city, String uf, boolean remote, String unresolved) {

        static final Resolution NONE = new Resolution(null, null, false, null);
    }

    private record Municipality(String name, String uf) {
    }

    /**
     * What one location text says, before it is matched against the list. {@code fallback} is a part
     * that names a state but may also be a city ({@code "São Paulo"} alone); it is tried last and its
     * failing to match is not an unresolved city.
     */
    private record Parsed(List<String> candidates, String fallback, String uf, boolean remote,
            boolean meaningful) {

        static final Parsed EMPTY = new Parsed(List.of(), null, null, false, false);
    }

    /** Nickname (lower case, accents kept) &rarr; IBGE code. Small on purpose: only unmistakable ones. */
    static final Map<String, String> ALIASES = Map.of(
            "sampa", "3550308",
            "bh", "3106200",
            "rio", "3304557",
            "floripa", "4205407",
            "poa", "4314902",
            "cwb", "4106902",
            "bsb", "5300108");

    /** State names that, written alone, mean their capital. */
    private static final Set<String> CAPITAL_NAMED_STATES = Set.of("sao paulo", "rio de janeiro");

    /** Parts that say nothing about the place. */
    private static final Set<String> NOISE = Set.of("brasil", "brazil", "br", "hibrido", "hibrida",
            "hybrid", "presencial", "on site", "onsite");

    private static final Pattern REMOTE = Pattern.compile(
            "\\b(remoto|remota|remote|home[ -]?office|anywhere|teletrabalho|trabalho a distancia)\\b");
    private static final Pattern HYBRID = Pattern.compile("\\b(hibrid[oa]|hybrid)\\b");
    private static final Pattern BRACKETS = Pattern.compile("[()\\[\\]]");
    private static final Pattern SEPARATORS = Pattern.compile("\\s*[,/;|]\\s*|\\s+[-–—]\\s+");
    private static final Pattern STATE_PREFIX = Pattern.compile("^(estado d[eoa]|state of)\\s+");
    private static final Pattern NON_ALNUM = Pattern.compile("[^a-z0-9]+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private final Map<String, List<Municipality>> byKey = new HashMap<>();
    private final Map<String, Municipality> byCode = new HashMap<>();

    private DumJobLocationResolver(DumTaxonomy taxonomy) {
        taxonomy.table().forEach((code, name) -> {
            Municipality municipality = new Municipality(name, DumIbgeMunicipalities.ufOf(code));
            byCode.put(code, municipality);
            byKey.computeIfAbsent(key(name), k -> new ArrayList<>()).add(municipality);
        });
    }

    /** A resolver over {@code taxonomy} (IBGE code &rarr; municipality name). */
    public static DumJobLocationResolver of(DumTaxonomy taxonomy) {
        return new DumJobLocationResolver(taxonomy);
    }

    /** How many municipalities this resolver knows. */
    public int size() {
        return byCode.size();
    }

    /**
     * The canonical place for a job whose adapter wrote {@code city}, {@code state} and, optionally,
     * the partner's raw location text {@code raw}. Any of them may be {@code null}.
     */
    public Resolution resolve(String city, String state, String raw) {
        Parsed fromCity = parse(city, true);
        Parsed fromRaw = parse(raw, false);
        String stateUf = ufOfPart(state);
        boolean stateGiven = state != null && !state.isBlank();
        if (!fromCity.meaningful() && !fromRaw.meaningful() && !stateGiven) {
            return Resolution.NONE;
        }
        String uf = firstNonNull(stateUf, fromCity.uf(), fromRaw.uf());
        if (fromCity.remote() || fromRaw.remote()) {
            return new Resolution(null, uf, true, null);
        }
        List<String> candidates = new ArrayList<>(fromCity.candidates());
        candidates.addAll(fromRaw.candidates());
        List<String> tries = new ArrayList<>(candidates);
        for (Parsed parsed : List.of(fromCity, fromRaw)) {
            if (parsed.fallback() != null) {
                tries.add(parsed.fallback());
            }
        }
        for (String candidate : tries) {
            Municipality found = match(candidate, uf);
            if (found != null) {
                return new Resolution(found.name(), found.uf() != null ? found.uf() : uf, false, null);
            }
        }
        // A named city that matched nothing (unknown, or not in the stated UF) is dropped and kept raw;
        // so is a text that settled nothing at all.
        boolean unresolved = !candidates.isEmpty() || uf == null;
        return new Resolution(null, uf, false, unresolved ? rawText(city, state, raw) : null);
    }

    /**
     * The municipality {@code candidate} names, inside {@code uf} when one is known. Without a UF a
     * unique name answers its own UF; a name shared by several states answers the name with no UF, after
     * first trying the exact spelling (accents included) to tell, say, Goianá (MG) from Goiana (PE).
     */
    private Municipality match(String candidate, String uf) {
        Municipality alias = alias(candidate);
        if (alias != null && (uf == null || uf.equals(alias.uf()))) {
            return alias;
        }
        List<Municipality> named = byKey.get(key(candidate));
        if (named == null) {
            return null;
        }
        if (uf != null) {
            return named.stream().filter(m -> uf.equals(m.uf())).findFirst().orElse(null);
        }
        if (named.size() == 1) {
            return named.getFirst();
        }
        String spelled = lower(candidate);
        List<Municipality> exact = named.stream().filter(m -> lower(m.name()).equals(spelled)).toList();
        List<Municipality> pool = exact.isEmpty() ? named : exact;
        if (pool.size() == 1) {
            return pool.getFirst();
        }
        String name = pool.getFirst().name();
        return pool.stream().allMatch(m -> m.name().equals(name)) ? new Municipality(name, null) : null;
    }

    private Municipality alias(String candidate) {
        String code = ALIASES.get(lower(candidate));
        return code == null ? null : byCode.get(code);
    }

    /**
     * Split one location text into city candidates (nearest the UF first), its UF and remote flag.
     * When the only part names a state by name: in the adapter's {@code city} field it is a city
     * ({@code cityField}, e.g. Paraná/RN) and sets no UF of its own; in free text it stays a UF, and is
     * also tried as a city only when it is a capital's name ({@code "São Paulo"}).
     */
    private static Parsed parse(String text, boolean cityField) {
        if (text == null || text.isBlank()) {
            return Parsed.EMPTY;
        }
        String folded = DumJobNormalizer.fold(text);
        boolean remote = REMOTE.matcher(folded).find() && !HYBRID.matcher(folded).find();
        List<String> parts = parts(text);
        if (parts.isEmpty()) {
            return new Parsed(List.of(), null, null, remote, remote);
        }
        int ufAt = -1;
        String uf = null;
        for (int i = parts.size() - 1; i >= 0 && uf == null; i--) {
            uf = ufOfPart(parts.get(i));
            ufAt = uf != null ? i : -1;
        }
        List<String> candidates = new ArrayList<>();
        int last = ufAt >= 0 ? ufAt - 1 : parts.size() - 1;
        for (int i = last; i >= 0; i--) {
            candidates.add(parts.get(i));
        }
        String fallback = null;
        if (ufAt >= 0 && candidates.isEmpty() && ufCode(parts.get(ufAt)) == null) {
            String named = parts.get(ufAt);
            if (cityField) {
                fallback = named;
                uf = null;
            } else if (CAPITAL_NAMED_STATES.contains(key(named))) {
                fallback = named;
            }
        }
        return new Parsed(candidates, fallback, uf, remote, true);
    }

    /** The meaningful parts of a location text, in order: no country, work-mode or remote words. */
    private static List<String> parts(String text) {
        List<String> parts = new ArrayList<>();
        for (String piece : SEPARATORS.split(BRACKETS.matcher(text).replaceAll(","))) {
            String part = SPACES.matcher(piece).replaceAll(" ").trim();
            // "Campinas-SP": a UF code glued to the city by a bare hyphen.
            int hyphen = part.lastIndexOf('-');
            String suffix = hyphen > 0 ? part.substring(hyphen + 1).trim() : "";
            if (suffix.length() == 2 && ufCode(suffix) != null) {
                addPart(parts, part.substring(0, hyphen).trim());
                addPart(parts, suffix);
            } else {
                addPart(parts, part);
            }
        }
        return parts;
    }

    private static void addPart(List<String> parts, String part) {
        String key = key(part);
        if (key.isEmpty() || NOISE.contains(key) || REMOTE.matcher(key).find()
                || key.chars().allMatch(Character::isDigit)) {
            return;
        }
        parts.add(part);
    }

    /** The UF a part names, by code or by name, with an optional "Estado de"/"State of" in front. */
    static String ufOfPart(String part) {
        if (part == null || part.isBlank()) {
            return null;
        }
        String folded = STATE_PREFIX.matcher(DumJobNormalizer.fold(part)).replaceFirst("");
        return DumJobNormalizer.uf(folded);
    }

    private static String ufCode(String code) {
        String uf = DumJobNormalizer.uf(code);
        return uf != null && uf.equalsIgnoreCase(code) ? uf : null;
    }

    /** The matching key: accents, case and punctuation folded, single spaces. */
    static String key(String text) {
        return NON_ALNUM.matcher(DumJobNormalizer.fold(text)).replaceAll(" ").trim();
    }

    private static String lower(String text) {
        return SPACES.matcher(text.toLowerCase(Locale.ROOT)).replaceAll(" ").trim();
    }

    /** The raw text to keep for an unresolved place: the partner's own text, else city and state. */
    private static String rawText(String city, String state, String raw) {
        if (raw != null && !raw.isBlank()) {
            return raw.trim();
        }
        List<String> given = new ArrayList<>();
        for (String value : new String[] { city, state }) {
            if (value != null && !value.isBlank()) {
                given.add(value.trim());
            }
        }
        return String.join(", ", given);
    }

    private static String firstNonNull(String... values) {
        for (String value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }
}
