# PPUtils

Services called from the command line for general use.

## Tools and examples of use

* `PPUtils find [regExpContent] regExpFile` - find readable files by regular expressions, partial compliance is assessed,
* `PPUtils grep regExpContent regExpFile` - find readable file rows by a regular expression.
* `PPUtils date` - prints a date by ISO format, for example: "2023-12-31"
* `PPUtils time` - prints hours and time, for example "2359"
* `PPUtils datetime` - prints datetime format "2023-12-31T2359"
* `PPUtils date-iso` - prints datetime by ISO format, eg: "2023-12-31T23:59:59.999"
* `PPUtils date-format "yyyy-MM-dd'T'HH:mm:ss.SSS"` - prints a time by a custom format
* `PPUtils base64encode "file.bin"` - encode any (binary) file.
* `PPUtils base64decode "file.base64"` - decode base64 encoded file (result removes extension)
* `PPUtils key json ` - get a value by the (composite) key, for example: `"a.b.c"`
* `PPUtils scriptArchive Archive.java File1 File2 File3` - Creates a self-extracting archive to a script of Java 17 format.
   File contents are compressed and converted using Base64.
   See an example of the Java archive. To extract all archive files write to console expression: `java Archive.java` .
   Take a look at the Java archive example. 
   To extract all archive files, type the expression in the console
```java
import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.*;
/** @version 2024-04-10T20:13:21.815623436 */
public final class Archive {
    public static void main(String[] args) throws IOException {
        java.util.stream.Stream.of(null
            , new File("temp/hallo.txt", "eJzLSMzJyQcABhwCEQ==")
            , new File("temp/test1.txt", "eJwrSS0uAQAEXQHB")
            , new File("temp/test2.txt", "eJwrSS0uAQAEXQHB")
            , new File("temp/test3.txt", "eJwrSS0uAQAEXQHB")
        ).skip(1).forEach(file -> write(file));
    }
    public static void write(File file) {
        try {
            var path = Path.of(file.path);
            if (path.getParent() != null) Files.createDirectories(path.getParent());
            var base64is = new ByteArrayInputStream(file.base64Body.getBytes(StandardCharsets.US_ASCII));
            var is = new InflaterInputStream(Base64.getDecoder().wrap(base64is), new Inflater());
            try (var os = Files.newOutputStream(path)) {
                var buffer = new byte[1024];
                var length = 0;
                while ((length = is.read(buffer)) != -1) { os.write(buffer, 0, length); }
            }
            System.out.println("Restored: " + path);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to extract file: " + file.path, e);
        }
    }
    record File(String path, String base64Body) {};
}
```

For more information see a source code: [PPUtils.java](../src/main/java/net/ponec/script/PPUtils.java) .