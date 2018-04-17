# Adding a New Analytics Module

This document describes how to add a new analytics module to Prebid Server.

### 1. Define config params 

Analytics modules are enabled through the [Configuration](../config.md).
 
### 2. Implement your module

Your new module `org.prebid.server.analytics.{module}AnalyticsReporter` needs to implement the `org.prebid.server.analytics.AnalyticsReporter` interface. 
  
### 3. Add your implementation to Spring Context

In order to make Prebid Server aware of the new analytics module it needs to be added to the Spring Context in `org.prebid.server.spring.config.AnalyticsConfiguration` as a bean.

Note: if the new implementation uses Vert.x `HttpClient` or other services that must not be shared between different verticle instances then `CompositeAnalyticsReporter` and `{module}AnalyticsReporter` in `org.prebid.server.spring.config.AnalyticsConfiguration` must have `prototype` scope. 

### Example

The [log](../../src/main/java/org/prebid/server/analytics/LogAnalyticsReporter.java) module is provided as an example. This module will write dummy messages to a log.

It can be configured with:

```yaml
analytics:
  log:
    enabled: true
```

Prebid Server will then write sample log messages to the log.
