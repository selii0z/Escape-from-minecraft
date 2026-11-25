@echo off
setlocal
cd /d "%~dp0"
set "LOG=%~dp0build-diagnostics.txt"
if not exist "gradlew.bat" goto missing
if not exist "gradle\wrapper\gradle-wrapper.jar" goto missing
echo EFM local build > "%LOG%"
echo Date: %DATE% %TIME% >> "%LOG%"
set "JDK="
if defined JAVA_HOME call :checkjdk "%JAVA_HOME%"
if not defined JDK for /f "delims=" %%I in ('where javac 2^>nul') do if not defined JDK call :checkjdk "%%~dpI.."
if not defined JDK for /d %%I in ("%ProgramFiles%\Eclipse Adoptium\jdk-21*" "%ProgramFiles%\Java\jdk-21*" "%ProgramFiles%\Microsoft\jdk-21*" "%ProgramFiles%\Amazon Corretto\jdk21*" "%ProgramFiles%\Zulu\zulu-21*") do if not defined JDK call :checkjdk "%%~fI"
if not defined JDK goto nojava
set "JAVA_HOME=%JDK%"
set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JDK: "%JAVA_HOME%" >> "%LOG%"
"%JAVA_HOME%\bin\java.exe" -version >> "%LOG%" 2>&1
"%JAVA_HOME%\bin\javac.exe" -version >> "%LOG%" 2>&1
echo Compilation EFM avec Java 21
echo Journal : "%LOG%"
call gradlew.bat --no-daemon build --stacktrace --console=plain >> "%LOG%" 2>&1
set "RESULT=%ERRORLEVEL%"
echo.
if not "%RESULT%"=="0" (
    echo Echec de compilation. Code %RESULT%.
    echo Envoie le fichier build-diagnostics.txt.
) else (
    echo Compilation reussie.
    dir /b "build\libs\*.jar"
    echo Le fichier JAR se trouve dans build\libs.
)
pause
exit /b %RESULT%
:checkjdk
if not exist "%~1\bin\javac.exe" exit /b 0
if not exist "%~1\bin\java.exe" exit /b 0
"%~1\bin\javac.exe" -version 2>&1 | findstr /b /l /c:"javac 21." /c:"javac 21 " /c:"javac 21" >nul
if errorlevel 1 exit /b 0
for %%I in ("%~1") do set "JDK=%%~fI"
exit /b 0
:nojava
echo Aucun JDK 21 detecte. >> "%LOG%"
where java >> "%LOG%" 2>&1
java -version >> "%LOG%" 2>&1
echo Installe un JDK 21 complet ou indique son dossier dans JAVA_HOME.
echo Site officiel : https://adoptium.net/temurin/releases/?version=21
pause
exit /b 2
:missing
echo Lance ce fichier depuis le dossier source EFM extrait.
pause
exit /b 1
