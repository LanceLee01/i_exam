@echo off
set ADB=%ANDROID_HOME%\platform-tools\adb.exe
if not defined ANDROID_HOME (
    set ANDROID_HOME=C:\Users\lasal\AppData\Local\Android\Sdk
    set ADB=%ANDROID_HOME%\platform-tools\adb.exe
)

set TS=%date:~0,4%%date:~5,2%%date:~8,2%_%time:~0,2%%time:~3,2%%time:~6,2%
set TS=%TS: =0%
set OUT=debug\solvepipeline_%TS%.txt

echo === Pulling SolvePipeline logs to %OUT% ... ===
%ADB% logcat -b main -d -s SolvePipeline:* ExamHelperL1:* > %OUT% 2>&1
echo === Done: %OUT% ===
echo.

echo === Key lines (stem/rescue/solve/L1 matched) ===
findstr /I "solve: stem empty rescue L1 matched Q34" %OUT%
