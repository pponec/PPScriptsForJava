# DirectoryBookmarks.java

Do you switch between different file system directories often in the character terminal? 
Then you might find the application described in this article useful. 
How does it work? 
In a frequently visited directory, type (on the terminal command line) the command `sdf d1`, where the parameter **d1** is an alphanumeric text representing the **name of the bookmark**. 
From that point on, you can return to the same directory at any later time with the `cdf d1 command`. 
Multiple bookmarks can be assigned to a single directory. 
Bookmark names can be recycled; when the same bookmark is saved again, the original entry is silently overwritten. 
Bookmarks are case-sensitive and are saved in CSV text format (tab separator) in the user's home directory named `.directory-bookmarks.csv`, so they can be easily edited with a regular text editor. 
A typical reason for editing is to change the directory structure, and the aforementioned file can also be useful when transferring a project to another computer.

The original implementation was created 30 years ago (sometime in 1993) - as a script for Unix C-shell. 
After switching to Linux, I modified the script slightly for Bash and I have used it in that form until now, almost every day. 
This year I decided to rewrite the tool in Java because of the originally planned work in Windows character terminal. 
I implemented the new solution in a single file (or more precisely, in a single Java class) called `DirectoryBookmarks.java` so that it could be run in Java 17 without any prior compilation. 
The class offers some new features, but retains the original data format. 
For reasonable use, the tool needs to be integrated into a character terminal environment - only then does it make sense to use the previously mentioned commands.

* `sdf d1 [comment]` : the command name was inspired by the words "Save Directory to a File". The parameter at position **d1** is mandatory and represents the **bookmark name** of the currently visited directory. Optionally, a text **comment** can be added.
* `cdf d1` : the command name was inspired by the words "Change Directory using the bookmark from the File" and switches the user to the directory that is paired with the saved bookmark. Optionally, the bookmark name can be followed by a slash (without a space) and the name of one of the actual subdirectories.
* `ldf` : the command name was inspired by the words "List Directories from the File", the command prints a sorted list of all saved bookmarks, their directories and comments.
* `ldf d1` : If we add the bookmark name to the previous command, we get the path of the paired directory. In Linux, this expression can also be used to copy files, for example. The following example copies all java files in Linux to the directory pointed to by the bookmark named **d1**: `cp *.java $(ldf d1)` .

Performance note: although direct use of a Java class may resemble interpreting a script, compilation is always done in the background. 
On newer machines there is a barely noticeable delay, but on older machines such a delay can be disruptive. 
To eliminate this problem, the class can compile itself and compile the result into an executable JAR file, reducing execution time by an order of magnitude.

Next, I will present a description of selected commands for the character terminal. Depending on the circumstances, it may be necessary to add a path to the files. In the following examples, the first expression represents the Java executable, the second expression denotes the class with the implementation, and the third expression represents the command execution. Commands can optionally be represented by a hyphen (one or more).

* `java DirectoryBookmarks.java i` : (Integrate) - lists the code needed to initialize the shell before the first use. On Ubuntu, the generated code for Bash can be written to the end of the `.bashrc` file. On Windows, the class generates functions for PowerShell, for their automatic initialization you need to find a solution in the documentation. A template for integration with a terminal of type `CMD` can also be found in the project directory. As a reminder, the above abbreviations can only be used after the generated functions have been implemented in the shell. For example, by reopening the character terminal.
* `java DirectoryBookmarks.java b` : (Bookmarks) Lists all bookmarks assigned to the current directory.
* `java DirectoryBookmarks.java r d1` : (Remove) This command removes the bookmark specified by **d1**, leaving the referenced directory unchanged, of course.
* `java DirectoryBookmarks.java f` : (Fix) Removes all bookmarks that point to nonexistent (or invisible) directories.
* `java DirectoryBookmarks.java c` : (Compile) compiles the source code of the current class into a JAR file. The JAR version then needs to be run with a slightly different command, see below.
* `java -jar DirectoryBookmarks.jar i` : (Integrate) - A slightly different initialization is generated for the JAR version of the program.
* `java DirectoryBookmarks.java` : Running without an execute command prints the current version and a full list of available parameters.

The application has been released under the Apache License, Version 2.0 and requires a Java 17 or higher environment to run. 
I have found a similar tool on the Internet, but unfortunately it has not been ported for Windows. 

## Similar projects:

* https://github.com/C-Hess/cd-bookmark
* https://github.com/wting/autojump/wiki
