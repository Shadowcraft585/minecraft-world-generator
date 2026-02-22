package io.github.Shadowcraft585.worldGenerator;

import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.JDOMException;
import org.jdom2.input.SAXBuilder;
import org.jdom2.output.XMLOutputter;

import java.io.File;
import java.io.IOException;

public class XMLReader {

    public static String getVariable(String parentName, String variableName) {
        Document doc;

        File f = new File("config.xml");

        try {
            SAXBuilder builder = new SAXBuilder();
            doc = builder.build(f);

            XMLOutputter fmt = new XMLOutputter();

            Element root = doc.getRootElement();
            if (root == null) return null;

            Element parent = root.getChild(parentName);
            if (parent == null) return null;

            Element child = parent.getChild(variableName);
            if (child == null) return null;

            return child.getText();
        } catch (JDOMException | IOException e) {
            e.printStackTrace();
            return null;
        }
    }
}