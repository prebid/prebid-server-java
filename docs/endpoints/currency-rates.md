# Currency Rates

Unavailable if currency conversion is disabled (`currency-converter.external-rates.enabled` config property).

This endpoint will return a json with the the following information:
- `active` - true if currency conversion is enabled
- `source` - URL from which rates are fetched
- `fetchingIntervalNs` - fetching interval from source in nanoseconds
- `lastUpdated` - timestamp when the rates where updated (in the ISO-8601 format, using UTC)
- `rates` - internal rates values

Sample response:

```json
{
  "active": true,
  "source": "https://cdn.jsdelivr.net/gh/prebid/currency-file@1/latest.json",
  "fetchingIntervalNs": 60000000000,
  "lastUpdated":"2018-11-06T19:25:48.085Z",
  "rates": {
    "GBP": {
      "AUD": 1.8611576401
     },
    "USD": {
      "AUD": 1.4056048493
    }
  }
}
```