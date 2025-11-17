@ECHO OFF
%JAVA_BIN% -classpath ".;%~dp0libs\db-sample.jar;%~dp0libs\dumont-db.jar" com.viglet.dumont.connector.db.DumDbImportTool %*
