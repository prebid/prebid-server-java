## Creating project ZIP package and deploying it to AWS Elastic Beanstalk

Follow next steps to create zip which can be deployed to AWS Elastic Beanstalk.

Build project as described [here](build.md).
Then get `prebid-server.jar` file from generated `target` directory

Create next configuration file:

- `prebid-logging.xml`

The content of these file can be found [here](config.md).

Copy `sample` directory from project root
(it will contain two files)

- `prebid-config.yaml`
- `sample-app-settings.yaml`

Create `Procfile` which tells how to start application:
```bash
nano Procfile
```

For example, if run.sh starts the server content should be:
```
web: ./run.sh
```
where `./run.sh` - script is used for running the server.
How to create this file will be described below.

Create `run.sh` script which will start the server:
```bash
nano run.sh
```

With the next content:
```
exec java -jar prebid-server.jar -Dlogging.config=$LOGGING_FILE --spring.config.additional-location=$CONFIG_FILE
```
where
- $LOGGING_FILE - file with configuration for logger (`prebid-logging.xml` from example above)
- $CONFIG_FILE - file with prebid server configuration (`prebid-config.yaml` from `sample` directory above)

If you follow same naming convention, your `run.sh` script should be similar to:
```
exec java -jar prebid-server.jar -Dlogging.config=prebid-logging.xml  --spring.config.additional-location=sample/prebid-config.yaml
```

Make run.sh executable using the next command:
```bash
chmod +x run.sh
```

- `.ebextensions`

Create hidden directory `.ebextensions`:
```bash
mkdir .ebextensions
```

Move to directory `.ebextensions`:
```
cd .ebextensions
```

Create next file inside `.ebextensions`:
- `server-logs.config`

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
```bash
cd ..
zip -j $ZIPFILE target/prebid-server.jar $PROCFILE $LOGGING_FILE $RUN_SH
```
where 
- $ZIPFILE - name for zip file will be create by the command above
- $PROCFILE - path to file describes how to start prebid-server (`Procfile` from example above)
- $LOGGING_FILE - path to file with configuration for logger (`prebid-logging.xml` from example above)
- $CONFIG_FILE - path to file with prebid server configuration (`prebid-config.yaml` from `sample` directory above)
- $RUN_SH - script to start application (`run.sh` from example above)

If you follow same naming convention, your command should be similar to:
```bash
zip -j prebid-server.zip prebid-server.jar Procfile prebid-logging.xml run.sh
```

Save project root directory to env variable:
```bash
export ROOT_DIR=$(pwd)
```

Add `sample` to created in previous step zip archive using the command:
```bash
zip -ur "$ROOT_DIR/$ZIPFILE" sample
```

Add `.ebextensions` to zip archive using the command:
```bash
zip -ur "$ROOT_DIR/$ZIPFILE" .ebextensions
```

Deploy created zip-file to the selected Beanstalk environment.
