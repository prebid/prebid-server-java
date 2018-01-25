## Creating vexing server .zip and deploying it to AWS Elastic Beanstalk

Follow next steps to create zip which can be deployed to AWS Elastic Beanstalk 

Download or clone a project:
```
git clone
```
Move to project "vexing" directory:
```
cd vexing
```
Run below command to build project:

```mvn clean package```

Create next configuration files:

- ```Procfile``` 

Create Procfile which tells how to start application. For example, if run.sh starts vexing server content should be:

```
nano Procfile
```

```
web: ./run.sh
```

where ./run.sh - script is used for running vexing server. How to create this file will be described below.

- ```prebid-logging.properties``` 

Create prebid-logging.properties file with configurations for logger:

```
handlers=java.util.logging.FileHandler
java.util.logging.FileHandler.level=FINEST
java.util.logging.FileHandler.formatter=io.vertx.core.logging.impl.VertxLoggerFormatter

java.util.logging.FileHandler.pattern=/var/log/vexing.log

.level=INFO
org.rtb.vexing.level=FINEST
```

- ```pbs.json```

Create pbs.json file with vexing server configurations for server host, port, adapters configurations, cache, metrics and others.
To create this file you can use ```vexing/targer/classes/default-conf.json``` as an example. If some property will be missed in created pbs.json application will look for it in default-conf.json

- ```.ebextensions```

Create hidden directory .ebextensions:

```
mkdir .ebextensions

```

Move to directory .ebextensions

```
cd .ebextensions
```

Create next file inside .ebextensions:

- server-logs.config

server-logs.config file is used to configure tasks for tail logs, bundle logs, and log rotation.

With content:
```
files:
  "/opt/elasticbeanstalk/tasks/taillogs.d/prebid-server.conf" :
    mode: "000755"
    owner: root
    group: root
    content: |
      /var/log/vexing.log

  "/opt/elasticbeanstalk/tasks/bundlelogs.d/prebid-server.conf" :
    mode: "000755"
    owner: root
    group: root
    content: |
      /var/log/vexing.log

  "/opt/elasticbeanstalk/tasks/publishlogs.d/prebid-server.conf" :
    mode: "000755"
    owner: root
    group: root
    content: |
      /var/log/vexing.log

```

After configuration files was created, move back to vexing directory and create run.sh script which will start vexing server

```
cd ..
nano run.sh

```
With next content:
```
NUMBER_OF_VERTICLES=$(nproc)
exec java -Djava.util.logging.config.file=$LOGGIN_FILE -jar vexing-0.0.1-SNAPSHOT-fat.jar -conf $PBS_JSON --instances $NUMBER_OF_VERTICLES
```

where 

- NUMBER_OF_VERTICLES - processors number in machine where server will be deployed and started
- $LOGGIN_FILE - file with configuration for logger (prebid-logging.properties from example above)
- $PBS_JSON - file with vexing server configuration (pbs.json from example above)

If you follow same naming convention, your run.sh script should be similar to:

```
NUMBER_OF_VERTICLES=$(nproc)
exec java -Djava.util.logging.config.file=prebid-logging.properties -jar vexing-0.0.1-SNAPSHOT-fat.jar -conf pbs.json --instances $NUMBER_OF_VERTICLES
```

Make run.sh executable using next command:

```
chmod +x run.sh  

```

Create zip file with next command:

```
zip -j $ZIPFILE  target/vexing-0.0.1-SNAPSHOT-fat.jar $PBS_JSON $PROCFILE $LOGGIN_FILE $RUN_SH

```

where 
- $ZIPFILE - name for zip file will be create by the command above
- $PROCFILE - path to file describes how to start vexing-server (Procfile from example above)
- $LOGGIN_FILE - path to file with configuration for logger (prebid-logging.properties from example above)
- $PBS_JSON - path to file with vexing server configuration (pbs.json from example above)
- $RUN_SH - script to start application (run.sh from example above)

If you follow same naming convention, your command should be similar to:

```
zip -j vexing.zip  target/vexing-0.0.1-SNAPSHOT-fat.jar pbs.json Procfile prebid-logging.properties run.sh

```

Save vexing root directory to env variable

```
export ROOT_DIR=$(pwd)
```

Add ```.ebextensions``` to created in previous step zip archive using command:

```
zip -ur "$ROOT_DIR/$ZIPFILE" .ebextensions

```

Deploy created zip-file to selected Beanstalk environment
