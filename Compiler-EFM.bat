@echo off
setlocal
cd /d "%~dp0"
set "LOG=%~dp0build-diagnostics.txt"
if not exist "gradlew.bat" goto missing
if not exist "gradle\wrapper\gradle-wrapper.jar" goto missing
echo Compilation EFM - Java 21 requis
echo EFM local build > "%LOG%"
where java >> "%LOG%" 2>&1
java -version >> "%LOG%" 2>&1
echo Compilation en cours - journal : "%LOG%"
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
:missing
echo Lance ce fichier depuis le dossier source EFM extrait.
pause
exit /b 1
