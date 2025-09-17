@echo off
setlocal ENABLEDELAYEDEXPANSION

REM ================= Configuration =================
REM Allow user override (if already set externally)
if not defined JAVA_HOME set "JAVA_HOME=D:\zoo\jdk-25"
set "MAIN_CLASS=org.feuyeux.jdk25.App"
set "CP=target\classes"

REM ================= Build Phase =================
REM Run Maven compile and capture output to a temp file
set "TMP_LOG=%TEMP%\mvn_build_hello_jdk25.log"
if exist "%TMP_LOG%" del "%TMP_LOG%" >nul 2>&1

echo [INFO] Compiling project...
call mvn compile -q 1>"%TMP_LOG%" 2>&1
set "MVN_ERRORLEVEL=%ERRORLEVEL%"

REM ================= Filter Warnings =================
REM We filter only the specific sun.misc.Unsafe related warnings.
REM findstr on Windows does not support '|' alternation inside one pattern the Unix way,
REM so we run multiple filters. If findstr finds the pattern, we skip printing that line.

for /f "usebackq delims=" %%L in ("%TMP_LOG%") do (
  set "LINE=%%L"
  echo !LINE! | findstr /i /c:"sun.misc.Unsafe" >nul && (
    REM skip this line
    goto :continueLoop
  )
  echo !LINE! | findstr /i /c:"staticFieldBase" >nul && goto :continueLoop
  echo !LINE! | findstr /i /c:"HiddenClassDefiner" >nul && goto :continueLoop
  echo !LINE!
  :continueLoop
)

REM ================= Result Handling =================
if not "%MVN_ERRORLEVEL%"=="0" (
  echo.
  echo [ERROR] Maven compile failed with exit code %MVN_ERRORLEVEL%.
  echo Full log saved at: %TMP_LOG%
  exit /b %MVN_ERRORLEVEL%
)

echo [INFO] Compile success.

REM ================= Run Application =================
if not exist "%CP%" (
  echo [ERROR] Classpath directory "%CP%" not found.
  exit /b 2
)

if not exist "%JAVA_HOME%\bin\java.exe" (
  echo [ERROR] java executable not found under JAVA_HOME=%JAVA_HOME%
  exit /b 3
)

echo [INFO] Running %MAIN_CLASS% with JAVA_HOME=%JAVA_HOME%
"%JAVA_HOME%\bin\java" -cp "%CP%" %MAIN_CLASS%
set "JAVA_RUN_ERROR=%ERRORLEVEL%"

if not "%JAVA_RUN_ERROR%"=="0" (
  echo [ERROR] Application exited with code %JAVA_RUN_ERROR%.
  exit /b %JAVA_RUN_ERROR%
)

echo [INFO] Done.
exit /b 0