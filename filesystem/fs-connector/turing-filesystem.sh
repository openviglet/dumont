#! /bin/bash
#./gradlew build shadowJar
java -Dlog4j.configurationFile=log4j2.properties -cp .:build/libs/dumont-filesystem-fat-jar.jar com.viglet.dumont.tool.filesystem.DumFSImportTool "$@"

