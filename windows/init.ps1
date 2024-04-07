# Shortcuts for DirectoryBookmarks v1.8.5 utilities - for the PowerShell:
function directoryBookmarks { & "java" --limit-modules java.base,java.net.http,jdk.compiler,jdk.crypto.ec -jar "$HOME/bin/DirectoryBookmarks.jar" $args }
function cdf { Set-Location -Path $(directoryBookmarks g $args) }
function sdf { directoryBookmarks s . @args }
function ldf { directoryBookmarks l $args }

# Linux compatibility statements
# Command history
function hist { Get-Content (Get-PSReadlineOption).HistorySavePath | Get-Unique }
# Plain list
function lp { Get-ChildItem -Name @args }
# List ordered by time
function lt { ls @args | sort LastWriteTime -Descending | Select -First 30 | Format-Table Mode, Length, @{n='LastWriteTime';e={$_.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")}}, @{N='Name';E={if($_.Target) {$_.Name+' -> '+$_.Target} else {$_.Name}}} }
# List including Owner
function l { Get-ChildItem @args | Format-Table Mode, @{N='Owner';E={(Get-Acl $_.FullName).Owner}}, Length, @{n='LastWriteTime';e={$_.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")}}, @{N='Name';E={if($_.Target) {$_.Name+' -> '+$_.Target} else {$_.Name}}} }
# List without Owner
function lo { Get-ChildItem @args | Format-Table Mode, Length, @{n='LastWriteTime';e={$_.LastWriteTime.ToString("yyyy-MM-dd HH:mm:ss")}}, @{N='Name';E={if($_.Target) {$_.Name+' -> '+$_.Target} else {$_.Name}}} }
# Text editor
function mousepad { & "notepad" @args }
function np { mousepad @args }
function cp { copy @args }
function cat { Get-Content @args }
function mkd {
    mkdir @args > $null
    cd @args
}

