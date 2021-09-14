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
git clone https://github.com/prebid/prebid-server-java.git
```

Move to project directory:
```bash
cd prebid-server-java
```

And from this step there are two common use cases, which can be chosen depending on your goals

1. Create prebid-server JAR only
- Run below command to build project:
```bash
mvn clean package
```

2. Create prebid-server JAR with modules
- Run below command to build project:
```bash
mvn clean package --file extra/pom.xml
```
