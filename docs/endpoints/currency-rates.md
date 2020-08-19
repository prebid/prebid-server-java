# Currency Rates

Unavailable if currency conversion is disabled (`currency-converter.external-rates.enabled` config property).

This endpoint will return a json with the latest update timestamp.

The timestamp will be in the ISO-8601 format, using UTC.

Possible response example:

```json
{
  "last_update":"2018-11-06T19:25:48.085Z"
}
```