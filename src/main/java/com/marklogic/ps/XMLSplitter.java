package com.marklogic.ps;

import java.io.*;
import java.util.*;

import javax.xml.namespace.QName;
import javax.xml.stream.*;
import javax.xml.stream.events.*;

import org.apache.commons.cli.*;

public class XMLSplitter
{
    public static void main(String[] args) throws Exception
    {
        // create the command line parser
        CommandLineParser parser = new DefaultParser();

        // create the Options
        Options options = new Options();
        options.addOption(new Option("i", "input_file_path", true, "path of file to split"));
        options.addOption(new Option("e", "aggregate_record_element", true, "name of element to split into documenta"));
        options.addOption(new Option("n", "aggregate_record_namespace", true, "namespace of element to split into documents"));
        options.addOption(new Option("o", "output_directory_path", true, "target directory for split documents"));
        options.addOption(new Option("d", "aggregate_depth", true, "depth below root to split into documents"));
        options.addOption(new Option("ne", "namespace_element_list", true, "comma-sep namespace element list"));
        options.addOption(new Option("h", "help", false, "print help message"));

        CommandLine line = null;
        try {
            line = parser.parse( options, args );
        }
        catch( ParseException exp ) {
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
            return;
        }

        if (line.hasOption("h")) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("xml-splitter", options);
            return;
        }

        XMLSplitter splitter = new XMLSplitter();

        splitter.setInputFilePath(line.getOptionValue("i"));
        splitter.setAggregateRecordNamespace(line.getOptionValue("n"));
        splitter.setAggregateRecordElement(line.getOptionValue("e"));
        splitter.setOutputDirectoryPath(line.getOptionValue("o"));
        splitter.setAggregateDepth(line.getOptionValue("d"));
        splitter.setAggregateRecordNamesList(line.getOptionValue("ne"));

        splitter.split();
    }

    private String inputFilePath;
    private String aggregateRecordElement;
    private String aggregateRecordNamespace;
    private String aggregateRecordNamesList;
    private String outputDirectoryPath;
    private int aggregateDepth = -1;

    private boolean hasAggregateDepth = false;
    private ArrayList<QName> aggregateRecordQNameList = null;

    public void setInputFilePath(String path) {
        if (path == null) {
            this.inputFilePath = "";
        } else {
            this.inputFilePath = path;
        }
    }

    public void setAggregateRecordElement(String elem) {
        this.aggregateRecordElement = elem;
    }

    public void setAggregateRecordNamespace(String ns) {
        this.aggregateRecordNamespace = ns;
    }

    public void setAggregateRecordNamesList(String str) {
        this.aggregateRecordNamesList = str;
    }

    public void setOutputDirectoryPath(String path) {
        if (path == null) {
            this.outputDirectoryPath = "";
        } else {
            this.outputDirectoryPath = path;
        }
    }

    public void setAggregateDepth(String depth) {
        if (depth == null) {
            this.aggregateDepth = -1;
            this.hasAggregateDepth = false;
        } else {
            this.aggregateDepth = Integer.parseInt(depth);
            this.hasAggregateDepth = true;
        }
    }

    private void constructQNames()
    {
        this.aggregateRecordQNameList = new ArrayList<QName>();

        if (this.aggregateRecordElement != null && this.aggregateRecordElement.length() > 0) {
            String ns = this.aggregateRecordNamespace;
            if (ns != null && ns.length() > 0) {
                this.aggregateRecordQNameList.add(new QName(this.aggregateRecordElement, this.aggregateRecordNamespace));
            }
            else {
                this.aggregateRecordQNameList.add(new QName(this.aggregateRecordElement));
            }
        }

        if (this.aggregateRecordNamesList != null && this.aggregateRecordNamesList.length() > 0) {
            String[] parts = this.aggregateRecordNamesList.split(",");
            for (int i = 0; i < parts.length; i += 2) {
                String ns = parts[i].trim();
                String localName = parts[i + 1].trim();
                if (ns.length() > 0) {
                    this.aggregateRecordQNameList.add(new QName(ns, localName));
                }
                else {
                    this.aggregateRecordQNameList.add(new QName(localName));
                }
            }
        }
    }

    private void checkParams()
    {
        constructQNames();

        if (this.aggregateRecordQNameList.size() == 0 && !this.hasAggregateDepth) {
            this.hasAggregateDepth = true;
            this.aggregateDepth = 1;
        }
    }

    private void split() throws Exception
    {
        checkParams();

        XMLEventFactory xef = XMLEventFactory.newFactory();
        XMLInputFactory xif = XMLInputFactory.newInstance();
        XMLEventReader xer = xif.createXMLEventReader(new FileReader(this.inputFilePath));

        // skip past the root element
        StartElement rootStartElement = xer.nextTag().asStartElement();

        StartDocument startDocument = xef.createStartDocument();
        EndDocument endDocument = xef.createEndDocument();

        int depth = 1;

        XMLOutputFactory xof = XMLOutputFactory.newFactory();
        while (xer.hasNext() && !xer.peek().isEndDocument())
        {
            // Set startDepth before the event. The check for the end depth is after the event.
            int startDepth = depth;

            XMLEvent xmlEvent = xer.nextEvent();
            if (xmlEvent.isStartElement())  depth++;
            else if (xmlEvent.isEndElement()) depth--;

            if (xmlEvent.isStartElement()) {
                if (this.hasAggregateDepth && this.aggregateDepth != depth - 1) {
                    continue;
                }

                if (this.aggregateRecordQNameList.size() > 0) {
                    QName thisName = xmlEvent.asStartElement().getName();
                    boolean hasMatch = false;
                    for (QName name : this.aggregateRecordQNameList) {
                        if (name.equals(thisName)) hasMatch = true;
                    }
                    if (!hasMatch) continue;
                }
            }
            else {
                continue;
            }

            StartElement startElement = xmlEvent.asStartElement();
            QName startName = startElement.getName();

            String outputFileName = UUID.randomUUID().toString() + ".xml";
            String outputFilePath = this.outputDirectoryPath +
                    (this.outputDirectoryPath.length() > 0 ? File.separator : "") +
                    outputFileName;

            // Create a file for the fragment, the name is derived from the value of the id attribute
            FileWriter fileWriter = new FileWriter(outputFilePath);

            // A StAX XMLEventWriter will be used to write the XML fragment
            XMLEventWriter xew = xof.createXMLEventWriter(fileWriter);
            xew.add(startDocument);
            xew.add(startElement);

            // Write the XMLEvents that we still need to parse from this fragment
            xmlEvent = xer.nextEvent();
            if (xmlEvent.isStartElement())  depth++;
            else if (xmlEvent.isEndElement()) depth--;

            while (xer.hasNext()) {
                if (xmlEvent.isEndElement()) {
                    QName endName = xmlEvent.asEndElement().getName();
                    // Depth check fixes bug in case where start element QName occurs again
                    // as a child of the start element
                    if (startName.equals(endName) && startDepth == depth) {
                        //sSystem.out.println("Depth=" + Integer.toString(depth) + ", startDepth = " + Integer.toString(startDepth));
                        break;
                    }
                }

                xew.add(xmlEvent);

                xmlEvent = xer.nextEvent();
                if (xmlEvent.isStartElement())  depth++;
                else if (xmlEvent.isEndElement()) depth--;
            }
            xew.add(xmlEvent);

            // Close everything we opened
            xew.add(endDocument);
            fileWriter.close();
        }
    }
}