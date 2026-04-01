Viglet Dumont DEP - Distribution
=================================

Dumont DEP is an open-source data extraction platform that connects content
sources to search engines like Viglet Turing ES, Apache Solr, and Elasticsearch.

Prerequisites
-------------
- Java 21 or later (set JAVA_HOME and add to PATH)
- A running Turing ES instance with an API Token (optional, for Turing indexing)

Directory Structure
-------------------
  dumont-<version>/
  ├── bin/                              Start scripts
  │   ├── dumont-aem.sh/.bat           Start connector with AEM plugin
  │   ├── dumont-webcrawler.sh/.bat    Start connector with Web Crawler plugin
  │   └── dumont-db.sh/.bat            Start connector with Database plugin
  ├── connector/                        Connector engine
  │   ├── dumont-connector.jar          Pipeline engine (Spring Boot)
  │   ├── dumont-connector.properties   Configuration (edit this!)
  │   └── libs/                         Connector plugins
  │       ├── aem/                      AEM connector plugin
  │       ├── db/                       Database connector plugin
  │       ├── webcrawler/               Web Crawler connector plugin
  │       └── assets/                   Assets (filesystem) connector plugin

Quick Start
-----------
1. Edit connector/dumont-connector.properties with your Turing ES URL and API key.

2. Start a connector (Linux/macOS):
     chmod +x bin/*.sh
     ./bin/dumont-aem.sh

   Start a connector (Windows):
     bin\dumont-aem.bat

3. Open http://localhost:30130 in your browser.

Running Multiple Connectors Simultaneously
-------------------------------------------
Only ONE plugin can be active per JVM instance. To run multiple connectors
at the same time, start them on different ports:

  # AEM on port 30130 (default)
  ./bin/dumont-aem.sh

  # Web Crawler on port 30131
  ./bin/dumont-webcrawler.sh -- --server.port=30131

  # Database on port 30132
  ./bin/dumont-db.sh -- --server.port=30132

Documentation
-------------
  https://viglet.org/dumont/
  https://github.com/openviglet/dumont
