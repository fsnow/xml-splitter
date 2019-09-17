# xml-splitter
A Java StAX-based command line XML splitter.

    usage: java -jar xml-splitter-<VERSION>-all.jar  
     -d,--aggregate_depth <arg>              depth below root to split into documents  
     -e,--aggregate_record_element <arg>     name of element to split into documents  
     -h,--help                               print help message  
     -i,--input_file_path <arg>              path of file to split  
     -n,--aggregate_record_namespace <arg>   namespace of element to split into documents  
     -ne,--namespace_element_list <arg>      comma-sep namespace element list  
     -o,--output_directory_path <arg>        target directory for split documents  

There are two ways to select for elements to split into separate documents. Depth refers to the depth of the XML element relative to the root. Children of the root are depth 1, children of children of the root are depth 2, and so on.

You can also select by element QName. A QName is a combination of the namespace and local name of an element. For a single QName you can use the -e and, optionally, the -n argument to specify the local name and namespace. For more then one QName you can use the -ne argument with a comma-separated alternating sequence of namespace and local name, e.g.: http://<i></i>foo.com/ns1,name1,http://<i></i>foo.com/ns2,name2 .

You can combine the depth and QName arguments to select only elements at a particular depth AND matching certain QNames.
