// npm install --save-dev @types/node
// npm install --save-dev @types/node

const fs = require('fs');
const path = require('path');
const USER_HOME = process.env.HOME || process.env.USERPROFILE;

class DirectoryBookmarks {

  private homePage = "https:";
  private appName = path.basename(__filename, '.js');
  private appVersion = "1.9.0";
  private cellSeparator = '\t';
  private comment = '#';
  private newLine = require('os').EOL;
  private dataHeader = `${this.comment} ${this.appName} ${this.appVersion} (${this.homePage})`;
  private currentDir = process.cwd();
  private currentDirMark = ".";
  private homeDirMark = "~";
  private storeName;
  private out;
  private err;
  private exitByException;
  private isSystemWindows;
  private dirSeparator;

  constructor(storeName, out, err, enforcedLinux : boolean, exitByException: boolean ) {
    this.storeName = storeName;
    this.out = out;
    this.err = err;
    this.exitByException = exitByException;
    this.isSystemWindows = !enforcedLinux && this.isSystemWindows();
    this.dirSeparator = enforcedLinux ? '/' : path.sep;
  }

  static main(argumentArray) {
    let args = argumentArray;
    const enforcedLinux = !args.isEmpty() && "linux" === args[0];
    if (enforcedLinux) {
      args = args.slice(1);
    }
    new DirectoryBookmarks(path.join(USER_HOME, ".directory-bookmarks.csv"),
        process.stdout,
        process.stderr, enforcedLinux, false).start(args);
  }

  start(args : string[]) {
    const statement = args.length === 0 ? "" : args[0];
    if (statement === "") {
      this.printHelpAndExit(0);
    }
    switch (statement.charAt(0) === '-' ? statement.substring(1) : statement) {
      case "l":
      case "list":
        if (args.length > 1 && args[1] !== "") {
          const defaultDir = `Bookmark [${args[1]}] has no directory.`;
          const dir = this.getDirectory(args[1], defaultDir);
          if (dir === defaultDir) {
            this.exit(-1, defaultDir);
          } else {
            this.out.println(dir);
          }
        } else {
          this.printDirectories();
        }
        break;
      case "s":
      case "save":
        if (args.length < 3) {
          this.printHelpAndExit(-1);
        }
        const msg = args.slice(3);
        this.save(args[1], args[2], msg);
        break;
      case "r":
      case "read":
        if (args.length < 2) {
          this.printHelpAndExit(-1);
        }
        this.removeBookmark(args[1]);
        break;
      case "b":
      case "bookmarks":
        const dir = args.length > 1 ? args[1] : this.currentDir;
        this.printAllBookmarksOfDirectory(dir);
        break;
      case "i":
      case "install":
        this.printInstall();
        break;
      case "f":
      case "fix":
        this.fixMarksOfMissingDirectories();
        break;
      case "v":
      case "version":
        this.out.printf("%s%n", this.appVersion);
        break;
      default:
        this.out.printf("Arguments are not supported: %s", args.join(" "));
        this.printHelpAndExit(-1);
    }
  }

  printHelpAndExit(status) {
    const out = status === 0 ? this.out : this.err;
    const isJar = false;
    const javaExe = `python3 ${this.appName}.py`;
    out.printf("%s %s (%s)%n", this.appName, this.appVersion, this.homePage);
    out.printf("Usage: %s [lsrbfuc] bookmark directory optionalComment%n", javaExe);
    if (this.isSystemWindows) {
      const initFile = "$HOME\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1";
      out.printf("Integrate the script to Windows: %s i >> %s", javaExe, initFile);
    } else {
      const initFile = "~/.bashrc";
      out.printf("Integrate the script to Ubuntu: %s i >> %s && . %s%n", javaExe, initFile, initFile);
    }
    this.exit(status);
  }

  exit(status, ...messageLines) {
    const msg = messageLines.join(this.newLine);
    if (this.exitByException && status < 0) {
      throw new Error(msg);
    } else {
      const output = status >= 0 ? this.out : this.err;
      output.println(msg);
      process.exit(status);
    }
  }

  printDirectories() {
    const storeFile = this.createStoreFile();
    const reader = fs.createReadStream(storeFile);
    reader.on('line', (line) => {
      if (!line.startsWith(this.comment)) {
        const formattedLine = this.isSystemWindows ? line.replace('/', '\\') : line;
        this.out.println(formattedLine);
      }
    });
  }

  getDirectory(key, defaultDir) {
    switch (key) {
      case this.currentDirMark:
        return this.currentDir;
      default:
        const idx = key.indexOf(this.dirSeparator);
        const extKey = (idx >= 0 ? key.substring(0, idx) : key) + this.cellSeparator;
        const storeFile = this.createStoreFile();
        const reader = fs.createReadStream(storeFile);
        let dir = defaultDir;
        reader.on('line', (line) => {
          if (!line.startsWith(this.comment) && line.startsWith(extKey)) {
            const dirString = line.substring(extKey.length);
            const commentPattern = new RegExp(`\\s+${this.comment}\\s`);
            const commentMatcher = commentPattern.exec(dirString);
            const endDir = idx >= 0 ? this.dirSeparator + key.substring(idx + 1) : "";
            const result = (commentMatcher
                    ? dirString.substring(0, commentMatcher.index)
                    : dirString)
                + endDir;
            dir = this.convertDir(false, result, this.isSystemWindows);
          }
        });
        return dir;
    }
  }

  removeBookmark(key) {
    this.save("", key, []);
  }

  save(dir, key, comments) {
    if (key.indexOf(this.cellSeparator) >= 0 || key.indexOf(this.dirSeparator) >= 0) {
      this.exit(-1, `The bookmark contains a tab or a slash: '${key}'`);
    }
    if (this.currentDirMark === dir) {
      dir = this.currentDir;
    }
    const extendedKey = key + this.cellSeparator;
    const tempFile = this.getTempStoreFile();
    const storeFile = this.createStoreFile();
    const writer = fs.createWriteStream(tempFile);
    writer.write(this.dataHeader + this.newLine);
    if (dir !== "") {
      let line = `${key}${this.cellSeparator}${this.convertDir(true, dir, this.isSystemMsWindows())}`;
      if (comments.length > 0) {
        line += `${this.cellSeparator}${this.comment}`;
        for (const comment of comments) {
          line += ` ${comment}`;
        }
      }
      writer.write(line + this.newLine);
    }
    const reader = fs.createReadStream(storeFile);
    reader.on('line', (line) => {
      if (!line.startsWith(this.comment) && !line.startsWith(extendedKey)) {
        writer.write(line + this.newLine);
      }
    });
    writer.on('finish', () => {
      fs.renameSync(tempFile, storeFile);
    });
  }

  createStoreFile() {
    if (!fs.existsSync(this.storeName)) {
      try {
        fs.writeFileSync(this.storeName, "");
      } catch (e) {
        throw new Error(e);
      }
    }
    return this.storeName;
  }

  getTempStoreFile() {
    const tempFile = fs.mkdtempSync(path.join(fs.realpathSync(this.storeName), '.dirbook'));
    return path.join(tempFile, '');
  }

  fixMarksOfMissingDirectories() {
    const keys = this.getAllSortedKeys();
    for (const key of keys) {
      const dir = this.getDirectory(key, "");
      if (dir === "" || !fs.existsSync(dir)) {
        const msg = `Removed: ${key}\t${this.getDirectory(key, "?")}`;
        this.out.println(msg);
        this.removeBookmark(key);
      }
    }
  }

  getAllSortedKeys() {
    const result = [];
    const storeFile = this.createStoreFile();
    const reader = fs.createReadStream(storeFile);
    reader.on('line', (line) => {
      if (!line.startsWith(this.comment)) {
        const key = line.substring(0, line.indexOf(this.cellSeparator));
        result.push(key);
      }
    });
    return result.sort();
  }

  printAllBookmarksOfDirectory(directory) {
    const keys = this.getAllSortedKeys();
    for (const key of keys) {
      if (directory === this.getDirectory(key, "")) {
        this.out.println(key);
      }
    }
  }

  printInstall() {
    const exe = `python3 ${this.appName}.py`;
    if (this.isSystemWindows) {
      const msg = [
        "",
        `# Shortcuts for ${this.appName} v${this.appVersion} utilities - for the PowerShell:`,
        `function directoryBookmarks { & ${exe} $args }`,
        `function cdf { Set-Location -Path $(directoryBookmarks -l $args) }`,
        `function sdf { directoryBookmarks s $($PWD.Path) @args }`,
        `function ldf { directoryBookmarks l $args }`,
        `function cpf() { cp ($args[0..($args.Length - 2)]) -Destination (ldf $args[-1]) -Force }`
      ];
      this.out.println(msg.join(this.newLine));
    } else {
      const msg = [
        "",
        `# Shortcuts for ${this.appName} v${this.appVersion} utilities - for the Bash:`,
        `alias directoryBookmarks='${exe}'`,
        `cdf() { cd "$(directoryBookmarks l $1)"; }`,
        `sdf() { directoryBookmarks s "$PWD" "$@"; }`,
        `ldf() { directoryBookmarks l "$1"; }` //,
        //`cpf() { argCount=$#; cp ${@:1:$((argCount-1))} "$(ldf ${!argCount})"; }`
      ];
      this.out.println(msg.join(this.newLine));
    }
  }

  convertDir(toStoreFormat, dir, isSystemWindows) {
    const homeDirMarkEnabled = this.homeDirMark !== "";
    if (toStoreFormat) {
      let result = homeDirMarkEnabled && dir.startsWith(USER_HOME)
          ? this.homeDirMark + dir.substring(USER_HOME.length)
          : dir;
      result = isSystemWindows
          ? result.replace('\\', '/')
          : result;
      return result;
    } else {
      let result = isSystemWindows
          ? dir.replace('/', '\\')
          : dir;
      result = homeDirMarkEnabled && result.startsWith(this.homeDirMark)
          ? USER_HOME + result.substring(this.homeDirMark.length)
          : result;
      return result;
    }
  }

  isSystemMsWindows() {
    return process.platform === "win32";
  }
}

module.exports = DirectoryBookmarks;
DirectoryBookmarks.main(process.argv);


