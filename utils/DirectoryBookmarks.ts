// npm install --save-dev @types/node
// npm install --save-dev @types/node

import * as fs from 'fs';
import * as path from 'path';

class DirectoryBookmarks {
  private static USER_HOME: string = process.env.HOME || process.env.USERPROFILE || '';
  private static CELL_SEPARATOR: string = '\t';
  private static COMMENT: string = '#';
  private static NEW_LINE: string = '\n';

  private storeName: string = path.join(this.USER_HOME, '.directory-bookmarks.csv');
  private out: fs.WriteStream = process.stdout;
  private err: fs.WriteStream = process.stderr;
  private exitByException: boolean = false;
  private isSystemWindows: boolean = this.isSystemMsWindows();

  public static main(argumentArray: string[]): void {
    const args: string[] = [...argumentArray];
    const enforcedLinux: boolean = args.length !== 0 && args[0] === 'linux';

    if (enforcedLinux) {
      args.shift();
    }

    new DirectoryBookmarks().start(args);
  }

  protected start(args: string[]): void {
    const statement: string = args.length === 0 ? '' : args[0];

    if (statement === '') {
      this.printHelpAndExit(0);
    }

    switch (statement.charAt(0) === '-' ? statement.substring(1) : statement) {
      case 'l':
      case 'list':
        this.printDirectories();
        break;
      case 's':
      case 'save':
        if (args.length < 3) this.printHelpAndExit(-1);
        this.save(args[1], args[2], args.slice(3));
        break;
      case 'r':
      case 'read':
        if (args.length < 2) this.printHelpAndExit(-1);
        this.removeBookmark(args[1]);
        break;
      case 'b':
      case 'bookmarks':
        const dir: string = args.length > 1 ? args[1] : process.cwd();
        this.printAllBookmarksOfDirectory(dir);
        break;
      case 'i':
      case 'install':
        this.printInstall();
        break;
      case 'f':
      case 'fix':
        this.fixMarksOfMissingDirectories();
        break;
      case 'v':
      case 'version':
        this.out.printf('%s%n', '1.9.0');
        break;
      default:
        this.out.printf('Arguments are not supported: %s', args.join(' '));
        this.printHelpAndExit(-1);
    }
  }

  private printHelpAndExit(status: number, ...messageLines: string[]): void {
    const output: fs.WriteStream = status === 0 ? this.out : this.err;
    const isJar: boolean = false;
    const javaExe: string = `node ${__filename}`;
    const initFile: string = this.isSystemWindows ? '$HOME\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1' : '~/.bashrc';

    output.write(`${this.appName} ${this.appVersion} (${this.homePage})\n`);
    output.write(`Usage: ${javaExe} [lsrbfuc] bookmark directory optionalComment\n`);

    if (this.isSystemWindows) {
      output.write(`Integrate the script to Windows: ${javaExe} i >> ${initFile}`);
    } else {
      output.write(`Integrate the script to Ubuntu: ${javaExe} i >> ${initFile} && . ${initFile}\n`);
    }

    this.exit(status);
  }

  private exit(status: number, ...messageLines: string[]): void {
    const msg: string = messageLines.join(this.NEW_LINE);

    if (this.exitByException && status < 0) {
      throw new Error(msg);
    } else {
      const output: fs.WriteStream = status >= 0 ? this.out : this.err;
      output.write(`${msg}\n`);
      process.exit(status);
    }
  }

  private printDirectories(): void {
    const storeFile: string = this.createStoreFile();
    const lines: string[] = fs.readFileSync(storeFile, 'utf-8').split('\n');

    lines
      .filter(line => !line.startsWith(this.COMMENT))
      .sort()
      .map(line => (this.isSystemWindows ? line.replace(/\//g, '\\') : line))
      .forEach(line => this.out.write(`${line}\n`));
  }

  private getDirectory(key: string, defaultDir: string): string {
    switch (key) {
      case this.currentDirMark:
        return process.cwd();
      default:
        const idx: number = key.indexOf(this.dirSeparator);
        const extKey: string = (idx >= 0 ? key.substring(0, idx) : key) + this.CELL_SEPARATOR;
        const storeFile: string = this.createStoreFile();
        const lines: string[] = fs.readFileSync(storeFile, 'utf-8').split('\n');

        const dir: string | undefined = lines
          .filter(line => !line.startsWith(this.COMMENT))
          .filter(line => line.startsWith(extKey))
          .map(line => line.substring(extKey.length))
          .find(Boolean);

        if (dir) {
          const dirString: string = dir;
          const commentPattern: RegExp = new RegExp(`\\s+${this.COMMENT}\\s`);
          const commentMatcher: RegExpMatchArray | null = dirString.match(commentPattern);
          const endDir: string = idx >= 0 ? this.dirSeparator + key.substring(idx + 1) : '';
          const result: string = (commentMatcher
            ? dirString.substring(0, commentMatcher.index)
            : dirString) + endDir;

          return this.convertDir(false, result, this.isSystemWindows);
        }
    }

    return defaultDir;
  }

  private removeBookmark(key: string): void {
    this.save('', key, []);
  }

  private save(dir: string, key: string, comments: string[]): void {
    if (key.includes(this.CELL_SEPARATOR) || key.includes(this.dirSeparator)) {
      this.exit(-1, `The bookmark contains a tab or a slash: '${key}'`);
    }

    if (this.currentDirMark === dir) {
      dir = process.cwd();
    }

    const extendedKey: string = key + this.CELL_SEPARATOR;
    const tempFile: string = this.getTempStoreFile();
    const storeFile: string = this.createStoreFile();
    const dataHeader: string = `${this.dataHeader}${this.NEW_LINE}`;
    const lines: string[] = fs.readFileSync(storeFile, 'utf-8').split('\n');

    const writer: fs.WriteStream = fs.createWriteStream(tempFile);
    writer.write(dataHeader);

    if (dir !== '') {
      const keyDirLine: string =
        `${key}${this.CELL_SEPARATOR}${this.convertDir(true, dir, this.isSystemWindows)}`;
      if (comments.length !== 0) {
        writer.write(`${keyDirLine}${this.CELL_SEPARATOR}${this.COMMENT}`);
        for (const comment of comments) {
          writer.write(` ${comment}`);
        }
      }
      writer.write(this.NEW_LINE);
    }

    lines
      .filter(line => !line.startsWith(this.COMMENT))
      .filter(line => !line.startsWith(extendedKey))
      .sort()
      .forEach(line => writer.write(`${line}${this.NEW_LINE}`));

    writer.end();

    fs.renameSync(tempFile, storeFile);
  }

  private createStoreFile(): string {
    if (!fs.existsSync(this.storeName)) {
      fs.writeFileSync(this.storeName, '');
    }

    return this.storeName;
  }

  private getTempStoreFile(): string {
    const tempFile: string = path.join(path.dirname(this.storeName), '.dirbook');

    fs.writeFileSync(tempFile, '');
    return tempFile;
  }

  private fixMarksOfMissingDirectories(): void {
    const keys: string[] = this.getAllSortedKeys();

    keys
      .filter(key => {
        const dir: string = this.getDirectory(key, '');
        return dir === '' || !fs.existsSync(dir);
      })
      .forEach(key => {
        const msg: string = `Removed: ${key}\t${this.getDirectory(key, '?')}`;
        this.out.write(`${msg}\n`);
        this.removeBookmark(key);
      });
  }

  private getAllSortedKeys(): string[] {
    const result: string[] = [];

    const lines: string[] = fs.readFileSync(this.createStoreFile(), 'utf-8').split('\n');
    lines
      .filter(line => !line.startsWith(this.COMMENT))
      .sort()
      .map(line => line.substring(0, line.indexOf(this.CELL_SEPARATOR)))
      .forEach(key => result.push(key));

    return result;
  }

  private printAllBookmarksOfDirectory(directory: string): void {
    this.getAllSortedKeys().forEach(key => {
      if (directory === this.getDirectory(key, '')) {
        this.out.write(`${key}\n`);
      }
    });
  }

  private printInstall(): void {
    const exe: string = `node ${__filename}`;
    if (this.isSystemWindows) {
      const msg: string = `
# Shortcuts for ${this.appName} v${this.appVersion} utilities - for the PowerShell:
function directoryBookmarks { & ${exe} $args }
function cdf { Set-Location -Path $(directoryBookmarks -l $args) }
function sdf { directoryBookmarks s $($PWD.Path) @args }
function ldf { directoryBookmarks l $args }
function cpf() { cp ($args[0..($args.Length - 2)]) -Destination (ldf $args[-1]) -Force }
      `;
      this.out.write(msg);
    } else {
      const msg: string = `
# Shortcuts for ${this.appName} v${this.appVersion} utilities - for the Bash:
alias directoryBookmarks='${exe}'
cdf() { cd "$(directoryBookmarks l $1)"; }
sdf() { directoryBookmarks s "$PWD" "$@"; } # Ready for symbolic links
ldf() { directoryBookmarks l "$1"; }
cpf() { argCount=$#; cp "${@:1:$((argCount-1))}" "$(ldf ${!argCount})"; }
      `;
      this.out.write(msg);
    }
  }

  private convertDir(toStoreFormat: boolean, dir: string, isSystemWindows: boolean): string {
    const homeDirMarkEnabled: boolean = !this.homeDirMark.isEmpty();

    if (toStoreFormat) {
      let result: string = homeDirMarkEnabled && dir.startsWith(this.USER_HOME)
        ? this.homeDirMark + dir.substring(this.USER_HOME.length)
        : dir;

      return isSystemWindows
        ? result.replace(/\\/g, '/')
        : result;
    } else {
      let result: string = isSystemWindows
        ? dir.replace(/\//g, '\\')
        : dir;

      return homeDirMarkEnabled && result.startsWith(this.homeDirMark)
        ? this.USER_HOME + result.substring(this.homeDirMark.length)
        : result;
    }
  }

  private isSystemMsWindows(): boolean {
    return process.platform === 'win32';
  }

  private get appName(): string {
    return path.basename(__filename, path.extname(__filename));
  }

  private get appVersion(): string {
    return '1.9.0';
  }

  private get homePage(): string {
    return 'https://github.com/pponec/DirectoryBookmarks';
  }

  private get currentDirMark(): string {
    return '.';
  }

  private get homeDirMark(): string {
    return '~';
  }

  private get mainClass(): Function {
    return this.constructor;
  }

  private get sourceUrl(): string {
    return `https://raw.githubusercontent.com/pponec/DirectoryBookmarks/${!true ? 'main' : 'development'}/${this.appName}.ts`;
  }

  private get dataHeader(): string {
    return `${this.COMMENT} ${this.appName} ${this.appVersion} (${this.homePage})`;
  }

  private get currentDir(): string {
    return process.cwd();
  }

  private get dirSeparator(): string {
    return this.isSystemLinux ? '/' : path.sep;
  }

  private get isSystemLinux(): boolean {
    return process.platform === 'linux';
  }
}

DirectoryBookmarks.main(process.argv.slice(2));
