#! /bin/bash
#./gradlew build shadowJar
java -Dlog4j.configurationFile=log4j2.properties -cp .:libs/mysql.jar:build/libs/dumont-jdbc-fat-jar.jar com.viglet.dumont.tool.jdbc.JDBCImportTool "$@"

