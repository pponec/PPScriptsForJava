# Running by Python: $ python3 DirectoryBookmarks.py
# Licence: Apache License, Version 2.0, https://github.com/pponec/
# Note: Generated code from a Java 17

import os
import sys
import tempfile
import shutil
import re
import requests

class DirectoryBookmarksSimple:
    userHome = os.path.expanduser("~")
    homePage = "https:"
    appName = os.path.basename(__file__).split(".")[0]
    appVersion = "1.9.1py"
    homePage = "https://github.com/pponec/DirectoryBookmarks"
    cellSeparator = '\t'
    comment = '#'
    newLine = os.linesep
    dataHeader = f"{comment} {appName} {appVersion} ({homePage})"
    currentDir = os.getcwd()
    currentDirMark = "."
    sourceUrl = "https://raw.githubusercontent.com/pponec/DirectoryBookmarks/%s/utils/%s.py" % (
        'main' if not True else 'development', appName )
    homeDirMark = "~"
    storeName = None
    exitByException = False
    isSystemWindows = False
    dirSeparator = None

    def __init__(self, storeName, enforcedLinux, exitByException):
        self.storeName = storeName
        self.exitByException = exitByException
        self.isSystemWindows = not enforcedLinux and self.isSystemMsWindows()
        self.dirSeparator = '/' if enforcedLinux else os.path.sep

    def start(self, args):
        statement = args[0] if args else ""
        if not statement:
            self.printHelpAndExit(0)
        statement = statement[1:] if statement[0] == '-' else statement
        if statement in ["l", "list"]:
            if len(args) > 1 and args[1]:
                defaultDir = f"Bookmark [{args[1]}] has no directory."
                dir = self.getDirectory(args[1], defaultDir)
                if dir == defaultDir:
                    self.exit(-1, defaultDir)
                else:
                    print(dir)
            else:
                self.printDirectories()
        elif statement in ["g", "get"]:
            key = args[1] if len(args) > 1 else self.homeDirMark
            self.start(["l", key])
        elif statement in ["s", "save"]:
            if len(args) < 3:
                self.printHelpAndExit(-1)
            msg = args[3:] if len(args) > 3 else []
            self.save(args[1], args[2], msg)
        elif statement in ["r", "read"]:
            if len(args) < 2:
                self.printHelpAndExit(-1)
            self.removeBookmark(args[1])
        elif statement in ["b", "bookmarks"]:
            dir = args[1] if len(args) > 1 else self.currentDir
            self.printAllBookmarksOfDirectory(dir)
        elif statement in ["i", "install"]:
            self.printInstall()
        elif statement in ["f", "fix"]:
            self.fixMarksOfMissingDirectories()
        elif statement in ["u", "upgrade"]:
            self.download()
            print("%s was downloaded." % (self.appName))
        elif statement in ["v", "version"]:
            scriptVersion = self.getScriptVersion()
            if self.appVersion == scriptVersion:
                print(scriptVersion)
            else:
              print(f"{scriptVersion} -> {self.appVersion}")
        else:
            print(f"Arguments are not supported: {' '.join(args)}")
            self.printHelpAndExit(-1)

    def printHelpAndExit(self, status):
        javaExe = f"python3 {self.appName}.py"
        print(f"{self.appName} {self.appVersion} ({self.homePage})")
        print(f"Usage: {javaExe} [lgsrbfuc] bookmark directory optionalComment")
        if self.isSystemWindows:
            initFile = "$HOME\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1"
            print(f"Integrate the script to Windows: {javaExe} i >> {initFile}")
        else:
            initFile = "~/.bashrc"
            print(f"Integrate the script to Ubuntu: {javaExe} i >> {initFile} && . {initFile}")
        self.exit(status)

    def exit(self, status, *messageLines):
        msg = os.linesep.join(messageLines)
        if self.exitByException and status < 0:
            raise NotImplementedError(msg)
        else:
            print(msg)
            sys.exit(status)

    def printDirectories(self):
        storeFile = self.createStoreFile()
        with open(storeFile, "r") as reader:
            for line in reader:
                line = line.strip()
                if not line.startswith(self.comment):
                    line = line.replace('/', '\\') if self.isSystemWindows else line
                    print(line)

    def getDirectory(self, key, defaultDir):
        if key == self.currentDirMark:
            return self.currentDir
        else:
            idx = key.find(self.dirSeparator)
            extKey = key[:idx] + self.cellSeparator if idx >= 0 else key + self.cellSeparator
            storeFile = self.createStoreFile()
            with open(storeFile, "r") as reader:
                for line in reader:
                    line = line.strip()
                    if not line.startswith(self.comment) and line.startswith(extKey):
                        dirString = line[len(extKey):]
                        commentPattern = re.compile(f"\\s+{self.comment}\\s")
                        commentMatcher = commentPattern.search(dirString)
                        endDir = self.dirSeparator + key[idx + 1:] if idx >= 0 else ""
                        if commentMatcher:
                            result = dirString[:commentMatcher.start()] + endDir
                        else:
                            result = dirString + endDir
                        return self.convertDir(False, result, self.isSystemWindows)
        return defaultDir

    def removeBookmark(self, key):
        self.save("", key, [])

    def save(self, dir, key, comments):
        if self.cellSeparator in key or self.dirSeparator in key:
            self.exit(-1, f"The bookmark contains a tab or a slash: '{key}'")
        if key == self.currentDirMark:
            dir = self.currentDir
        extendedKey = key + self.cellSeparator
        tempFile = self.getTempStoreFile()
        storeFile = self.createStoreFile()
        with open(tempFile, "w") as writer:
            writer.write(self.dataHeader + self.newLine)
            if dir:
                writer.write(f"{key}{self.cellSeparator}{self.convertDir(True, dir, self.isSystemMsWindows())}")
                if comments:
                    writer.write(f"{self.cellSeparator}{self.comment}")
                    for comment in comments:
                        writer.write(f" {comment}")
                writer.write(self.newLine)
            with open(storeFile, "r") as reader:
                for line in reader:
                    line = line.strip()
                    if not line.startswith(extendedKey):
                        writer.write(line + self.newLine)
        shutil.move(tempFile, storeFile)

    def createStoreFile(self):
        if not os.path.isfile(self.storeName):
            try:
                open(self.storeName, "w").close()
            except Exception as e:
                raise RuntimeError(f"{self.storeName}", e)
        return self.storeName

    def getTempStoreFile(self):
        return tempfile.NamedTemporaryFile(suffix=".dirbook", dir=self.storeName.parent)

    def fixMarksOfMissingDirectories(self):
        keys = self.getAllSortedKeys()
        for key in keys:
            dir = self.getDirectory(key, "")
            if not dir or not os.path.isdir(dir):
                msg = f"Removed: {key}\t{self.getDirectory(key, '?')}"
                print(msg)
                self.removeBookmark(key)

    def getAllSortedKeys(self):
        result = []
        with open(self.createStoreFile(), "r") as reader:
            for line in reader:
                line = line.strip()
                if not line.startswith(self.comment):
                    result.append(line.split(self.cellSeparator)[0])
        return sorted(result)

    def printAllBookmarksOfDirectory(self, directory):
        keys = self.getAllSortedKeys()
        for key in keys:
            if directory == self.getDirectory(key, ""):
                print(key)

    def download(self):
        try:
            response = requests.get(self.sourceUrl)
            response.raise_for_status()
            with open(os.path.abspath(__file__), "w", encoding="utf-8") as file:
                file.write(response.text)
        except requests.exceptions.RequestException as e:
            raise RuntimeError(f"Upgrade fails: {e}")

    def getScriptVersion(self):
        return self.appVersion

    def printInstall(self):
        exe = f"python3 {os.path.abspath(__file__)}"
        msg = os.linesep.join([
            f"# Shortcuts for {self.appName} v{self.appVersion} utilities - for the Bash:",
            f"alias directoryBookmarks='{exe}'",
            "cdf() { cd \"$(directoryBookmarks g $1)\"; }",
            "sdf() { directoryBookmarks s \"$PWD\" \"$@\"; }",
            "ldf() { directoryBookmarks l \"$1\"; }",
            "cpf() { argCount=$#; cp ${@:1:$((argCount-1))} \"$(ldf ${!argCount})\"; }"
        ])
        print(msg)

    def convertDir(self, toStoreFormat, dir, isSystemWindows):
        homeDirMarkEnabled = bool(self.homeDirMark)
        if toStoreFormat:
            result = f"{self.homeDirMark}{dir[len(self.userHome):]}" if homeDirMarkEnabled and dir.startswith(self.userHome) else dir
            return result.replace('\\', '/') if isSystemWindows else result
        else:
            result = dir.replace('/', '\\') if isSystemWindows else dir
            return f"{self.userHome}{result[len(self.homeDirMark):]}" if homeDirMarkEnabled and result.startswith(self.homeDirMark) else result

    def isSystemMsWindows(self):
        return "win" in sys.platform.lower()

args = sys.argv[1:]
enforcedLinux = bool(args) and args[0] == "linux"
if enforcedLinux:
    args = args[1:]

DirectoryBookmarksSimple(
    os.path.join(DirectoryBookmarksSimple.userHome, ".directory-bookmarks.csv"),
    enforcedLinux,
    False).start(args)


