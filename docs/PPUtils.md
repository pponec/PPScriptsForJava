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

For more information see a source code: [PPUtils.java](../src/main/java/net/ponec/script/PPUtils.java) .