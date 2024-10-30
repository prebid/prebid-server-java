# Admin enpoints

Prebid Server Java offers a set of admin endpoints for managing and monitoring the server's health, configurations, and
metrics. Below is a detailed description of each endpoint, including HTTP methods, paths, parameters, and responses.

## General settings

Each endpoint can be either enabled or disabled by changing `admin-endpoints.<endoint name>.enabled` toggle. Defaults to
`false`.

Each endpoint can be configured to serve either on application port (configured via `server.http.port` setting) or
admin port (configured via `admin.port` setting) by changing `admin-endpoints.<endpoint name>.on-application-port`
setting.
By default, all admin endpoints reside on admin port.

Each endpoint can be configured to serve on a certain path by setting `admin-endpoints.<endpoint name>.path`.

Each endpoint can be configured to either require basic authorization or not by changing
`admin-endpoints.<endpoint name>.protected` setting,
defaults to `true`. Allowed credentials are globally configured for all admin endpoints with
`admin-endpoints.credentials.<username>`
setting.

## Endpoints

1. Version info

- Name: version
- Endpoint: Configured via `admin-endpoints.version.path` setting
- Methods:
    - `GET`:
        - Description: Returns the version information for the Prebid Server Java instance.
        - Parameters: None
        - Responses:
            - 200 OK: JSON containing version details
            ```json
            {
                "version": "x.x.x",
                "revision": "commit-hash"
            }
           ```

2. Currency rates

- Name: currency-rates
- Methods:
    - `GET`:
        - Description: Returns the latest information about currency rates used by server instance.
        - Parameters: None
        - Responses:
            - 200 OK: JSON containing version details
            ```json
            {
                "active": "true",
                "source": "http://currency-source"
                "fetchingIntervalNs": 200,
                "lastUpdated": "02/01/2018 - 13:45:30 UTC"
                ... Rates ...
            }
           ```

3. Cache notification endpoint

- Name: storedrequest
- Methods:
    - `POST`:
        - Description: Updates stored requests/imps data stored in server instance cache.
        - Parameters:
            - body:
              ```json
              {
                  "requests": {
                      "<request-name-1>": "<body-1>",
                      ... Requests data ...
                  },
                  "imps": {
                      "<imp-name-1>": "<body-1>",
                      ... Imps data ...
                  }
              }
              ``` 
        - Responses:
            - 200 OK
            - 400 BAD REQUEST
            - 405 METHOD NOT ALLOWED
    - `DELETE`:
        - Description: Invalidates stored requests/imps data stored in server instance cache.
        - Parameters:
            - body:
              ```json
              {
                  "requests": ["<request-name-1>", ... Request names ...],
                  "imps": ["<imp-name-1>", ... Imp names ...]
              }
              ``` 
        - Responses:
            - 200 OK
            - 400 BAD REQUEST
            - 405 METHOD NOT ALLOWED

4. Amp cache notification endpoint

- Name: storedrequest-amp
- Methods:
    - `POST`:
        - Description: Updates stored requests/imps data for amp, stored in server instance cache.
        - Parameters:
            - body:
              ```json
              {
                  "requests": {
                      "<request-name-1>": "<body-1>",
                      ... Requests data ...
                  },
                  "imps": {
                      "<imp-name-1>": "<body-1>",
                      ... Imps data ...
                  }
              }
              ``` 
        - Responses:
            - 200 OK
            - 400 BAD REQUEST
            - 405 METHOD NOT ALLOWED
    - `DELETE`:
        - Description: Invalidates stored requests/imps data for amp, stored in server instance cache.
        - Parameters:
            - body:
              ```json
              {
                  "requests": ["<request-name-1>", ... Request names ...],
                  "imps": ["<imp-name-1>", ... Imp names ...]
              }
              ``` 
        - Responses:
            - 200 OK
            - 400 BAD REQUEST
            - 405 METHOD NOT ALLOWED

5. Account cache notification endpoint

- Name: cache-invalidation
- Methods:
    - any:
        - Description: Invalidates cached data for a provided account in server instance cache.
        - Parameters:
            - `account`: Account id.
        - Responses:
            - 200 OK
            - 400 BAD REQUEST


6. Http interaction logging endpoint

- Name: logging-httpinteraction
- Methods:
    - any:
        - Description: Changes request logging specification in server instance.
        - Parameters:
            - `endpoint`: Endpoint. Should be either: `auction` or `amp`.
            - `statusCode`: Status code for logging spec.
            - `account`: Account id.
            - `bidder`: Bidder code.
            - `limit`: Limit of requests for specification to be valid.
        - Responses:
            - 200 OK
            - 400 BAD REQUEST
- Additional settings:
    - `logging.http-interaction.max-limit` - max limit for logging specification limit.

7. Logging level control endpoint

- Name: logging-changelevel
- Methods:
    - any:
        - Description: Changes request logging level for specified amount of time in server instance.
        - Parameters:
            - `level`: Logging level. Should be one of: `all`, `trace`, `debug`, `info`, `warn`, `error`, `off`.
            - `duration`: Duration of logging level (in millis) before reset to original one.
        - Responses:
            - 200 OK
            - 400 BAD REQUEST
- Additional settings:
    - `logging.change-level.max-duration-ms` - max duration of changed logger level.

8. Tracer log endpoint

- Name: tracelog
- Methods:
    - any:
        - Description: Adds trace logging specification for specified amount of time in server instance.
        - Parameters:
            - `account`: Account id.
            - `bidderCode`: Bidder code.
            - `level`: Log level. Should be one of: `info`, `warn`, `trace`, `error`, `fatal`, `debug`.
            - `duration`: Duration of logging specification (in seconds).
        - Responses:
            - 200 OK
            - 400 BAD REQUEST

9. Collected metrics endpoint

- Name: collected-metrics
- Methods:
    - any:
        - Description: Adds trace logging specification for specified amount of time in server instance.
        - Parameters: None
        - Responses:
            - 200 OK: JSON containing metrics data.
