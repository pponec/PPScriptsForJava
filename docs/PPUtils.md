# PPUtils

Services called from the command line for general use.

## Tools and examples of use

- `PPUtils find [regExpContent] regExpFile` - find readable files by regular expressions, partial compliance is assessed,
- `PPUtils grep regExpContent regExpFiles` - find readable file rows by a regular expression.
- `PPUtils grepf regGroupContent formatter regExpFiles` - print formatted rows from the `grep` for a group regular expression. 
   A template formats all regexp groups by the marks `%s`.
   For example: `PPUtils grepf "a-(.*)-(.*)-c" "a:%s, b:%s" a.txt` returns `"a:hello, b:world"`in case, the file contains the row: `a-hello-world-c`.
- `PPUtils date` - prints a date by ISO format, for example: "2023-12-31"
- `PPUtils time` - prints hours and time, for example "2359"
- `PPUtils datetime` - prints datetime format "2023-12-31T2359"
- `PPUtils date-iso` - prints datetime by ISO format, eg: "2023-12-31T23:59:59.999"
- `PPUtils date-format "yyyy-MM-dd'T'HH:mm:ss.SSS"` - prints a time by a custom format
- `PPUtils base64encode "file.bin"` - encode any (binary) file.
- `PPUtils base64decode "file.base64"` - decode base64 encoded file (result removes extension)
- `PPUtils key json` - get a value by the (composite) key, for example: `"a.b.c"`
- `PPUtils archive  Archive.java File1 File2 Dir1 Dir2` - Creates a self-extracting archive in Java class source code format. Recursive directories are supported.</li>
- `PPUtils archive  Archive.java --file FileList.txt` - Creates a self-extracting archive for all files from the file list.</li>
- `PPUtils archive1 Archive.java File1 File2 File3` - Compress the archive to the one row. . Recursive directories are supported.</li>

   File contents are compressed and converted using Base64.
   Optionally, you can put a __single directory__ to the parameter from which all files will be loaded.
   Using the tool is optimal for archiving text files, but in principle it also works for binary files.
   The total size of the **binary** data to be archived should not exceed `300 MB`, otherwise you may run into Java language limits at the time of extracting the archive.
   To extract archive files, type the expression in the console: `java Archive.java`, to extract large archives, use rather the: `java -Xmx4g Archive.java` .
   Take a look at the generated Java archive example.
```java
public final class Archive {
   public static void main(String[] args) {
      Stream.of(null
              , new File("temp/test.txt", "eJzzSMzJyVcIzy/KSQEAF+MEGQ==")
              , new File("temp/message.txt", "eJwLL8osSVXIz0tO1VEoKs1TSMyrLM9ILUpV0NVVCAbyfTOTi/KLK4tLUnOLuQBvohAB")
      ).skip(1).forEach(file -> write(file));
   }
   static void write(File file) {…}
```

For more information see a source code: [PPUtils.java](../src/main/java/net/ponec/script/PPUtils.java) .