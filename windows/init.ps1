# Windows PowerShell script template to create shortcuts for the DirectoryBookmarks utility

function directoryBookmarks { java $HOME\bin\DirectoryBookmarks.java $args }
function cdf { Set-Location -Path $(directoryBookmarks -r $args) }
function ldf { directoryBookmarks -r $args }
function sdf { directoryBookmarks -s $PWD.path $args }
