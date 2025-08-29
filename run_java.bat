@echo off
echo =======================================
echo AWS Bedrock 4-Agent Pipeline - Java
echo =======================================
echo.

REM Check if Java is installed (we need Java 17+)
java -version >nul 2>&1
if not errorlevel 1 (
    echo Java found in PATH:
    java -version
    set JAVA_CMD=java
) else (
    REM Check if Java 24 is installed in standard location
    "C:\Program Files\Java\jdk-24\bin\java.exe" -version >nul 2>&1
    if not errorlevel 1 (
        echo Java 24 found in standard location:
        "C:\Program Files\Java\jdk-24\bin\java.exe" -version
        set "JAVA_HOME=C:\Program Files\Java\jdk-24"
        set "PATH=%JAVA_HOME%\bin;%PATH%"
        set JAVA_CMD=java
    ) else (
        echo ERROR: Java 17+ is not installed or not found
        echo Please install Java 17+ from https://adoptium.net/
        pause
        exit /b 1
    )
)

REM Check if Maven is installed
mvn -version >nul 2>&1
if not errorlevel 1 (
    echo.
    echo Maven found:
    mvn -version | findstr /r "Apache Maven"
    
    echo.
    echo Compiling Java project with Maven...
    mvn compile
    if errorlevel 1 (
        echo ERROR: Maven compilation failed
        pause
        exit /b 1
    )
    
    echo.
    echo Running Java implementation with Maven...
    echo.
    mvn exec:java
    
    echo.
    echo Java implementation completed.
    pause
    exit /b 0
)

REM Check if Maven Daemon (mvnd) is available
"C:\Program Files\apache\maven\maven-mvnd-1.0.2-windows-amd64\bin\mvnd.exe" -version >nul 2>&1
if not errorlevel 1 (
    echo.
    echo Maven Daemon found
    
    echo.
    echo Setting JAVA_HOME for Maven Daemon...
    set "JAVA_HOME=C:\Program Files\Java\jdk-24"
    echo Using JAVA_HOME: %JAVA_HOME%
    
    echo.
    echo Compiling Java project with Maven Daemon...
    "C:\Program Files\apache\maven\maven-mvnd-1.0.2-windows-amd64\bin\mvnd.exe" compile
    if errorlevel 1 (
        echo ERROR: Maven Daemon compilation failed
        pause
        exit /b 1
    )
    
    echo.
    echo Running Java implementation with Maven Daemon...
    echo.
    "C:\Program Files\apache\maven\maven-mvnd-1.0.2-windows-amd64\bin\mvnd.exe" exec:java
    
    echo.
    echo Java implementation completed.
    pause
    exit /b 0
)

echo ERROR: Neither Maven nor Maven Daemon found
echo Please install Maven or ensure Maven Daemon is available
pause
exit /b 1