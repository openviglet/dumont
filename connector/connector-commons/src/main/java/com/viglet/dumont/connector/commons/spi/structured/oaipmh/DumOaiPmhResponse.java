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

package com.viglet.dumont.connector.commons.spi.structured.oaipmh;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

/**
 * One parsed OAI-PMH 2.0 {@code ListRecords} response (roadmap <strong>D315</strong>).
 *
 * <p>
 * Parsed with the JDK's DOM parser, namespace-aware and locked down against XXE: a response that
 * declares a {@code DOCTYPE} is refused outright, and external entities, external DTDs and
 * XInclude are all off. A repository has no reason to send a DTD, so refusing one costs nothing and
 * closes the whole class of entity attacks rather than one spelling of it.
 *
 * <p>
 * Metadata is read as Dublin Core: every element in the {@code dc} namespace under a record's
 * {@code metadata}, whatever wraps it, keyed by local name. A record in DSpace's native {@code dim}
 * format (roadmap <strong>D314</strong>) is read as well, each {@code <dim:field>} with its schema,
 * element and qualifier, and so are the header's {@code setSpec} values.
 *
 * @param responseDate    the response's {@code responseDate}, or {@code null} if absent
 * @param errors          each {@code <error>} as code to message, in order
 * @param records         the records of this page, in document order
 * @param resumptionToken the token for the next page, or {@code null} when this is the last
 *
 * @author Alexandre Oliveira
 * @since 2026.3.1
 */
record DumOaiPmhResponse(String responseDate, Map<String, String> errors, List<Item> records,
        String resumptionToken) {

    static final String OAI_NS = "http://www.openarchives.org/OAI/2.0/";
    static final String DC_NS = "http://purl.org/dc/elements/1.1/";
    static final String DIM_NS = "http://www.dspace.org/xmlns/dspace/dim";

    /**
     * One harvested record: its header, its Dublin Core values by element name, its sets and, for
     * a {@code dim} record, every {@code <dim:field>} in document order.
     */
    record Item(String identifier, String datestamp, boolean deleted, Map<String, List<String>> dc,
            List<String> sets, List<DimField> dim) {

        Item(String identifier, String datestamp, boolean deleted, Map<String, List<String>> dc) {
            this(identifier, datestamp, deleted, dc, List.of(), List.of());
        }

        /** Every value of one Dublin Core element, empty when the record has none. */
        List<String> values(String element) {
            return dc.getOrDefault(element, List.of());
        }
    }

    /**
     * One {@code <dim:field mdschema element qualifier>} value; {@code qualifier} is {@code null}
     * when the field has none.
     */
    record DimField(String schema, String element, String qualifier, String value) {
    }

    /**
     * Parses one response body.
     *
     * @throws IOException when the body is not well-formed XML, declares a DOCTYPE, or is not an
     *                     OAI-PMH response
     */
    static DumOaiPmhResponse parse(String xml) throws IOException {
        Element root = document(xml).getDocumentElement();
        if (!OAI_NS.equals(root.getNamespaceURI()) || !"OAI-PMH".equals(root.getLocalName())) {
            throw new IOException("not an OAI-PMH response (root <%s>)".formatted(root.getNodeName()));
        }
        String responseDate = child(root, "responseDate").map(DumOaiPmhResponse::text).orElse(null);
        Map<String, String> errors = new LinkedHashMap<>();
        for (Element error : children(root, "error")) {
            errors.put(error.getAttribute("code"), text(error));
        }
        List<Item> records = new ArrayList<>();
        String token = null;
        Optional<Element> list = child(root, "ListRecords");
        if (list.isPresent()) {
            children(list.get(), "record").forEach(r -> records.add(item(r)));
            token = child(list.get(), "resumptionToken").map(DumOaiPmhResponse::text).orElse(null);
        }
        return new DumOaiPmhResponse(responseDate, errors, records, token);
    }

    private static Item item(Element element) {
        Optional<Element> header = child(element, "header");
        boolean deleted = header.map(h -> "deleted".equals(h.getAttribute("status"))).orElse(false);
        String identifier = header.flatMap(h -> child(h, "identifier")).map(DumOaiPmhResponse::text)
                .orElse(null);
        String datestamp = header.flatMap(h -> child(h, "datestamp")).map(DumOaiPmhResponse::text)
                .orElse(null);
        List<String> sets = header.map(h -> children(h, "setSpec").stream().map(DumOaiPmhResponse::text)
                .filter(Objects::nonNull).toList()).orElse(List.of());
        Map<String, List<String>> dc = new LinkedHashMap<>();
        List<DimField> dim = new ArrayList<>();
        child(element, "metadata").ifPresent(metadata -> {
            NodeList elements = metadata.getElementsByTagNameNS(DC_NS, "*");
            for (int i = 0; i < elements.getLength(); i++) {
                Element value = (Element) elements.item(i);
                String text = text(value);
                if (text != null) {
                    dc.computeIfAbsent(value.getLocalName(), k -> new ArrayList<>()).add(text);
                }
            }
            NodeList fields = metadata.getElementsByTagNameNS(DIM_NS, "field");
            for (int i = 0; i < fields.getLength(); i++) {
                Element field = (Element) fields.item(i);
                String text = text(field);
                if (text != null) {
                    dim.add(new DimField(attribute(field, "mdschema"), attribute(field, "element"),
                            attribute(field, "qualifier"), text));
                }
            }
        });
        return new Item(identifier, datestamp, deleted, dc, sets, dim);
    }

    private static String attribute(Element element, String name) {
        String value = element.getAttribute(name).strip();
        return value.isEmpty() ? null : value;
    }

    private static Optional<Element> child(Element parent, String localName) {
        return children(parent, localName).stream().findFirst();
    }

    /** The direct children of {@code parent} in the OAI namespace named {@code localName}. */
    private static List<Element> children(Element parent, String localName) {
        List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && OAI_NS.equals(element.getNamespaceURI())
                    && localName.equals(element.getLocalName())) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static String text(Element element) {
        String text = element.getTextContent();
        if (text == null) {
            return null;
        }
        text = text.strip();
        return text.isEmpty() ? null : text;
    }

    private static Document document(String xml) throws IOException {
        try {
            DocumentBuilder builder = factory().newDocumentBuilder();
            builder.setErrorHandler(FAIL_FAST);
            return builder.parse(new InputSource(new StringReader(xml == null ? "" : xml)));
        } catch (SAXException e) {
            throw new IOException("not a well-formed OAI-PMH response: " + e.getMessage(), e);
        } catch (ParserConfigurationException e) {
            throw new IllegalStateException("The JDK XML parser cannot be locked down", e);
        }
    }

    private static DocumentBuilderFactory factory() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory;
    }

    /** Fails on the first error instead of the JDK default of also printing it to stderr. */
    private static final ErrorHandler FAIL_FAST = new ErrorHandler() {
        @Override
        public void warning(SAXParseException e) {
            // a warning does not stop the parse
        }

        @Override
        public void error(SAXParseException e) throws SAXException {
            throw e;
        }

        @Override
        public void fatalError(SAXParseException e) throws SAXException {
            throw e;
        }
    };
}
