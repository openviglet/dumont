@ECHO OFF
call %~dp0env.cmd
java -Dloader.path=%~dp0libs ^
-Dturing.url=%TURING_URL% ^
-Dturing.apiKey=%TURING_API_KEY% ^
-Dspring.h2.console.enabled=true ^
-Dlogging.config=classpath:logback-spring.xml ^
-Dlogging.level.com.viglet=DEBUG ^
-Dlogging.level.org.springframework=DEBUG ^
-Dlogging.level.org.apache=DEBUG ^
-jar %~dp0dumont-connector.jar