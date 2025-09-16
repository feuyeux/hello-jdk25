@echo off
set JAVA_HOME=D:\zoo\jdk-25
call mvn compile -q >nul 2>&1
"%JAVA_HOME%\bin\java" -cp target\classes com.example.app.App