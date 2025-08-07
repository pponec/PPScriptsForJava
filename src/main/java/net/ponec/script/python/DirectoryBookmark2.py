#!/usr/bin/env python3
# -*- coding: utf-8 -*-
# Licence: Apache License 2.0, https://github.com/pponec/
# Converted from Java 17 to Python 3 by ChatGPT v5

import os
import sys
import re
import shutil
import tempfile
from pathlib import Path
from urllib.parse import unquote
from typing import List as TypedList

class DirectoryBookmarksSimplified:
    USER_HOME = str(Path.home())

    def __init__(self, store_name: Path, out=sys.stdout, err=sys.stderr,
                 enforced_linux=False, exit_by_exception=False):
        self.home_page = "https://github.com/pponec/PPScriptsForJava"
        self.app_name = self.__class__.__name__
        self.app_version = "2.0.0"
        self.cell_separator = '\t'
        self.comment = '#'
        self.new_line = os.linesep
        self.data_header = f"{self.comment} {self.app_name} {self.app_version} ({self.home_page})"
        self.current_dir = os.getcwd()
        self.current_dir_mark = "."
        self.home_dir_mark = "~"
        self.store_name = Path(store_name)
        self.out = out
        self.err = err
        self.exit_by_exception = exit_by_exception
        self.is_system_windows = (not enforced_linux) and self.utils_is_system_ms_windows()
        self.dir_separator = '/' if enforced_linux else os.sep

    @staticmethod
    def main():
        args = sys.argv[1:]
        enforced_linux = (args[0] == "linux") if args else False
        if enforced_linux:
            args = args[1:]
        app = DirectoryBookmarksSimplified(
            Path(DirectoryBookmarksSimplified.USER_HOME) / ".directory-bookmarks.csv",
            sys.stdout, sys.stderr, enforced_linux, False
        )
        app.main_run(args)

    def main_run(self, args: TypedList[str]):
        statement = args[0] if args else ""
        if not statement:
            self.print_help_and_exit(0)

        cmd = statement[1:] if statement.startswith('-') else statement
        if cmd in ("l", "list"):
            if len(args) > 1 and args[1]:
                default_dir = f"Bookmark [{args[1]}] has no directory."
                dir_val = self.get_directory(args[1], default_dir)
                if dir_val == default_dir:
                    self.exit(-1, default_dir)
                else:
                    print(dir_val, file=self.out)
            else:
                self.print_directories()
        elif cmd in ("g", "get"):
            key = args[1] if len(args) > 1 else self.home_dir_mark
            self.main_run(["l", key])
        elif cmd in ("s", "save"):
            if len(args) < 3:
                self.print_help_and_exit(-1)
            msg = args[3:]
            self.save(args[1], args[2], msg)
        elif cmd in ("d", "delete"):
            if len(args) < 2:
                self.print_help_and_exit(-1)
            self.save("", args[1], [])
        elif cmd in ("r", "read"):
            if len(args) < 2:
                self.print_help_and_exit(-1)
            self.remove_bookmark(args[1])
        elif cmd in ("b", "bookmarks"):
            dir_val = args[1] if len(args) > 1 else self.current_dir
            self.print_all_bookmarks_of_directory(dir_val)
        elif cmd in ("f", "fix"):
            self.fix_marks_of_missing_directories()
        elif cmd in ("v", "version"):
            script_version = self.get_script_version()
            if self.app_version == script_version:
                print(script_version, file=self.out)
            else:
                print(f"{script_version} -> {self.app_version}", file=self.out)
        else:
            print(f"Arguments are not supported: {' '.join(args)}", file=self.out)
            self.print_help_and_exit(-1)

    def print_help_and_exit(self, status: int):
        out = self.out if status == 0 else self.err
        java_exe = f"java {self.app_name}.java"
        print(f"{self.app_name} {self.app_version} ({self.home_page})", file=out)
        print(f"Usage: {java_exe} [slgdrbfuc] directory bookmark optionalComment", file=out)
        if self.is_system_windows:
            init_file = "$HOME\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1"
            print(f"Integrate the script to Windows: {java_exe} i >> {init_file}", file=out)
        else:
            init_file = "~/.bashrc"
            print(f"Integrate the script to Ubuntu: {java_exe} i >> {init_file} && . {init_file}", file=out)
        self.exit(status)

    def exit(self, status: int, *message_lines: str):
        msg = self.new_line.join(message_lines)
        if self.exit_by_exception and status != 0:
            raise RuntimeError(msg)
        else:
            output = self.out if status >= 0 else self.err
            if msg:
                print(msg, file=output)
            sys.exit(status)

    def create_store_file(self) -> Path:
        if not self.store_name.exists():
            self.store_name.touch()
        return self.store_name

    def get_temp_store_file(self) -> Path:
        fd, path = tempfile.mkstemp(prefix=".dirbook", suffix=".temp", dir=str(self.store_name.parent))
        os.close(fd)  # close the low-level file descriptor
        return Path(path)

    def print_directories(self):
        with open(self.create_store_file(), encoding="utf-8") as f:
            for line in sorted(l.strip() for l in f if not l.startswith(self.comment)):
                if self.is_system_windows:
                    line = line.replace('/', '\\')
                print(line, file=self.out)

    def get_directory(self, key: str, default_dir: str) -> str:
        if key == self.current_dir_mark:
            return self.current_dir
        elif key == self.home_dir_mark:
            return self.USER_HOME
        else:
            idx = max(key.find('/'), key.find('\\'))
            ext_key = (key[:idx] if idx >= 0 else key) + self.cell_separator
            with open(self.create_store_file(), encoding="utf-8") as f:
                for line in f:
                    if not line.startswith(self.comment) and line.startswith(ext_key):
                        dir_string = line[len(ext_key):]
                        comment_match = re.search(r"\s+#\s", dir_string)
                        end_dir = key[idx:] if idx >= 0 else ""
                        result = (dir_string[:comment_match.start()] if comment_match else dir_string) + end_dir
                        return self.convert_dir(False, result.strip(), self.is_system_windows)
        return default_dir

    def remove_bookmark(self, key: str):
        self.save("", key, [])

    def save(self, dir_val: str, key: str, comments: TypedList[str]):
        if self.cell_separator in key or self.dir_separator in key:
            self.exit(-1, f"The bookmark contains a tab or a slash: '{key}'")
        if dir_val == self.current_dir_mark:
            dir_val = self.current_dir
        extended_key = key + self.cell_separator
        temp_file = self.get_temp_store_file()
        with open(temp_file, "w", encoding="utf-8") as writer:
            writer.write(self.data_header + self.new_line)
            if dir_val:
                writer.write(key + self.cell_separator + self.convert_dir(True, dir_val, self.utils_is_system_ms_windows()))
                if comments:
                    writer.write(self.cell_separator + self.comment + ''.join(' ' + c for c in comments))
                writer.write(self.new_line)
            with open(self.create_store_file(), encoding="utf-8") as reader:
                for line in sorted(l for l in reader if not l.startswith(self.comment) and not l.startswith(extended_key)):
                    writer.write(line)
        shutil.move(temp_file, self.create_store_file())

    def fix_marks_of_missing_directories(self):
        for key in self.get_all_sorted_keys():
            dir_val = self.get_directory(key, "")
            if not dir_val or not Path(dir_val).is_dir():
                print(f"Removed: {key}\t{self.get_directory(key, '?')}", file=self.out)
                self.remove_bookmark(key)

    def get_all_sorted_keys(self) -> TypedList[str]:
        with open(self.create_store_file(), encoding="utf-8") as f:
            return sorted(line.split(self.cell_separator)[0]
                          for line in f if not line.startswith(self.comment))

    def print_all_bookmarks_of_directory(self, directory: str):
        for key in self.get_all_sorted_keys():
            if directory == self.get_directory(key, ""):
                print(key, file=self.out)

    def get_script_version(self) -> str:
        pattern = re.compile(r'String\s+appVersion\s*=\s*"(.+)"\s*;')
        try:
            with open(self.utils_get_src_path(), encoding="utf-8") as f:
                for line in f:
                    match = pattern.search(line)
                    if match:
                        return match.group(1)
        except Exception:
            pass
        return self.app_version

    def convert_dir(self, to_store_format: bool, dir_val: str, is_system_windows: bool) -> str:
        home_enabled = bool(self.home_dir_mark)
        if to_store_format:
            result = (self.home_dir_mark + dir_val[len(self.USER_HOME):]
                      if home_enabled and dir_val.startswith(self.USER_HOME) else dir_val)
            return result.replace('\\', '/') if is_system_windows else result
        else:
            result = dir_val.replace('/', '\\') if is_system_windows else dir_val
            return (self.USER_HOME + result[len(self.home_dir_mark):]
                    if home_enabled and result.startswith(self.home_dir_mark) else result)

    # === Utilities ===
    def utils_get_script_dir(self) -> str:
        exe_path = self.utils_get_path_of_running_application()
        return str(Path(exe_path).parent)

    def utils_is_jar(self) -> bool:
        return self.utils_get_path_of_running_application().lower().endswith(".jar")

    def utils_get_src_path(self) -> str:
        return str(Path(self.utils_get_script_dir()) / f"{self.app_name}.java")

    def utils_get_path_of_running_application(self) -> str:
        try:
            path = sys.argv[0]
            return unquote(os.path.abspath(path))
        except Exception:
            return f"{self.app_name}.java"

    def utils_is_system_ms_windows(self) -> bool:
        return os.name == "nt"


if __name__ == "__main__":
    DirectoryBookmarksSimplified.main()
