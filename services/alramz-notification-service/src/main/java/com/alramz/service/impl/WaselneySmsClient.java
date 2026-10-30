package com.alramz.service.impl;

import com.alramz.config.SmsProperties;
import com.alramz.exception.SmsException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.util.UriComponentsBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.io.StringReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Waselney SMS API: send (GET with credentials in the query string, as the provider requires) and the XML
 * delivery report. Every query value is a URI variable, so it is strictly encoded. Never logs the URI.
 */
@Slf4j
@Component
public class WaselneySmsClient {

    private static final String PROVIDER = "WASELNEY";
    private static final String SUCCESS = "1701";
    private static final String NO_RECORD = "There is no record(s) found";

    private final SmsProperties.Waselney config;
    private final RestClient restClient;

    @Autowired
    public WaselneySmsClient(final SmsProperties properties) {
        this(properties, SmsServiceImpl.restClientBuilder(properties));
    }

    WaselneySmsClient(final SmsProperties properties, final RestClient.Builder builder) {
        this.config = properties.waselney();
        this.restClient = builder.build();
    }

    /** Returns the provider message id, or throws SmsException SMS009 (rejected) / SMS010 (unreachable). */
    public String send(final String number, final String message) {
        final URI uri = UriComponentsBuilder.fromUriString(config.sendUrl())
                .queryParam("username", "{username}")
                .queryParam("password", "{password}")
                .queryParam("type", "2")
                .queryParam("dlr", "0")
                .queryParam("source", "{source}")
                .queryParam("destination", "{destination}")
                .queryParam("message", "{message}")
                .encode()
                .buildAndExpand(config.username(), config.password(), SmsProperties.SENDER, number, encode(message))
                .toUri();
        final String response;
        try {
            response = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw SmsServiceImpl.providerFailure(PROVIDER, e);
        }
        final String[] tokens = response == null ? new String[0] : response.strip().split("\\|");
        if (tokens.length < 2 || !SUCCESS.equals(tokens[0]) || tokens[1].isBlank()) {
            final String code = SmsServiceImpl.providerCode(tokens.length == 0 ? null : tokens[0]);
            log.warn("SMS provider rejected provider={} resultCode={}", PROVIDER, code);
            throw new SmsException("SMS009", "SMS provider rejected " + PROVIDER + " " + code);
        }
        return tokens[1];
    }

    /** Delivery report rows (Report child name -> text), or SMS008 when the provider has none. */
    public List<Map<String, String>> report(final String messageId) {
        final URI uri = UriComponentsBuilder.fromUriString(config.reportUrl())
                .queryParam("uname", "{uname}")
                .queryParam("pwd", "{pwd}")
                .queryParam("usertype", "0")
                .queryParam("id", "{id}")
                .encode()
                .buildAndExpand(config.username(), config.password(), messageId)
                .toUri();
        final String xml;
        try {
            xml = restClient.get().uri(uri).retrieve().body(String.class);
        } catch (RestClientException e) {
            throw SmsServiceImpl.providerFailure(PROVIDER, e);
        }
        if (xml == null || xml.isBlank() || xml.contains(NO_RECORD)) {
            throw notFound();
        }
        final List<Map<String, String>> reports = parse(xml);
        if (reports.isEmpty()) {
            throw notFound();
        }
        return reports;
    }

    /** UCS-2 hex: each UTF-16 unit as 4 lowercase hex digits. */
    static String encode(final String message) {
        return message.chars().mapToObj(c -> String.format("%04x", c)).collect(Collectors.joining());
    }

    private static List<Map<String, String>> parse(final String xml) {
        final Document document;
        try {
            final DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            final DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setErrorHandler(SILENT);
            document = builder.parse(new InputSource(new StringReader(xml)));
        } catch (ParserConfigurationException | SAXException | IOException e) {
            log.warn("SMS provider report unparseable provider={} error={}", PROVIDER, e.getClass().getSimpleName());
            throw new SmsException("SMS009", "SMS provider rejected " + PROVIDER + " unparseable report");
        }
        final List<Map<String, String>> reports = new ArrayList<>();
        for (Element outer : children(document.getDocumentElement(), "DeliveryReport")) {
            for (Element report : children(outer, "Report")) {
                final Map<String, String> fields = new LinkedHashMap<>();
                final NamedNodeMap attributes = report.getAttributes();
                for (int i = 0; i < attributes.getLength(); i++) {
                    fields.put(attributes.item(i).getNodeName(), attributes.item(i).getNodeValue());
                }
                for (Element field : children(report, null)) {
                    fields.put(field.getTagName(), field.getTextContent());
                }
                reports.add(fields);
            }
        }
        return reports;
    }

    private static List<Element> children(final Element parent, final String name) {
        final List<Element> elements = new ArrayList<>();
        for (Node node = parent.getFirstChild(); node != null; node = node.getNextSibling()) {
            if (node instanceof Element element && (name == null || name.equals(element.getTagName()))) {
                elements.add(element);
            }
        }
        return elements;
    }

    private static SmsException notFound() {
        return new SmsException("SMS008", "No SMS record found");
    }

    /** Keeps the default handler from printing parser errors (which can echo document content) to stderr. */
    private static final ErrorHandler SILENT = new ErrorHandler() {
        @Override
        public void warning(final SAXParseException exception) {
            // ignored
        }

        @Override
        public void error(final SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(final SAXParseException exception) throws SAXException {
            throw exception;
        }
    };
}
