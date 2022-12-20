package org.pentaho.di.sdk.samples.steps.ntmlclient.utils;

import org.apache.commons.lang.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class XmlUtils {
    private XmlUtils() {
        throw new AssertionError();
    }

    public static String unicode2String( String unicode ) {
        if ( StringUtils.isBlank( unicode ) ) {
            return null;
        }

        if ( !unicode.contains( "_x") ) {
            return unicode.replace("d:", "");
        }

        unicode = unicode.replace( "_x", "\\u\\" );
        StringBuilder sb = new StringBuilder();
        String[] label = unicode.split("_");
        for ( String s : label ) {
            String[] lecture = s.split("\\\\u");
            for (String value : lecture) {
                if (value.contains("\\")) {
                    value = value.replace("\\", "");
                    if (!value.equals("")) {
                        sb.append((char) Integer.valueOf(value, 16).intValue());
                    }
                } else {
                    sb.append(value);
                }
            }
        }

        String result = sb.toString();
        if (result.contains("OData")) {
            result = result.replace("OData", "");
        }
        return result.replace("d:", "");
    }

    public static Document xmlParse( byte[] bytes ) throws ParserConfigurationException, IOException, SAXException {
        InputStream is = new ByteArrayInputStream( bytes );
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature( "http://apache.org/xml/features/disallow-doctype-decl", true );
        return factory.newDocumentBuilder().parse( is );
    }

    public static List<String> getChildrenTab( NodeList nodeList ) {
        NodeList child = nodeList.item(0).getChildNodes();
        List<String> result = new ArrayList<>();
        for (int i = 0; i < child.getLength(); i++ ) {
            Node node = child.item( i );
            if ( node.getNodeName().equalsIgnoreCase("#text" ) ) {
                continue;
            }
            recursiveNodeTag(node, false, null, result);
        }
        return result;
    }

    private static void recursiveNodeTag(Node node, Boolean inlineNode, String title, List<String> result) {
        if ( node.getChildNodes().getLength()>0 ) {
            for( int f = 0; f < node.getChildNodes().getLength(); f++ ) {
                Node subNode = node.getChildNodes().item( f );
                if ( subNode.getNodeName().equalsIgnoreCase("#text" ) ) {
                    continue;
                }

                if (subNode.getNodeName().contains("properties")) {
                    NodeList secondNode = subNode.getChildNodes();
                    getSubNodeTag(secondNode, inlineNode, title, result);
                }

                if(subNode.getNodeName().contains("inline")) {
                    NamedNodeMap attributes = node.getAttributes();
                    Node titleNode = attributes.getNamedItem("title");
                    title = titleNode.getTextContent();
                    inlineNode = true;
                }
                recursiveNodeTag(subNode, inlineNode, title, result);
            }
        }
    }

    private static void getSubNodeTag(NodeList nodeList, Boolean inlineNode, String title, List<String> result) {
        for (int i = 0; i < nodeList.getLength(); i++ ) {
            Node subNode = nodeList.item(i);
            if ( subNode.getNodeName().equalsIgnoreCase("#text" ) ) {
                continue;
            }
            if (Boolean.TRUE.equals(inlineNode)) {
                result.add(title + ":" + unicode2String(subNode.getNodeName()));
            } else {
                result.add(unicode2String(subNode.getNodeName()));
            }

        }
    }

    public static void recursiveNodeVal(Node node, Boolean inlineNode, String title, Map<String, Object> result) {
        if ( node.getChildNodes().getLength()>0 ) {
            for( int f = 0; f < node.getChildNodes().getLength(); f++ ) {
                Node subNode = node.getChildNodes().item( f );
                if ( subNode.getNodeName().equalsIgnoreCase("#text" ) ) {
                    continue;
                }

                if (subNode.getNodeName().contains("properties")) {
                    NodeList secondNode = subNode.getChildNodes();
                    getSubNodeVal(secondNode, inlineNode, title, result);
                }

                if(subNode.getNodeName().contains("inline")) {
                    NamedNodeMap attributes = node.getAttributes();
                    Node titleNode = attributes.getNamedItem("title");
                    title = titleNode.getTextContent();
                    inlineNode = true;
                }
                recursiveNodeVal(subNode, inlineNode, title, result);
            }
        }
    }

    private static void getSubNodeVal(NodeList nodeList, boolean inlineNode, String title, Map<String, Object> result) {
        for (int i = 0; i < nodeList.getLength(); i++ ) {
            Node subNode = nodeList.item(i);
            if ( subNode.getNodeName().equalsIgnoreCase("#text" ) ) {
                continue;
            }
            if (inlineNode) {
                String key = title + ":" + unicode2String(subNode.getNodeName());
                result.put(key, unicode2String(subNode.getTextContent()));
            } else {
                result.put(unicode2String(subNode.getNodeName()), unicode2String(subNode.getTextContent()));
            }
        }
    }
}
