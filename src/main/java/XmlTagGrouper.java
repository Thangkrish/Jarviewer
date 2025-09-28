import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class XmlTagGrouper {

    public static void main(String[] args) {
        String inputFile = "/Users/thanga-10113/Work/PROJECTS/Access_Management/Build/dad/AdventNet/Sas/tomcat/webapps/ROOT/WEB-INF/conf/adsf/appfw/applications/core/event/APFApplicationEvents.xml";
        String outputFile = "grouped_by_tag.txt";

        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new File(inputFile));
            Document newDoc = dBuilder.newDocument(); // Helper doc to create new elements

            NodeList parentNodeList = doc.getElementsByTagName("APFEventsVsActions");

            try (BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile))) {
                for (int i = 0; i < parentNodeList.getLength(); i++) {
                    Element parentElement = (Element) parentNodeList.item(i);
                    List<Element> childrenToModify = getTargetChildren(parentElement);

                    if (!childrenToModify.isEmpty()) {
                        // Write the opening splitter tag
                        writer.write("<InputParams>");
                        writer.newLine();

                        for (Element oldChildElement : childrenToModify) {
                            String mappingId = parentElement.getAttribute("MAPPING_ID");
                            Element newElement = createModifiedElement(newDoc, oldChildElement, mappingId);
                            String nodeAsString = elementToString(newElement);
                            writer.write("    " + nodeAsString); // Indent for readability
                            writer.newLine();
                        }

                        // Write the closing splitter tag
                        writer.write("</InputParams>");
                        writer.newLine();
                        writer.newLine(); // Add a blank line between blocks
                    }
                }
            }

            System.out.println("✅ Grouped extraction complete. Output saved to " + outputFile);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<Element> getTargetChildren(Element parentElement) {
        List<Element> targetChildren = new ArrayList<>();
        NodeList childNodes = parentElement.getChildNodes();
        for (int i = 0; i < childNodes.getLength(); i++) {
            Node node = childNodes.item(i);
            if (node.getNodeType() == Node.ELEMENT_NODE && "APFEventsVsActionInputParams".equals(node.getNodeName())) {
                targetChildren.add((Element) node);
            }
        }
        return targetChildren;
    }

    private static Element createModifiedElement(Document doc, Element oldElement, String mappingId) {
        Element newElement = doc.createElement("APFEventAndActionInputParams");
        newElement.setAttribute("EVENT_ACTION_MAPPING_ID", mappingId);

        NamedNodeMap attributes = oldElement.getAttributes();
        for (int i = 0; i < attributes.getLength(); i++) {
            Node attr = attributes.item(i);
            if ("EVENT_INPUT_PARAM_ID".equals(attr.getNodeName())) {
                newElement.setAttribute("INPUT_PARAM_ID", attr.getNodeValue());
            } else {
                newElement.setAttribute(attr.getNodeName(), attr.getNodeValue());
            }
        }
        return newElement;
    }

    private static String elementToString(Element element) throws Exception {
        TransformerFactory tf = TransformerFactory.newInstance();
        Transformer transformer = tf.newTransformer();
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
        transformer.setOutputProperty(OutputKeys.INDENT, "no");

        StringWriter writer = new StringWriter();
        transformer.transform(new DOMSource(element), new StreamResult(writer));
        return writer.getBuffer().toString();
    }

    /**
     * Process XML from a string input and return grouped XML tags as a string
     *
     * @param xmlContent The XML content as a string
     * @return The grouped XML tags as a string
     */
    public static String processXmlFromString(String xmlContent) {
        try {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(new ByteArrayInputStream(xmlContent.getBytes(StandardCharsets.UTF_8)));
            Document newDoc = dBuilder.newDocument(); // Helper doc to create new elements

            NodeList parentNodeList = doc.getElementsByTagName("APFEventsVsActions");
            StringWriter stringWriter = new StringWriter();

            try (BufferedWriter writer = new BufferedWriter(stringWriter)) {
                for (int i = 0; i < parentNodeList.getLength(); i++) {
                    Element parentElement = (Element) parentNodeList.item(i);
                    List<Element> childrenToModify = getTargetChildren(parentElement);

                    if (!childrenToModify.isEmpty()) {
                        // Write the opening splitter tag
                        writer.write("<InputParams>");
                        writer.newLine();

                        for (Element oldChildElement : childrenToModify) {
                            String mappingId = parentElement.getAttribute("MAPPING_ID");
                            Element newElement = createModifiedElement(newDoc, oldChildElement, mappingId);
                            String nodeAsString = elementToString(newElement);
                            writer.write("    " + nodeAsString); // Indent for readability
                            writer.newLine();
                        }

                        // Write the closing splitter tag
                        writer.write("</InputParams>");
                        writer.newLine();
                        writer.newLine(); // Add a blank line between blocks
                    }
                }
            }

            return stringWriter.toString();
        } catch (Exception e) {
            e.printStackTrace();
            return "Error processing XML: " + e.getMessage();
        }
    }
}