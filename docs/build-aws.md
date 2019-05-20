## Creating project ZIP package and deploying it to AWS Elastic Beanstalk

Follow next steps to create zip which can be deployed to AWS Elastic Beanstalk.

Build project as described [here](build.md).

Create next configuration files:

- ```prebid-config.yaml```
- ```prebid-logging.xml```

The content of these files can be found [here](config.md).

- ```Procfile```

Create ```Procfile``` which tells how to start application:
```
nano Procfile
```

For example, if run.sh starts the server content should be:
```
web: ./run.sh
```
where ```./run.sh``` - script is used for running the server.
How to create this file will be described below.

Create ```run.sh``` script which will start the server:
```
nano run.sh
```

With the next content:
```
exec java -Dlogging.config=$LOGGING_FILE -jar prebid-server.jar --spring.config.additional-location=$CONFIG_FILE
```
where
- $LOGGING_FILE - file with configuration for logger (```prebid-logging.xml``` from example above)
- $CONFIG_FILE - file with prebid server configuration (```prebid-config.yaml``` from example above)

If you follow same naming convention, your run.sh script should be similar to:
```
exec java -Dlogging.config=prebid-logging.xml -jar prebid-server.jar --spring.config.additional-location=prebid-config.yaml
```

Make run.sh executable using the next command:
```
chmod +x run.sh
```

- ```.ebextensions```

Create hidden directory ```.ebextensions```:
```
mkdir .ebextensions
```

Move to directory ```.ebextensions```:
```
cd .ebextensions
```

Create next file inside ```.ebextensions```:
- ```server-logs.config```

With content:
```
files:
  "/opt/elasticbeanstalk/tasks/taillogs.d/prebid-server.conf" :
    mode: "000755"
    owner: root
    group: root
    content: |
      /var/log/prebid.log

  "/opt/elasticbeanstalk/tasks/bundlelogs.d/prebid-server.conf" :
    mode: "000755"
    owner: root
    group: root
    content: |
      /var/log/prebid.log

  "/opt/elasticbeanstalk/tasks/publishlogs.d/prebid-server.conf" :
    mode: "000755"
    owner: root
    group: root
    content: |
      /var/log/prebid.log

```
It is used to configure tasks for tail logs, bundle logs, and log rotation.

Move back to the project root directory and create zip file:
```
cd ..
zip -j $ZIPFILE target/prebid-server.jar $CONFIG_FILE $PROCFILE $LOGGING_FILE $RUN_SH
```
where 
- $ZIPFILE - name for zip file will be create by the command above
- $PROCFILE - path to file describes how to start prebid-server (```Procfile``` from example above)
- $LOGGING_FILE - path to file with configuration for logger (```prebid-logging.xml``` from example above)
- $CONFIG_FILE - path to file with prebid server configuration (```prebid-config.yaml``` from example above)
- $RUN_SH - script to start application (```run.sh``` from example above)

If you follow same naming convention, your command should be similar to:
```
zip -j prebid-server.zip target/prebid-server.jar prebid-config.yaml Procfile prebid-logging.xml run.sh
```

Save project root directory to env variable:
```
export ROOT_DIR=$(pwd)
```

Add ```.ebextensions``` to created in previous step zip archive using the command:
```
zip -ur "$ROOT_DIR/$ZIPFILE" .ebextensions
```

Deploy created zip-file to the selected Beanstalk environment.
