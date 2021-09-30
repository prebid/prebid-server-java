# Build project

To build the project, you will need at least
[Java 11 JDK](https://docs.aws.amazon.com/corretto/latest/corretto-11-ug/downloads-list.html)
and [Maven](https://maven.apache.org/) installed.

To verify the installed Java run in console:

```bash
java -version
```

which should show something like (yours may be different):

```
openjdk version "11.0.12" 2021-07-20 LTS
OpenJDK Runtime Environment Corretto-11.0.12.7.1 (build 11.0.12+7-LTS)
OpenJDK 64-Bit Server VM Corretto-11.0.12.7.1 (build 11.0.12+7-LTS, mixed mode)
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
