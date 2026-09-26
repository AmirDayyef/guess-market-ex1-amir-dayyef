@echo off
setlocal

set "CLIENT_DIR=%~dp0"
java -cp "%CLIENT_DIR%guess-market-client-1.0.0.jar;%CLIENT_DIR%lib\*" com.guessmarket.client.ClientLauncher
set "EXIT_CODE=%ERRORLEVEL%"

if not "%EXIT_CODE%"=="0" (
    echo.
    echo Guess Market client could not start. Check Java 25 and start Tomcat with guess-market-server.war.
)

exit /b %EXIT_CODE%
