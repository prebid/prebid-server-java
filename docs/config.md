# Configuration

Configuration is handled by [Spring Boot](https://docs.spring.io/spring-boot/docs/current/reference/html/boot-features-external-config.html), 
which supports properties files, YAML files, environment variables and command-line arguments for setting config values.

As a general rule, Prebid Server will immediately fails on startup if any of required properties is missing or invalid.

The next sections describes how to set up project configuration.

## Application properties

Prebid Server configuration stands for server host, port, bidder configurations, cache, stored requests, metrics and others.

The project default configuration is located at `src/main/resources/application.yaml` file. 

These properties can be extended/modified with external configuration file. 
For example `prebid-config.yaml`:
```yaml
metrics:
  graphite:
    host: graphite
    port: 3003
    interval: 60
settings:
  database:
    type: mysql
    stored-requests-query: SELECT reqid, requestData, 'request' as dataType FROM stored_requests WHERE reqid IN (%REQUEST_ID_LIST%) UNION ALL SELECT impid, impData, 'imp' as dataType FROM stored_imps WHERE impid IN (%IMP_ID_LIST%)
    amp-stored-requests-query: SELECT reqid, requestData, 'request' as dataType FROM stored_requests WHERE reqid IN (%REQUEST_ID_LIST%)
```
If some property is missed in `prebid-config.yaml` application will look for it in `src/main/resources/application.yaml` file.

To use external application configuration just add the following as start up arguments:
```
--spring.config.additional-location=/path/to/prebid-config.yaml
```

## Logging properties

Default logger properties can be found at `src/main/resources/logback-spring.xml` file.

These properties can be extended/modified with external configuration file. 
For example `prebid-logging.xml`:
```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>
    <property name="LOG_FILE" value="${LOG_PATH}/prebid-server.log"/>
    <include resource="org/springframework/boot/logging/logback/file-appender.xml"/>
    <root level="INFO">
        <appender-ref ref="FILE"/>
    </root>
</configuration>
```

To use external logging configuration just add the following as start up arguments to java executable:
```
-Dlogging.config=/path/to/prebid-logging.xml
```

Full list of application configuration options can be found [here](config-app.md).

### See also

- [How to run Prebid Server](run.md)
