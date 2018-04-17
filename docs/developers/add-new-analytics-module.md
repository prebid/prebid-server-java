# Adding a New Analytics Module

This document describes how to add a new analytics module to Prebid Server.

### 1. Config: 

The parameters needed to setup the analytics module are sent through `analytics.{module}` configuration section .
 
### 2. Create an implementation

The new analytics module `org.prebid.server.analytics.{module}AnalyticsReporter` needs to implement the `org.prebid.server.analytics.AnalyticsReporter` interface. Implementation of `processEvent(event)` method is responsible for completing the logging of event. 
  
### 3. Add implementation to Spring Context

In order to make Prebid Server aware of the new analytics module it needs to be added to the Spring Context in `org.prebid.server.spring.config.AnalyticsConfiguration` as a bean.

An example of such an analytics module is the `org.prebid.server.analytics.LogAnalyticsReporter`.

Note: if the new implementation uses Vert.x `HttpClient` or other services that must not be shared between different verticle instances then `CompositeAnalyticsReporter` and `{module}AnalyticsReporter` in `org.prebid.server.spring.config.AnalyticsConfiguration` must have `prototype` scope. 