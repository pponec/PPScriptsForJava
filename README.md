# Common Script Utilities for Java 17

The project contains several classes with different uses that can be run (without further dependencies) 
as a script (without prior compilation).

## Java Script Class Descriptions

* [DirectoryBookmarks](docs/DirectoryBookmarks.md) - A tool for switching directories in the character terminal using bookmarks. 
* [Mp3PlayerGenerator](docs/Mp3PlayerGenerator.md) - The script builds a music player for your music files in HTML format.
* [SqlParamBuilder](docs/SqlParamBuilder.md) - Script template for working with relational database using JDBC.
* [PPUtils](docs/PPUtils.md) - Services called from the command line for general use (find, grep, json parser, base64 utils and more).

## Example of use

If the project is compiled into a JAR file, the following command can be used:

```bash
java -jar target/script-0.0.1-SNAPSHOT.jar PPUtils datetime
```

The command prints a **date** with **time** in a format suitable for use in file names.

> 2024-03-23T1142

Where `PPUtils` is the name of the Java class that provides the service and `datetime` is an argument.
However, because the `PPUtils`  class can be run as a script (without prior compilation), you can use a next simple command.

```bash
java src/main/java/net/ponec/script/PPUtils.java datetime
```

The script can be run from any directory with an absolute or relative file path.


## License

[Apache License](LICENSE), Version 2.0, [Pavel Ponec](https://github.com/pponec/Mp3PlayerGenerator/)
