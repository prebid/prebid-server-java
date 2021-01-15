# Server Based Global Default Request

This allows a default request to be defined that allows the server to set up some defaults for all incoming 
requests. A stored request(s) referenced by a bid request override default request, and of course any options specified 
directly in the bid request override both. The default request is only read on server startup, it is meant as an 
installation static default rather than a dynamic tuning option.

## Config Options

Following config options are exposed to support this feature.
```yaml
default-request:
  file:
    path : path/to/default/request.json
```

The `path` option is the path to a JSON file containing the default request JSON.
```json
{
    "tmax": "2000",
    "regs": {
        "ext": {
            "gdpr": 1
        }
    }
}
```
This will be JSON merged into the incoming requests at the top level. These will be used as fallbacks which can be 
overridden by both Stored Requests _and_ the incoming HTTP request payload.
