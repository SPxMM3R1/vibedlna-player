package cl.streambox.tv;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

final class DlnaXml {
    private DlnaXml() {}

    static Document parse(String xml) throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        setFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
        setFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
        setFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        return factory.newDocumentBuilder().parse(
                new InputSource(new StringReader(xml))
        );
    }

    static List<Element> descendants(Node root, String localName) {
        List<Element> result = new ArrayList<>();
        collect(root, localName, result);
        return result;
    }

    static Element firstDescendant(Node root, String localName) {
        NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element && localName.equals(name(child))) {
                return (Element) child;
            }
            Element nested = firstDescendant(child, localName);
            if (nested != null) return nested;
        }
        return null;
    }

    static String firstText(Node root, String localName) {
        Element element = firstDescendant(root, localName);
        return element == null ? "" : element.getTextContent().trim();
    }

    static String name(Node node) {
        String local = node.getLocalName();
        if (local != null) return local;
        String nodeName = node.getNodeName();
        int separator = nodeName.indexOf(':');
        return separator >= 0 ? nodeName.substring(separator + 1) : nodeName;
    }

    private static void collect(Node root, String localName, List<Element> result) {
        NodeList children = root.getChildNodes();
        for (int index = 0; index < children.getLength(); index++) {
            Node child = children.item(index);
            if (child instanceof Element) {
                if (localName.equals(name(child))) result.add((Element) child);
                collect(child, localName, result);
            }
        }
    }

    private static void setFeature(
            DocumentBuilderFactory factory,
            String name,
            boolean value
    ) {
        try {
            factory.setFeature(name, value);
        } catch (Exception ignored) {
            // Algunos parsers Android no exponen todas las opciones.
        }
    }
}
