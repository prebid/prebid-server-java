Each of these test requests works with prebid-config-file-bidders.yaml and the files in the samples/stored directory.

You can invoke them with:

curl --header "X-Forwarded-For: 151.101.194.216" -H 'User-Agent: Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_6) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/59.0.3071.115 Safari/537.36' -H 'Referer: https://example.com/demo/' -H "Content-Type: application/json" http://localhost:8080/openrtb2/auction --data @FILENAME

- rubicon-storedresponse.json - this is a request that calls for a stored-auction-response.

- appnexus-disabled-gdpr.json - this is a request that actually calls the appnexus endpoint after disabling GDPR by setting regs.ext.gdpr:0

- pbs-stored-req-test-video.json - this is a stored-request/response chain returning a VAST document

