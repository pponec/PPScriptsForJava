# Running by Python 17: $ python3 DirectoryBookmarks.py
# Licence: Apache License, Version 2.0, https://github.com/pponec/
# Enable PowerShell in Windows 11: Set-ExecutionPolicy unrestricted

import os
import sys
import shutil
import tempfile
from pathlib import Path

def is_system_windows():
    return os.name == 'nt'

class DirectoryBookmarks:
    USER_HOME = str(Path.home())
    APP_NAME = "DirectoryBookmarks"
    APP_VERSION = "2.0.0"
    CELL_SEPARATOR = '\t'
    COMMENT = '#'
    NEW_LINE = '\n'
    CURRENT_DIR_MARK = '.'
    HOME_DIR_MARK = '~'
    UTF8='utf-8'

    def __init__(self, store_name=None, enforced_linux=False):
        self.store_name = store_name or Path(self.USER_HOME) / ".directory-bookmarks.csv"
        self.is_system_windows = not enforced_linux and is_system_windows()
        self.dir_separator = '/' if enforced_linux else os.sep

    def main_run(self, args):
        if not args:
            self.print_help_and_exit(0)
        statement = args[0]
        if statement in ('l', 'list'):
            if len(args) > 1:
                key = args[1]
                directory = self.get_directory(key, f"Bookmark [{key}] has no directory.")
                print(directory)
            else:
                self.print_directories()
        elif statement in ('g', 'get'):
            key = args[1] if len(args) > 1 else self.HOME_DIR_MARK
            self.main_run(['l', key])
        elif statement in ('s', 'save'):
            if len(args) < 3:
                self.print_help_and_exit(-1)
            self.save(args[1], args[2], args[3:])
        elif statement in ('d', 'delete'):
            if len(args) < 2:
                self.print_help_and_exit(-1)
            self.save("", args[1], [])
        else:
            print(f"Arguments are not supported: {' '.join(args)}")
            self.print_help_and_exit(-1)

    def print_help_and_exit(self, status):
        print(f"{self.APP_NAME} {self.APP_VERSION}")
        print("Usage: python script.py [slgdr] directory bookmark optionalComment")
        sys.exit(status)

    def print_directories(self):
        try:
            with open(self.store_name, 'r', encoding=self.UTF8) as file:
                lines = file.readlines()
                for line in sorted(lines):
                    if not line.startswith(self.COMMENT):
                        print(line.strip().replace('/', os.sep) if self.is_system_windows else line.strip())
        except FileNotFoundError:
            pass

    def get_directory(self, key, default_dir):
        if key == self.CURRENT_DIR_MARK:
            return os.getcwd()
        elif key == self.HOME_DIR_MARK:
            return self.USER_HOME
        try:
            with open(self.store_name, 'r', encoding=self.UTF8) as file:
                for line in file:
                    if line.startswith(key + self.CELL_SEPARATOR):
                        return line.split(self.CELL_SEPARATOR, 1)[1].strip()
        except FileNotFoundError:
            pass
        return default_dir

    def save(self, directory, key, comments):
        if self.CELL_SEPARATOR in key or os.sep in key:
            print(f"Invalid bookmark key: {key}")
            sys.exit(-1)
        temp_file = tempfile.NamedTemporaryFile(delete=False, mode='w', encoding=self.UTF8)
        try:
            with open(self.store_name, 'r', encoding=self.UTF8) as file:
                lines = file.readlines()
        except FileNotFoundError:
            lines = []

        with open(temp_file.name, 'w', encoding=self.UTF8) as out_file:
            out_file.write(f"{self.COMMENT} {self.APP_NAME} {self.APP_VERSION}{self.NEW_LINE}")
            if directory:
                line = f"{key}{self.CELL_SEPARATOR}{directory}"
                if comments:
                    line += f"{self.CELL_SEPARATOR}{self.COMMENT} {' '.join(comments)}"
                out_file.write(line + self.NEW_LINE)
            for line in lines:
                if not line.startswith(key + self.CELL_SEPARATOR):
                    out_file.write(line)
        shutil.move(temp_file.name, self.store_name)

if __name__ == "__main__":
    args = sys.argv[1:]
    enforced_linux = args and args[0] == "linux"
    if enforced_linux:
        args.pop(0)
    DirectoryBookmarks(enforced_linux=enforced_linux).main_run(args)
