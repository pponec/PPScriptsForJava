# PPUtils

Services called from the command line for general use.

## Tools and examples of use

- `PPUtils find [regExpContent] regExpFile` - find readable files by regular expressions, partial compliance is assessed,
- `PPUtils grep regExpContent regExpFile` - find readable file rows by a regular expression.
- `PPUtils date` - prints a date by ISO format, for example: "2023-12-31"
- `PPUtils time` - prints hours and time, for example "2359"
- `PPUtils datetime` - prints datetime format "2023-12-31T2359"
- `PPUtils date-iso` - prints datetime by ISO format, eg: "2023-12-31T23:59:59.999"
- `PPUtils date-format "yyyy-MM-dd'T'HH:mm:ss.SSS"` - prints a time by a custom format
- `PPUtils base64encode "file.bin"` - encode any (binary) file.
- `PPUtils base64decode "file.base64"` - decode base64 encoded file (result removes extension)
- `PPUtils key json ` - get a value by the (composite) key, for example: `"a.b.c"`
- `PPUtils scriptArchive Archive.java File1 File2 File3` - Creates a self-extracting archive to a script of Java 17 format.
   File contents are compressed and converted using Base64.
   Using the tool is optimal for archiving text files.
   The total size of the binary data to be archived should not exceed 300 MB, otherwise the compiled Java archive may hit the limits of the Java language when unpacking. 
   <br/>
   To extract archive files, type the expression in the console: `java Archive.java`, to extract large archives, use the: `java -Xmx4g Archive.java` .
   <br/>
   Take a look at the Java archive example.
```java
public final class Archive {
   public static void main(String[] args) {
      Stream.of(null
              , new File("temp/test.txt", "eJzzSMzJyVcIzy/KSQEAF+MEGQ==")
      ).skip(1).forEach(file -> write(file));
   }
   static void write(File file) {
      try {
         var path = Path.of(file.path);
         if (path.getParent() != null) Files.createDirectories(path.getParent());
         var base64is = new Base64InputStream(file.base64Body);
         var is = new InflaterInputStream(Base64.getDecoder().wrap(base64is), new Inflater());
         try (var os = new PrintStream(Files.newOutputStream(path))) { is.transferTo(os); }
         System.out.println("Restored: " + path);
      } catch (IOException e) {
         throw new IllegalArgumentException("Failed to extract file: " + file.path, e);
      }
   }
   record File(String path, String... base64Body) {}
   static final class Base64InputStream extends InputStream { … }
}
```

For more information see a source code: [PPUtils.java](../src/main/java/net/ponec/script/PPUtils.java) .