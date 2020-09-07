# Build project

To build the project, you will need at least
[Java 8 JDK](http://www.oracle.com/technetwork/java/javase/downloads/index.html)
and [Maven](https://maven.apache.org/) installed.

To verify the installed Java run in console:
```bash
java -version
```
which should show something like (yours may be different):
```
openjdk version "1.8.0_252"
OpenJDK Runtime Environment (build 1.8.0_252)
OpenJDK 64-Bit Server VM (build 25.252-b09, mixed mode)
```

Follow next steps to create JAR which can be deployed locally. 

Download or clone a project locally:
```bash
git clone https://github.com/rubicon-project/prebid-server-java.git
```

Move to project directory:
```bash
cd prebid-server-java
```

Run below command to create JAR file:
```bash
mvn clean package
```
