# DirectoryBookmarks.java

https://github.com/pponec/PPScriptsForJava/assets/5459216/c7df34c4-c572-43f0-a247-2b27074ff7f5

Do you often switch between different directories of the file system in the character terminal? 
Then you might find the application described in this project useful.
How does it work? 
In the currently visited directory you can type (in the terminal) the command `sdf d1`, where the parameter `d1` is an alphanumeric text representing the name of the bookmark.
From that point on, the same directory can be returned to at any later time with the `cdf d1` command. 
Multiple bookmarks can be assigned to a single directory. 
Used bookmark names can be recycled: when the same bookmark is saved again, the original entry is silently overwritten. 
Bookmarks are case-sensitive and are saved in CSV text format (tab delimiter) in the user's home directory named `.directory-bookmarks.csv`, so they can be easily edited with a regular text editor. 
A typical reason for editing might be to change the directory structure, but the file can also be useful when transferring a project to another computer.

The original implementation was created 30 years ago (sometime during 1993) - as a Unix C-shell script. 
After switching to Linux, I modified the script slightly for Bash and have used it in that form until now, practically every working day. 
This year I decided to rewrite the tool in Java for my originally planned Windows work. 
I then implemented the new solution in a single file (or more precisely, in a single Java class) called `DirectoryBookmarks.java` so that it could be run in Java 17 even without prior compilation. 
The class accepts the original data format, but some functions have been added. 
It makes sense to start using the above shortcuts (of application commands) after integrating it into the character terminal environment. 
Recall them:

* `sdf d1 [comment]` : the command name was inspired by the words "Save Directory to a File". 
  The parameter at position `d1` is mandatory and represents the bookmark name of the currently visited directory. 
  Optionally, a text comment can be added.
* `cdf d1` : the command name was inspired by the words "Change Directory using the bookmark from the File" and switches the user to the directory that is paired with the saved bookmark. 
  Optionally, the bookmark name can be followed by a slash (without a space) and the name of one of the actual subdirectories. 
  Example: `cdf d1/mySubdirectory`.
* `ldf` : the name of the command was inspired by the words "List Directories from the File", the command prints a sorted list of all the bookmarks stored in the CSV file, including their directories and comments.
* `ldf d1` : If we add the bookmark name to the previous command, we get the path of the paired directory. 
  In Linux, this expression can also be used for copying files, for example. 
  In Linux, the following example copies all java files from the directory labeled d1 to the directory labeled d2: `cp $(ldf d1)/*.java $(ldf d2)`.
* `cpf f1 d1` : copy a file name `f1` (multiple files are allowed) to the target directory marked with the name `d1`. 
   The function does not perform the copying itself, but delegates it to a standard operating system command.
   This is a simplified notation of the `cp f1 $(ldf d1)` command.

Performance note: although direct use of a Java class may resemble interpreting a script, compilation is always done in the background. 
On newer machines, there is a barely noticeable delay, but on older machines such a delay can be distracting. 
To eliminate this problem, the class can compile itself and compile the result into an executable JAR file, reducing execution time by an order of magnitude. 
Starting Java with only the kernel module parameter had no measurable effect on the result.

Next, I will present a description of selected commands for the character terminal. 
Depending on the circumstances, it may be necessary to add a path to the files. 
In the following examples, the first expression represents the Java executable file, the second expression denotes the class with the implementation, and the third expression represents the implementation command. 
Commands can optionally be represented by a hyphen (one or more). 
You can, of course, prepare a command shortcut for each of these commands using a script or function.

* `java DirectoryBookmarks.java i` : (Integrate) - lists the code needed to initialize the shell before the first use. On Ubuntu, the generated code for Bash can be written to the end of the `.bashrc` file. On Windows, the class generates functions for PowerShell, for their automatic initialization you need to find a solution in the documentation. A template for integration with a terminal of type `CMD` can also be found in the project directory. As a reminder, the above abbreviations can only be used after the generated functions have been implemented in the shell. For example, by reopening the character terminal.
* `java DirectoryBookmarks.java b` : (Bookmarks) Lists all bookmarks assigned to the current directory.
* `java DirectoryBookmarks.java r d1` : (Remove) This command removes the bookmark specified by `d1`, leaving the referenced directory unchanged, of course.
* `java DirectoryBookmarks.java f` : (Fix) Removes all bookmarks that point to nonexistent (or invisible) directories.
* `java DirectoryBookmarks.java c` : (Compile) compiles the source code of the current class into a JAR file. 
The JAR version then needs to be run with a slightly different command, see below.
* `java -jar DirectoryBookmarks.jar i` : (Integrate) - A slightly different initialization is generated for the JAR version of the program.
* `java DirectoryBookmarks.java` : running without the "execute command" prints the current version and a list of available parameters.

The application has been released under the [Apache License, Version 2.0](https://www.apache.org/licenses/LICENSE-2.0) and requires Java 17 or higher to run.
I have already found similar tools on the Internet, but they either did not meet my expectations or were not portable to Windows.

## How to integrate the application

1. Install Java17+.
2. Download the file [DirectoryBookmarks.java](../src/main/java/net/ponec/script/DirectoryBookmarks.java) to a local directory 
3. Open a character terminal and run commands depending on the operating system.

### For the Ubuntu:

1. `java DirectoryBookmarks.java c`
2. `java -jar DirectoryBookmarks.jar i >> ~/.bashrc`
3. Re-open the terminal emulator.

**Note:** The easiest way for Ubuntu is to download and run the installation script [installDirectoryBookmarks.sh](../src/main/java/net/ponec/script/installDirectoryBookmarks.sh).

### For the Windows PowerShell:

1. `mkdir $HOME\Documents\WindowsPowerShell`
2. `java DirectoryBookmarks.java c`
3. `java -jar DirectoryBookmarks.jar i >> $HOME\Documents\WindowsPowerShell\Microsoft.PowerShell_profile.ps1`
4. `Set-ExecutionPolicy -Scope CurrentUser -ExecutionPolicy Unrestricted`
5. Re-open the PowerShell console.

### For the Windows Command Prompt:

1. `mkdir %USERPROFILE%\bin`
2. `cd %USERPROFILE%\bin`
3.  Move the file `DirectoryBookmarks.java` to the current directory.
3. `java DirectoryBookmarks.java c`
4.  Copy the file [init.bat](../windows/init.bat) to the directory `$HOME/bin`. 
5.  Open `regedit` and follow the instructions in the file header.
6.  Modify path to Java and optionally remove unnecessary auxiliary functions.

### For the Windows GitBash (Bash emulator):

1. Follow the instructions (1-4) for **Command Prompt**.
2. Instal the **GitBash** from the [project page](https://gitforwindows.org/).
3. Copy the file [.profile](../windows/.profile) to the directory `$HOME`.
4. Modify path to Java and optionally remove unnecessary auxiliary functions.


## Similar projects:

* https://github.com/C-Hess/cd-bookmark
* https://github.com/huyng/bashmarks
* https://github.com/wting/autojump/wiki
