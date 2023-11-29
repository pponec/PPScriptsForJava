# GitBash initialization script (https://gitforwindows.org/)
# File name: .profile
# ----------------------------------------------------------

# Shortcuts for DirectoryBookmarks v1.8.5 utilities:
alias directoryBookmarks='"/c/Program Files/Amazon Corretto/jdk17.0.9_8/bin/java" --limit-modules java.base,java.net.http,jdk.compiler,jdk.crypto.ec -jar "/c/Users/User/bin/DirectoryBookmarks.jar" linux'
cdf() { cd "$(directoryBookmarks l $1)"; }
sdf() { directoryBookmarks s "$PWD" "$@"; }
ldf() { directoryBookmarks l "$1"; }
cpf() { numArgs=$#; cp ${@:1:$((numArgs-1))} "$(ldf ${!numArgs})"; }

# System environment variables:
PS1='${debian_chroot:+($debian_chroot)}\u@\h:\w> '
HISTSIZE=1000
HISTFILESIZE=2000
export TIME_STYLE=long-iso

# Some more ls aliases
lt() { ls -lt "$@" | head -n20; }
alias ll='ls -alF'
alias la='ls -A'
alias l='ls -l'
alias q='exit'
alias mousepad='"/c/Program Files/Notepad++/notepad++.exe"'
alias mp='mousepad'
alias np='mousepad'

# Functions
mkd() { mkdir $1 && cd $1; }
lt() { ls -lt "$@" | head -n20; }
grepf() {
   find . -type f -name "$2" -exec grep -lnH "$1" {} \;
   #grep -nHR "$1" --include="$2" .
}
enc() {
   out="$(basename $1).enc"
   cat "$1" | gpg --symmetric --cipher-algo AES256 -o "$out"
}




