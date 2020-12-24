# Enable HTTP interaction logging endpoint

This endpoint has a path `/logging/httpinteraction` by default (can be configured).

This endpoint turns on temporary logging of raw HTTP requests and responses, mainly for troubleshooting production issues. 

Interaction is logged at `INFO` level using `http-interaction` logback logger so make sure this logger has at least 
`INFO` or more verbose level set ([logback configuration](../../../src/main/resources/logback-spring.xml) bundled in JAR 
file sets this logger to `INFO` level).

### Query Params
- `endpoint` - endpoint to be affected; valid values: [auction](../openrtb2/auction.md), [amp](../openrtb2/amp.md); 
if omitted all valid endpoints will be affected
- `statusCode` - specifies that only interactions resulting in this response status code should be logged; 
valid values: >=200 and <=500
- `account` - specifies that only interactions involving this account should be logged
- `limit` - number of interactions to log; there is an upper threshold for this value set in 
[configuration](../../config-app.md) 
