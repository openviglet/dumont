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
  │   ├── dumont-db.sh/.bat            Run the database CLI tool
  │   └── dumont-filesystem.sh/.bat    Run the filesystem CLI tool
  ├── connector/                        Connector engine
  │   ├── dumont-connector.jar          Pipeline engine (Spring Boot)
  │   ├── dumont-connector.properties   Configuration (edit this!)
  │   └── libs/                         Connector plugins
  │       ├── aem/
  │       │   └── aem-plugin.jar        AEM connector plugin
  │       └── webcrawler/
  │           └── web-crawler-plugin.jar Web Crawler connector plugin
  ├── db/                               Database connector
  │   └── dumont-db.jar                 Standalone CLI tool
  └── filesystem/                       Filesystem connector
      └── dumont-filesystem.jar         Standalone CLI tool

Quick Start
-----------
1. Edit connector/dumont-connector.properties with your Turing ES URL and API key.

2. Start the AEM connector (Linux/macOS):
     chmod +x bin/*.sh
     ./bin/dumont-aem.sh

   Start the AEM connector (Windows):
     bin\dumont-aem.bat

   Or start the Web Crawler connector (Linux/macOS):
     ./bin/dumont-webcrawler.sh

   Start the Web Crawler connector (Windows):
     bin\dumont-webcrawler.bat

3. Open http://localhost:30130 in your browser.

Running Both Connectors Simultaneously
---------------------------------------
Only ONE plugin can be active per JVM instance. To run both AEM and Web Crawler
at the same time, start them on different ports:

  # AEM on port 30130 (default)
  ./bin/dumont-aem.sh

  # Web Crawler on port 30131
  ./bin/dumont-webcrawler.sh -- --server.port=30131

Standalone CLI Tools
--------------------
Database:
  ./bin/dumont-db.sh \
    --server http://localhost:30130 \
    --api-key YOUR_API_KEY \
    --driver org.mariadb.jdbc.Driver \
    --connect "jdbc:mariadb://localhost:3306/mydb" \
    --query "SELECT id, title, body FROM articles" \
    --site MySite --locale en_US

Filesystem:
  ./bin/dumont-filesystem.sh \
    --source-dir /path/to/documents \
    --server http://localhost:30130 \
    --api-key YOUR_API_KEY \
    --site InternalDocs --locale en_US

Documentation
-------------
  https://viglet.org/dumont/
  https://github.com/openviglet/dumont
