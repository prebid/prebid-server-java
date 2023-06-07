# Build project

To build the project, you will need at least
[Java 11](https://download.java.net/java/GA/jdk11/9/GPL/openjdk-11.0.2_linux-x64_bin.tar.gz)
and [Maven](https://maven.apache.org/) installed.

To verify the installed Java run in console:

```bash
java -version
```

which should show something like (yours may be different):

```
openjdk version "11.0.2" 2019-01-15
OpenJDK Runtime Environment 18.9 (build 11.0.2+9)
OpenJDK 64-Bit Server VM 18.9 (build 11.0.2+9, mixed mode)
```

Follow next steps to create JAR which can be deployed locally.

Download or clone a project locally:

```bash
git clone https://github.com/prebid/prebid-server-java.git
```

Move to project directory:

```bash
cd prebid-server-java
```

And from this step there are two common use cases, which can be chosen depending on your goals.

1. Create prebid-server JAR only:

```bash
mvn clean package
```

2. Create prebid-server JAR with modules included:

```bash
mvn clean package --file extra/pom.xml
```

## Common problems
For IntelliJ IDEA users, if IDEA can't resolve proto classes:
First of all, you need to compile these files. They are compiled from .proto files located in src.main.proto. This can be done by running the mvn protobuf:compile command in your terminal or by clicking in IntelliJ IDEA:
Maven > prebid-server > Plugins > protobuf > protobuf:compile.

This step is also performed during the compile (and test-compile for tests) phase of Maven if you build the app using Maven directly.

After that, you can build and run the application via IntelliJ IDEA. However, IntelliJ IDEA will not recognize these files with its default settings yet, and it will still report an error even though the application can be compiled. This is because intellisense does not introspect large files by default, and the compiled classes from proto files are much larger than the default classes.

Here is how to resolve this:
In IntelliJ IDEA, click `Help > Edit custom properties...` and add modify any of these properties(in kilobytes):
`idea.max.content.load.filesize=your-value-here`(for example, `idea.max.content.load.filesize=200000`) - increases max file size that IDEA is able to open
`idea.max.intellisense.filesize=your-value-here`(for example, `idea.max.intellisense.filesize=200000`) - increases max file size for coding assistance and design-time code inspection.

You can find more information about these properties here: https://www.jetbrains.com/help/objc/configuring-file-size-limit.html
