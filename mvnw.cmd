@ECHO OFF
SETLOCAL
IF "%JAVA_HOME%"=="" (
    IF EXIST "C:\Program Files\Android\Android Studio\jbr\bin\java.exe" (
        SET "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
    )
)
SET "WRAPPER_JAR=%~dp0.mvn\wrapper\maven-wrapper.jar"
IF NOT EXIST "%WRAPPER_JAR%" (
    powershell -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar' -OutFile '%WRAPPER_JAR%'"
)
"%JAVA_HOME%\bin\java.exe" "-Dmaven.multiModuleProjectDirectory=%~dp0." -cp "%WRAPPER_JAR%" org.apache.maven.wrapper.MavenWrapperMain %*
ENDLOCAL
