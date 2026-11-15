@echo off
cd /d "%~dp0"
if not exist "build\gesture-lab" mkdir "build\gesture-lab"
javac -encoding UTF-8 -d "build\gesture-lab" src\main\java\dev\palimpsest\glyph\*.java src\test\java\dev\palimpsest\glyph\GestureLab.java
if errorlevel 1 (pause & exit /b 1)
java -cp "build\gesture-lab" dev.palimpsest.glyph.GestureLab
