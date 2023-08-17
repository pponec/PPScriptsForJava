# Windows PowerShell script to create a shortcuts for the DirectoryBookmarks utilities

function directoryBookmarks { java $HOME\bin\DirectoryBookmarks.java $args }
# function directoryBookmarks { java -jar $HOME\bin\DirectoryBookmarks.jar $args }
function cdf { Set-Location -Path $(directoryBookmarks r $args) }
function sdf { directoryBookmarks s $PWD.path $args }
function ldf { directoryBookmarks r $args }
