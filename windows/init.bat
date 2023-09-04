REM Windows CMD script template to create shortcuts for the DirectoryBookmarks utility

doskey cdf=for /f "tokens=*" %a in ('java %USERPROFILE%\bin\DirectoryBookmarks.java -l $1') do cd "%a"
doskey ldf=java %USERPROFILE%\bin\DirectoryBookmarks.java -l $*
doskey sdf=java %USERPROFILE%\bin\DirectoryBookmarks.java -s . $*
