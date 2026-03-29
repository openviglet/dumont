@ECHO OFF
call %~dp0env.cmd
java -Dloader.path=%~dp0libs ^
-jar %~dp0dumont-connector.jar ^
--spring.config.additional-location="file:%~dp0dumont-connector.properties"