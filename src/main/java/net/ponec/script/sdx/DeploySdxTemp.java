package net.ponec.script.sdx;

/* Deploy a SDX module
 *
 * Usage: java DeploySdx [info] am.ear-2.0.6-SNAPSHOT_3142.ear
 *    or: java DeploySdx [info] .
 * The file argument can be a directory from which the latest EAR is taken.
 *
 * Add the function to the Powershell config ("%USERPROFILE%"\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1):
 * function deploySdx { $javaClass = Join-Path -Path $env:UserProfile -ChildPath 'bin\DeploySdx.java'; java $javaClass $args }
 *
 * Author: Pavel Ponec
 * Verson: 2024-06-07
 */

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class DeploySdxTemp {

    private static final String appVersion = "1.0.4";
    private static final int basePort = 8080;
    private static final String defDistributionDir = "_distribution/binaries";
    private static final boolean killServer = !true;
    private static final boolean deleteTempDir = !true;
    private static final boolean DEMO_DIR = !false;
    private static final String ROOT = DEMO_DIR ? "/home/pavel/temp" // "C:/tmp"
            : "E:";
    /** EAR pattern, for example: am.ear-2.0.6-SNAPSHOT_3142.ear */
    private static final Pattern earPattern = Pattern.compile("^(.{2,5})\\..+?-(.*?)_(\\d+)\\.[eE][aA][rR]$");
    private static final Map<String, ModuleEnum> ITEMS = Stream
            .of(ModuleEnum.class.getEnumConstants())
            .collect(Collectors.toMap(ModuleEnum::getKey, Function.identity()));

    public static void main(String[] args) throws IOException {
        new DeploySdxTemp().run(Array.of(args));
    }

    void run(Array<String> args) throws IOException {
        final var infoMode = args.getFirst().orElse("").equals("info");
        if (infoMode) {
            args = args.removeFirst();
        }

        if (args.getFirst().orElse("").isEmpty()) {
            println("Use the next statement to copy EAR file to the target (v%s):", appVersion);
            println("$ java %s.java [info] am.ear-2.0.6-SNAPSHOT_3142.ear", getClass().getSimpleName());
            println("where the file name can be replaced a directory");
            System.exit(1);
        }

        final var earFile = findEarFile(args.getItem(0));
        final var earModel = createEarModel(earFile);

        printInfo(earModel);
        if (infoMode) {
            return;
        }

        validateContext(earFile, earModel);
        backupEar(earModel);
        if (killServer) {
            killServer(earModel.moduleEnum);
        }
        if (deleteTempDir) {
            deleteTempDir(earModel);
        }
        deleteDeployDir(earModel);
        copyEarToDeploy(earModel);
        println(">>> The module %s is deployed. Start the service manually.", earModel.moduleEnum.name());
    }

    /** Sample: am.ear-2.0.6-SNAPSHOT_3142.ear */
    EarModel createEarModel(Path earFile) {
        var fileName = earFile.getFileName().toString();
        var matcher = earPattern.matcher(fileName);
        if (!matcher.find()) {
            throw new IllegalArgumentException("Incorrect format of the file name: " + fileName);
        }
        var values = IntStream.range(1, matcher.groupCount() + 1 )
                .mapToObj(matcher::group)
                .toList();

        var moduleName = values.get(0);
        var earVersion = values.get(1);
        var commitNumber = values.get(2);
        var moduleEnum = ModuleEnum.findModule(moduleName);
        return new EarModel(earFile, moduleEnum, commitNumber, earVersion, moduleName);
    }

    Path findEarFile(final String fileOrDirectory) throws IOException {
        final var path = Path.of(fileOrDirectory);
        return !Files.isDirectory(path)
                ? path
                : Files.list(path)
                .filter(Files::isReadable)
                .filter(p -> earPattern.matcher(p.getFileName().toString()).find())
                .min(new TimeDescendingComparator())
                .orElseThrow(() -> new IllegalArgumentException(
                        "No EAR was found to deploy in directory: " + path));
    }

    void validateContext(Path earSource, EarModel earModel) {
        if (!Files.isRegularFile(earSource)) {
            throw new IllegalArgumentException("The source EAR file '%s' was not found".formatted(earSource));
        }
        if (!Files.isDirectory(earModel.getTargetDir())) {
            throw new IllegalArgumentException("The TARGET dir '%s' was not found".formatted(earModel.getTargetDir()));
        }
        final var parentBackup = earModel.getBackupDir().getParent();
        if (!Files.isDirectory(parentBackup)) {
            throw new IllegalArgumentException("The BACKUP dir '%s' was not found".formatted(parentBackup));
        }
    }

    void printInfo(EarModel earModel) {
        println("Debug info:");
        println("\tsourceFile: %s", earModel.sourceEarFile.normalize());
        println("\tmoduleEnum: %s", earModel.moduleEnum);
        println("\tearVersion: %s", earModel.earVersion);
        println("\tcommitNum : %s", earModel.commitNumber);
        println("\tbackupDir : %s", earModel.getBackupDir());
        println("\ttargetDir : %s", earModel.getTargetDir());
        println("\tappVersion: %s", appVersion);
    }

    void copyEarToDeploy(EarModel ear) throws IOException {
        copyFileToDir(ear.sourceEarFile, ear.getTargetDir());
    }

    void deleteTempDir(EarModel ear) {
        deleteAllFiles(ear.getTempDir());
    }

    void deleteDeployDir(EarModel ear) {
        deleteAllFiles(ear.getTargetDir());
    }

    void backupEar(EarModel ear) throws IOException {
        final var backupDir = ear.getBackupDir();
        if (!Files.isDirectory(backupDir)) {
            Files.createDirectories(backupDir);
        }
        copyFileToDir(ear.sourceEarFile, ear.getBackupDir());
    }

    void copyFileToDir(Path file, Path dir) throws IOException {
        final var targetFile = dir.resolve(file.getFileName());
        Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
    }

    void killServer(ModuleEnum module) {
        final var port = module.port;
        final var powerShellCommand = ("Get-Process -Id (Get-NetTCPConnection -LocalPort %s).OwningProcess" +
                " | Stop-Process -Force")
                .formatted(port);
        try {
            powerShellCommand(powerShellCommand);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException("Can't kill the module %s".formatted(module), e);
        }
    }

    void powerShellCommand(String powerShellCommand) throws IOException, InterruptedException {
        // Create a process to run PowerShell
        ProcessBuilder processBuilder = new ProcessBuilder("powershell.exe", "-Command", powerShellCommand);
        Process process = processBuilder.start();

        // Get output from the process
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        String line;
        while ((line = reader.readLine()) != null) {
            println(line);
        }

        // Wait for the end of the process
        int exitCode = process.waitFor();
        println("PowerShell statement finished with the code: " + exitCode);

        // Only success is allowed
        if (exitCode != 0) {
            throw new IllegalStateException("Powersher command fails: '%s'".formatted(powerShellCommand));
        }
    }

    /** Delete all files of the directory: */
    void deleteAllFiles(Path dir) {
        try (var stream = Files.newDirectoryStream(dir)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    Files.delete(file);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Deleting files of the directory %s fails".formatted(dir), e);
        }
    }

    void println(String message, Object... arguments) {
        System.out.printf(message + "%n", arguments);
    }

    record EarModel (
            Path sourceEarFile,
            ModuleEnum moduleEnum,
            String commitNumber,
            String earVersion,
            String moduleName
    ) {
        Path getTargetDir() {
            return moduleEnum.baseDir.resolve("standalone-%s/deployments".formatted(moduleEnum.key));
        }

        Path getBackupDir() {
            var dirName = "%s_v%s".formatted(commitNumber, earVersion);
            return moduleEnum.baseDir.resolve(moduleEnum.distributionDir).resolve(dirName);
        }

        public Path getTempDir() {
            return moduleEnum.baseDir.resolve("tmp");
        }
    }

    /** CreationTime descending comparator */
    static class TimeDescendingComparator implements Comparator<Path> {
        @Override
        public int compare(final Path p1, final Path p2) {
            try {
                return time(p2).compareTo(time(p1));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }

        private FileTime time(final Path path) throws IOException {
            return Files.readAttributes(path, BasicFileAttributes.class).creationTime();
        }
    }


    /** The immutable Array wrapper (from the Ujorm framework) */
    static class Array<T> {
        protected final T[] array;

        public Array(T[] array) {
            this.array = array;
        }

        /** Negative index is supported */
        public Optional<T> get(final int i) {
            final var j = i >= 0 ? i : array.length - i;
            return Optional.ofNullable(j >= 0 && j < array.length ? array[j] : null);
        }

        /** Add new items to the new Array */
        @SuppressWarnings("unchecked")
        public Array<T> add(final T... toAdd) {
            final T[] result = Arrays.copyOf(array, array.length + toAdd.length);
            System.arraycopy(toAdd, 0, result, array.length, toAdd.length);
            return new Array<>(result);
        }

        /** Negative index is supported */
        public T getItem(final int i) {
            return array[i >= 0 ? i : array.length + i];
        }

        public Optional<T> getFirst() {
            return get(0);
        }

        public Optional<T> getLast() {
            return get(-1);
        }

        public Array<T> removeFirst() {
            final var result = array.length > 0 ? Arrays.copyOfRange(array, 1, array.length) : array;
            return new Array<>(result);
        }

        public Array<T> subArray(final int from) {
            final var result = Arrays.copyOfRange(array, from, array.length);
            return new Array<>(result);
        }

        public Stream<T> stream() {
            return Stream.of(array);
        }

        public boolean isEmpty() {
            return array.length == 0;
        }

        public int size() {
            return array.length;
        }

        @SuppressWarnings("unchecked")
        public static <T> Array<T> of(T... chars) {
            return new Array<T>(chars);
        }
    }


    enum ModuleEnum {
        /** Dev 10.225.0.6 */
        AM(0, "am", "%s/wildfly_26_AM".formatted(ROOT), ""),
        CM(1, "cm", "%s/wildfly_26_CM".formatted(ROOT), ""),
        UFO(2, "ufo", "%s/wildfly_26_UFO".formatted(ROOT), ""),
        MA(3, "ma", "%s/wildfly_26_MA".formatted(ROOT), ""),
        /** Dev 10.225.0.7 */
        PSO(0, "pso", "%s/wildfly_26_PSO".formatted(ROOT), "_distribution/bin"),
        MMO(1, "mmo", "%s/wildfly_26_MMO".formatted(ROOT), "_distribution/bin"),
        NTFM(2, "ntfm", "%s/wildfly_26_NTFM".formatted(ROOT), "_distribution/bin"),
        TS(3, "ts", "%s/wildfly_26_TS".formatted(ROOT), "_distribution/bin"),
        IDM(4, "idm", "%s/wildfly_26_IDM".formatted(ROOT), "_distribution/bin"),
        UNDEFINED(10000, "?", "/UNDEFINED", "undefined");

        /** Module key */
        final String key;
        /** Module port */
        final int port;
        final Path baseDir;
        final String distributionDir;

        ModuleEnum(int offsetPort, String key, String baseDir, String distributionDir) {
            this.port = basePort + offsetPort;
            this.key = key;
            this.baseDir = Path.of(baseDir);
            this.distributionDir = ! distributionDir.isEmpty()
                    ? defDistributionDir
                    : "_distribution/binaries";
        }

        public String getKey() {
            return key;
        }

        public boolean isAvailable() {
            return Files.isDirectory(baseDir);
        }

        public static ModuleEnum findModule(String module) {
            return ITEMS.getOrDefault(module, ModuleEnum.UNDEFINED);
        }

        /** Example "am(8080)" */
        @Override
        public String toString() {
            return "%s(port:%s)".formatted(key, port);
        }
    }
}