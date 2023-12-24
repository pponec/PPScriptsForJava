// Running by Node.js: $ node DirectoryBookmarks.js
// Licence: Apache License, Version 2.0, https://github.com/pponec/

// npm install --save-dev @types/node
// npm install --save-dev @types/node

const fs = require('fs');
const path = require('path');
const readline = require('readline');
const USER_HOME = process.env.HOME || process.env.USERPROFILE || "~";

class DirectoryBookmarks {
    private readonly homePage = "https://github.com/pponec/DirectoryBookmarks";
    private readonly appName = path.basename(__filename, '.js');
    private readonly appVersion = "1.9.0";
    private readonly cellSeparator = '\t';
    private readonly comment = '#';
    private readonly newLine = require('os').EOL;
    private readonly dataHeader = `${this.comment} ${this.appName} ${this.appVersion} (${this.homePage})`;
    private readonly currentDir = process.cwd();
    private readonly currentDirMark = ".";
    private readonly homeDirMark = "~";
    private readonly storeName : string;
    private readonly out : NodeJS.WriteStream;
    private readonly err : NodeJS.WriteStream;
    private readonly exitByException : boolean;
    private readonly isSystemWindows : boolean;
    private readonly dirSeparator : string;

    constructor(storeName: string, out: NodeJS.WriteStream, err: NodeJS.WriteStream, enforcedLinux: boolean, exitByException: boolean) {
        this.storeName = storeName;
        this.out = out;
        this.err = err;
        this.exitByException = exitByException;
        this.isSystemWindows = !enforcedLinux && this.isSystemMsWindowsProc();
        this.dirSeparator = enforcedLinux ? '/' : path.sep;
    }

    static main(argumentArray : string[] ) {
        let args = new Array(...argumentArray);
        const enforcedLinux = args.length > 0 && "linux" === args[0];
        if (enforcedLinux) {
            args = args.slice(1);
        }
        new DirectoryBookmarks(path.join(USER_HOME, ".directory-bookmarks.csv"),
            process.stdout,
            process.stderr, enforcedLinux, false).start(args);
    }

    start(args: Array<string>) {
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
                        this.out.write(`${dir}\n`);
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
                this.out.write(`${this.appVersion}\n`);
                break;
            default:
                this.out.write(`Arguments are not supported: ${args.join(' ')}\n`);
                this.printHelpAndExit(-1);
        }
    }

    printHelpAndExit(status: number) {
        const out = status === 0 ? this.out : this.err;
        const javaExe = `node ${this.appName}.js`;
        out.write(`${this.appName} ${this.appVersion} (${this.homePage})\n`);
        out.write(`Usage: ${javaExe} [lsrbfuc] bookmark directory optionalComment\n`);
        if (this.isSystemWindows) {
            const initFile = "$HOME\\Documents\\WindowsPowerShell\\Microsoft.PowerShell_profile.ps1";
            out.write(`Integrate the script to Windows: ${javaExe} i >> ${initFile}`);
        } else {
            const initFile = "~/.bashrc";
            out.write(`Integrate the script to Ubuntu: ${javaExe} i >> ${initFile} && . ${initFile}\n`);
        }
        this.exit(status);
    }

    exit(status : number, ...messageLines : string[]) {
        const msg = messageLines.join(this.newLine);
        if (this.exitByException && status < 0) {
            throw new Error(msg);
        } else {
            const output = status >= 0 ? this.out : this.err;
            output.write(`${msg}\n`);
            process.exit(status);
        }
    }

    printDirectories() {
        const storeFile = this.createStoreFile();
        this.forAllLines(storeFile, line => {
            if (!line.startsWith(this.comment)) {
                const formattedLine: string = this.isSystemWindows ? line.replace('/', '\\') : line;
                this.out.write(`${formattedLine}\n`);
            }
        });
    }

    getDirectory(key: string, defaultDir: string) {
        switch (key) {
            case this.currentDirMark:
                return this.currentDir;
            default:
                const idx = key.indexOf(this.dirSeparator);
                const extKey = (idx >= 0 ? key.substring(0, idx) : key) + this.cellSeparator;
                const storeFile = this.createStoreFile();
                let dir = defaultDir;
                this.forAllLines(storeFile, line => {
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

    removeBookmark(key: string) {
        this.save("", key, []);
    }

    save(dir : string, key : string, comments : string[]) {
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
            let line = `${key}${this.cellSeparator}${this.convertDir(true, dir, this.isSystemWindows)}`;
            if (comments.length > 0) {
                line += `${this.cellSeparator}${this.comment}`;
                for (const comment of comments) {
                    line += ` ${comment}`;
                }
            }
            writer.write(line + this.newLine);
        }
        this.forAllLines(storeFile, line => {
            if (!line.startsWith(this.comment) && !line.startsWith(extendedKey)) {
                writer.write(line + this.newLine);
            }
        });
        fs.renameSync(tempFile, storeFile);
    }

    createStoreFile(){
        if (!fs.existsSync(this.storeName)) {
            fs.writeFileSync(this.storeName, "");
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
                this.out.write(`${msg}\n`);
                this.removeBookmark(key);
            }
        }
    }

    getAllSortedKeys(): string[] {
        const result : string[] = [];
        const storeFile = this.createStoreFile();
        this.forAllLines(storeFile, (line: string) => {
            if (!line.startsWith(this.comment)) {
                const key = line.substring(0, line.indexOf(this.cellSeparator));
                result.push(key);
            }
        });
        return result.sort();
    }

    printAllBookmarksOfDirectory(directory: string) {
        const keys = this.getAllSortedKeys();
        for (const key of keys) {
            if (directory === this.getDirectory(key, "")) {
                this.out.write(`${key}\n`);
            }
        }
    }

    printInstall() {
        const exe = `node ${this.appName}.js`;
        if (this.isSystemWindows) {
            const msg = [
                "",
                `# Shortcuts for ${this.appName} v${this.appVersion} utilities - for the PowerShell:`,
                `function directoryBookmarks { & ${exe} $args }`,
                'function cdf { Set-Location -Path \$(directoryBookmarks -l \$args) }',
                'function sdf { directoryBookmarks s \$($PWD.Path) @args }',
                'function ldf { directoryBookmarks l \$args }',
                'function cpf() { cp ($args[0..($args.Length - 2)]) -Destination (ldf $args[-1]) -Force }'
            ];
            this.out.write(msg.join(this.newLine));
        } else {
            const msg = [
                "",
                `# Shortcuts for ${this.appName} v${this.appVersion} utilities - for the Bash:`,
                `alias directoryBookmarks='${exe}'`,
                'cdf() { cd "$(directoryBookmarks l $1)"; }',
                'sdf() { directoryBookmarks s "$PWD" "$@"; }',
                'ldf() { directoryBookmarks l "$1"; }',
                'cpf() { argCount=$#; cp ${@:1:$((argCount-1))} "$(ldf ${!argCount})"; }'
            ];
            this.out.write(msg.join(this.newLine));
        }
    }

    convertDir(toStoreFormat: boolean, dir: string, isSystemWindows: boolean) : string {
        const homeDirMarkEnabled = true; // this.homeDirMark !== ""; // TODO(fix the error)
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

    isSystemMsWindowsProc() {
        return process.platform === "win32";
    }

    forAllLines(filePath: string,  consumer: (line: string) => void, encoding: BufferEncoding = 'utf-8'): void {
        const reader = readline.createInterface({
            input: fs.createReadStream(filePath, {encoding}),
            crlfDelay: Infinity
        });
        reader.on('line', (line : string) => {
            consumer(line);
        });
    }
}

module.exports = DirectoryBookmarks;
DirectoryBookmarks.main(process.argv.slice(2));