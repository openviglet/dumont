@ECHO OFF
java -Dloader.path=%~dp0libs -Ddumont.url=http://localhost:2700 -Ddumont.apiKey=968620e286c3483b829642b7f -Ddumont.connector.plugin=com.viglet.dumont.connector.plugin.webcrawler.DumWCPlugin -jar dumont-connector.jar
