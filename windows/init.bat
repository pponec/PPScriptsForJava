@echo off
rem Command Prompt configuratino script for the directoryBookmarks
rem REGEDIT:
rem [Computer\HKEY_LOCAL_MACHINE\SOFTWARE\Microsoft\Command Processor]
rem "Autorun"="%USERPROFILE%\bin\init.bat"

rem Windows CMD script template to create shortcuts for the DirectoryBookmarks utility
doskey directoryBookmarks="C:\Program Files\Amazon Corretto\jdk17.0.9_8\bin\java" --limit-modules java.base,java.net.http,jdk.compiler,jdk.crypto.ec -jar "%USERPROFILE%\bin\DirectoryBookmarks.jar"
doskey cdf=for /f "tokens=*" %%a in ('java -jar "%USERPROFILE%\bin\DirectoryBookmarks.jar" -l $1') do cd "%%a"
doskey ldf=java -jar "%USERPROFILE%\bin\DirectoryBookmarks.jar" -l $*
doskey sdf=java -jar "%USERPROFILE%\bin\DirectoryBookmarks.jar" -s . $*

rem Linux compatibility statements
doskey l=dir /o-n /tc $*
doskey ls=dir /o-n /tc $*
doskey lt=dir /a-d /o-d /tc $*
doskey cat=type $*
doskey mp=notepad $*
doskey cp=copy $*
doskey rm=delete $*
doskey mv=rename $*
doskey history=doskey /history
doskey mkd=mkdir $* ^&^& cd $*



