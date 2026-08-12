@echo off
setlocal

set "APP_CP=%~dp0guess-market-console-1.0.0.jar;%~dp0guess-market-engine-1.0.0.jar;%~dp0guess-market-api-1.0.0.jar;%~dp0lib\*"
java -cp "%APP_CP%" com.guessmarket.console.GuessMarketApplication
set "APP_EXIT_CODE=%ERRORLEVEL%"

if not "%APP_EXIT_CODE%"=="0" (
    echo.
    echo Guess Market stopped with an error. Make sure Java 25 is installed and available on PATH.
)

exit /b %APP_EXIT_CODE%
