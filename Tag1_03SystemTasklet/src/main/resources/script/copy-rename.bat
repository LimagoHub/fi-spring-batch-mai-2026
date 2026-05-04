@echo off
set "inbox=..\inbox"
set "input=..\input"
for %%f in (%inbox%\*.csv) do (
    move "%%f" "%input%\%%~nf-data%%~xf"
)