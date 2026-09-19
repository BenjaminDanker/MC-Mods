@echo off
pushd "%~dp0"
call "..\Border-Lock\gradlew.bat" %*
set "PRUNER_EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %PRUNER_EXIT_CODE%
