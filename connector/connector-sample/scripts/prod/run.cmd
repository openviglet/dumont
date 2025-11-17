@ECHO OFF
java -Dloader.path=%~dp0libs -Ddumont.url=http://localhost:2700 -Ddumont.apiKey=968620e286c3483b829642b7f -jar dumont-connector.jar
