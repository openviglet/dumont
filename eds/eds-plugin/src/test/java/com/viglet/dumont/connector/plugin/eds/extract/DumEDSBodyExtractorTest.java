/*
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

package com.viglet.dumont.connector.plugin.eds.extract;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.Test;

class DumEDSBodyExtractorTest {

    private final DumEDSBodyExtractor extractor = new DumEDSBodyExtractor();

    @Test
    void extractsTextFromMainAndStripsScriptAndStyle() {
        Document doc = Jsoup.parse("""
                <html><body>
                  <nav>Top nav</nav>
                  <main>
                    <h1>Conta PF</h1>
                    <p>Sua conta digital sem tarifas.</p>
                    <script>window.foo = 1;</script>
                    <style>.x{color:red}</style>
                  </main>
                  <footer>Footer junk</footer>
                </body></html>
                """);

        String body = extractor.extract(doc, "main", List.of());

        assertThat(body).contains("Conta PF");
        assertThat(body).contains("Sua conta digital sem tarifas.");
        assertThat(body).doesNotContain("window.foo");
        assertThat(body).doesNotContain("color:red");
    }

    @Test
    void removesGivenSelectorsBeforeNormalising() {
        Document doc = Jsoup.parse("""
                <html><body>
                  <main>
                    <h1>Conta PF</h1>
                    <aside class="cta">Baixe o App agora!</aside>
                    <p>Use o PIX integrado.</p>
                  </main>
                </body></html>
                """);

        String body = extractor.extract(doc, "main", List.of(".cta", "aside"));

        assertThat(body).doesNotContain("Baixe o App");
        assertThat(body).contains("Use o PIX integrado");
    }

    @Test
    void fallsBackToBodyWhenSelectorMisses() {
        Document doc = Jsoup.parse("<html><body><p>Plain body</p></body></html>");

        String body = extractor.extract(doc, "main", List.of());

        assertThat(body).contains("Plain body");
    }

    @Test
    void collapsesExcessiveWhitespace() {
        Document doc = Jsoup.parse("""
                <html><body><main>
                  <p>One</p>


                  <p>Two</p>
                </main></body></html>
                """);

        String body = extractor.extract(doc, "main", List.of());

        assertThat(body).doesNotContain("   ");
        assertThat(body).doesNotContain("\n\n\n");
    }

    @Test
    void mainExcludesCTARepeatedInNavAndFooter() {
        // Spec test case 5: page body should NOT contain a CTA ("Baixe o App")
        // repeated in the nav/footer — the removeSelectors must strip it.
        Document doc = Jsoup.parse("""
                <html>
                  <head><title>Personal</title></head>
                  <body>
                    <header><nav><a href="/baixe-o-app">Baixe o App</a></nav></header>
                    <main>
                      <h1>Welcome to the site</h1>
                      <p>Digital account, pix, investments and credit card.</p>
                    </main>
                    <footer><a href="/baixe-o-app">Baixe o App</a></footer>
                  </body>
                </html>
                """);

        String body = extractor.extract(doc, "main",
                List.of("nav", "footer", "aside", "header", "[role=navigation]"));

        assertThat(body).contains("Welcome to the site");
        assertThat(body).contains("Digital account, pix, investments and credit card");
        assertThat(body).doesNotContain("Baixe o App");
    }
}
