@echo off
setlocal
cd /d "%~dp0"

where java >nul 2>&1
if errorlevel 1 (
    echo Java was not found. Install JDK 25 and make sure java is available on PATH.
    pause
    exit /b 1
)

java --module-path "lib" --add-modules javafx.controls --enable-native-access=javafx.graphics -cp "guess-market-javafx-1.0.0.jar;lib/*" com.guessmarket.ui.Launcher
if errorlevel 1 (
    echo.
    echo Guess Market could not start. Verify that JDK 25 is active and the lib directory is unchanged.
    pause
    exit /b 1
)

endlocal
