@ECHO OFF
call %~dp0env.cmd
java -Dloader.path=%~dp0libs ^
-Ddumont.url=%TURING_URL% ^
-Ddumont.apiKey=%TURING_API_KEY% ^
-Dspring.h2.console.enabled=true ^
-jar %~dp0dumont-connector.jar