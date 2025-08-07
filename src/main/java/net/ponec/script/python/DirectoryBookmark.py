#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Licence: Apache License 2.0, https://github.com/pponec/
# Converted from Java 17 to Python 3 by Perplexity


import os
import sys
import tempfile
from pathlib import Path
import shutil
import re

class DirectoryBookmarksSimplified:

    USER_HOME = str(Path.home())

    def __init__(self, store_file=None, out=sys.stdout, err=sys.stderr,
                 enforced_linux=False, exit_by_exception=False):
        self.home_page = "https://github.com/pponec/PPScriptsForJava"
        self.app_name = self.__class__.__name__
        self.app_version = "2.0.0"
        self.cell_separator = '\t'
        self.comment = '#'
        self.new_line = '\n'
        self.data_header = f"{self.comment} {self.app_name} {self.app_version} ({self.home_page})"
        self.current_dir = os.getcwd()
        self.current_dir_mark = "."
        self.home_dir_mark = "~"
        self.exit_by_exception = exit_by_exception
        self.is_system_windows = (os.name == "nt") and not enforced_linux
        self.dir_separator = '/' if enforced_linux else os.sep
        self.store_file = Path(store_file) if store_file else Path(self.USER_HOME) / ".directory-bookmarks.csv"
        self.out = out
        self.err = err

    def main_run(self, args):
        if not args:
            self.print_help_and_exit(0)

        statement = args[0]
        if statement.startswith('-'):
            statement = statement[1:]

        if statement in ("l", "list"):
            if len(args) > 1 and args[1]:
                key = args[1]
                default_dir = f"Bookmark [{key}] has no directory."
                dir_path = self.get_directory(key, default_dir)
                if dir_path == default_dir:
                    self.exit(-1, default_dir)
                else:
                    print(dir_path, file=self.out)
            else:
                self.print_directories()

        elif statement in ("g", "get"):
            key = args[1] if len(args) > 1 else self.home_dir_mark
            self.main_run(["l", key])

        elif statement in ("s", "save"):
            if len(args) < 3:
                self.print_help_and_exit(-1)
            directory = args[1]
            key = args[2]
            comments = args[3:] if len(args) > 3 else []
            self.save(directory, key, comments)

        elif statement in ("d", "delete"):
            if len(args) < 2:
                self.print_help_and_exit(-1)
            self.save("", args[1], [])

        elif statement in ("r", "read"):
            if len(args) < 2:
                self.print_help_and_exit(-1)
            self.remove_bookmark(args[1])

        elif statement in ("b", "bookmarks"):
            directory = args[1] if len(args) > 1 else self.current_dir
            self.print_all_bookmarks_of_directory(directory)

        elif statement in ("f", "fix"):
            self.fix_marks_of_missing_directories()

        elif statement in ("v", "version"):
            script_version = self.get_script_version()
            if script_version == self.app_version:
                print(script_version, file=self.out)
            else:
                print(f"{script_version} -> {self.app_version}", file=self.out)

        else:
            print(f"Arguments are not supported: {' '.join(args)}", file=self.out)
            self.print_help_and_exit(-1)

    def print_help_and_exit(self, status):
        out = self.out if status == 0 else self.err

        java_exe = f"python {sys.argv[0]}"
        out.write(f"{self.app_name} {self.app_version} ({self.home_page})\n")
        out.write(f"Usage: {java_exe} [slgdrbfuc] directory bookmark optionalComment\n")
        if self.is_system_windows:
            init_file = "$HOME\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1"
            out.write(f"Integrate the script to Windows: {java_exe} i >> {init_file}\n")
        else:
            init_file = "~/.bashrc"
            out.write(f"Integrate the script to Ubuntu: {java_exe} i >> {init_file} && . {init_file}\n")
        sys.exit(status)

    def exit(self, status, *message_lines):
        msg = self.new_line.join(message_lines)
        if self.exit_by_exception and status != 0:
            raise RuntimeError(msg)
        out = self.out if status >= 0 else self.err
        if msg:
            print(msg, file=out)
        sys.exit(status)

    def create_store_file(self):
        if not self.store_file.exists():
            try:
                self.store_file.touch()
            except Exception as e:
                raise RuntimeError(f"Cannot create store file {self.store_file}: {e}")
        return self.store_file

    def print_directories(self):
        store_file = self.create_store_file()
        with open(store_file, 'r', encoding='utf-8') as f:
            lines = [line.rstrip('\n') for line in f if not line.startswith(self.comment)]
        lines.sort()
        for line in lines:
            if self.is_system_windows:
                print(line.replace('/', '\\'), file=self.out)
            else:
                print(line, file=self.out)

    def get_directory(self, key, default_dir):
        if key == self.current_dir_mark:
            return self.current_dir
        elif key == self.home_dir_mark:
            return self.USER_HOME
        else:
            idx_slash = max(key.find('/'), key.find('\\'))
            if idx_slash >= 0:
                ext_key = key[:idx_slash] + self.cell_separator
                end_dir = key[idx_slash:]
            else:
                ext_key = key + self.cell_separator
                end_dir = ""
            store_file = self.create_store_file()
            with open(store_file, 'r', encoding='utf-8') as f:
                for line in f:
                    if line.startswith(str(self.comment)):
                        continue
                    if line.startswith(ext_key):
                        dir_string = line[len(ext_key):]
                        comment_match = re.search(rf"\s+{re.escape(self.comment)}\s", dir_string)
                        if comment_match:
                            dir_part = dir_string[:comment_match.start()]
                        else:
                            dir_part = dir_string
                        result = dir_part + end_dir
                        result = self.convert_dir(to_store=False, dir_path=result)
                        return result
            return default_dir

    def save(self, directory, key, comments):
        if self.cell_separator in key or self.dir_separator in key:
            self.exit(-1, f"The bookmark contains a tab or a slash: '{key}'")
        if directory == self.current_dir_mark:
            directory = self.current_dir
        extended_key = key + self.cell_separator

        temp_file = tempfile.NamedTemporaryFile('w', encoding='utf-8', delete=False,
                                                dir=self.store_file.parent,
                                                prefix=".dirbook", suffix=".temp")

        store_file = self.create_store_file()

        with temp_file as writer:
            writer.write(self.data_header + self.new_line)
            if directory:
                conv_dir = self.convert_dir(to_store=True, dir_path=directory)
                writer.write(key + self.cell_separator + conv_dir)
                if comments:
                    writer.write(self.cell_separator + self.comment)
                    for comment in comments:
                        writer.write(" " + comment)
                writer.write(self.new_line)

            with open(store_file, 'r', encoding='utf-8') as reader:
                lines = [line.rstrip('\n') for line in reader if not line.startswith(str(self.comment)) and not line.startswith(extended_key)]
                for line in sorted(lines):
                    writer.write(line + self.new_line)

        shutil.move(temp_file.name, store_file)

    def remove_bookmark(self, key):
        self.save("", key, [])

    def fix_marks_of_missing_directories(self):
        keys = self.get_all_sorted_keys()
        for key in keys:
            dir_path = self.get_directory(key, "")
            if not dir_path or not Path(dir_path).is_dir():
                print(f"Removed: {key}\t{self.get_directory(key, '?')}", file=self.out)
                self.remove_bookmark(key)

    def get_all_sorted_keys(self):
        store_file = self.create_store_file()
        keys = []
        with open(store_file, 'r', encoding='utf-8') as f:
            for line in f:
                if line.startswith(str(self.comment)):
                    continue
                if self.cell_separator in line:
                    keys.append(line.split(self.cell_separator, 1)[0])
        return sorted(keys)

    def print_all_bookmarks_of_directory(self, directory):
        keys = self.get_all_sorted_keys()
        for key in keys:
            if self.get_directory(key, "") == directory:
                print(key, file=self.out)

    def get_script_version(self):
        # Python verze nemá externí Java zdroj, proto vrací app_version
        return self.app_version

    def convert_dir(self, to_store, dir_path):
        home_dir_mark = self.home_dir_mark
        user_home = self.USER_HOME
        if to_store:
            if dir_path.startswith(user_home):
                result = home_dir_mark + dir_path[len(user_home):]
            else:
                result = dir_path
            if self.is_system_windows:
                result = result.replace('\\', '/')
            return result
        else:
            result = dir_path
            if self.is_system_windows:
                result = result.replace('/', '\\')
            if result.startswith(home_dir_mark):
                result = user_home + result[len(home_dir_mark):]
            return result

def main():
    args = sys.argv[1:]
    enforced_linux = False
    if args and args[0].lower() == "linux":
        enforced_linux = True
        args = args[1:]
    app = DirectoryBookmarksSimplified(enforced_linux=enforced_linux)
    app.main_run(args)

if __name__ == "__main__":
    main()
