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
        options.addOption(new Option("e", "aggregate_record_element", true, "name of element to split into document"));
        options.addOption(new Option("n", "aggregate_record_namespace", true, "namespace of element to split into document"));
        options.addOption(new Option("o", "output_directory_path", true, "target directory for split documents"));
        options.addOption(new Option("d", "aggregate_depth", true, "depth below root to split into documents"));

        CommandLine line = null;
        try {
            line = parser.parse( options, args );
        }
        catch( ParseException exp ) {
            System.err.println( "Parsing failed.  Reason: " + exp.getMessage() );
            return;
        }

        XMLSplitter splitter = new XMLSplitter();

        splitter.setInputFilePath(line.getOptionValue("i"));
        splitter.setAggregateRecordNamespace(line.getOptionValue("n"));
        splitter.setAggregateRecordElement(line.getOptionValue("e"));
        splitter.setOutputDirectoryPath(line.getOptionValue("o"));
        splitter.setAggregateDepth(line.getOptionValue("d"));

        splitter.split();
    }

    private String inputFilePath;
    private String aggregateRecordElement;
    private String aggregateRecordNamespace;
    private String outputDirectoryPath;
    private int aggregateDepth = -1;

    private boolean hasAggregateRecordQName = false;
    private boolean hasAggregateDepth = false;
    private QName aggregateRecordQName = null;

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
    private void constructQName() {
        if (this.aggregateRecordElement != null && this.aggregateRecordElement.length() > 0) {
            String ns = this.aggregateRecordNamespace;
            if (ns != null && ns.length() > 0) {
                this.aggregateRecordQName = new QName(this.aggregateRecordElement, this.aggregateRecordNamespace);
            }
            else {
                this.aggregateRecordQName = new QName(this.aggregateRecordElement);
            }
            this.hasAggregateRecordQName = true;
        }
        else {
            this.hasAggregateRecordQName = false;
            this.aggregateRecordQName = null;
        }
    }

    private void checkParams()
    {
        constructQName();

        if (!this.hasAggregateRecordQName && !this.hasAggregateDepth) {
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

                if (this.hasAggregateRecordQName &&
                        !xmlEvent.asStartElement().getName().equals(this.aggregateRecordQName)) {
                    continue;
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