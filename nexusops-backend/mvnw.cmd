@REM ----------------------------------------------------------------------------
@REM Maven Wrapper for Windows (NexusOps)
@REM ----------------------------------------------------------------------------
@echo off
setlocal

set "DIR=%~dp0"
set "WRAPPER_JAR=%DIR%.mvn\wrapper\maven-wrapper.jar"
set "WRAPPER_PROP=%DIR%.mvn\wrapper\maven-wrapper.properties"

if exist "%WRAPPER_JAR%" goto run_wrapper

echo Maven wrapper jar not found, bootstrapping...
powershell -NoProfile -ExecutionPolicy Bypass -Command "& { [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar', '%WRAPPER_JAR%') }"

if not exist "%WRAPPER_JAR%" (
    echo Error: Failed to download maven-wrapper.jar.
    exit /b 1
)

:run_wrapper
java -classpath "%WRAPPER_JAR%" -Dmaven.multiModuleProjectDirectory=. org.apache.maven.wrapper.MavenWrapperMain %*
exit /b %ERRORLEVEL%
