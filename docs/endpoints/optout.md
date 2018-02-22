# Opt Out Endpoint

This endpoint is used to decline sending any user targeting information to bidder.

To make an opt out decision user should be redirected to page `http://prebid.site.com/static/optout.html`.
Then check or uncheck "Opt out?" field, pass Google Recaptcha verification and press "Submit" button.

This will make the request like:

`GET http://prebid.site.com/optout?g-recaptcha-response=<VERIFICATION_CODE>&optout=On`

and user will be redirected to the url defined in configuration 
with `host-cookie.opt-out-url` and `host-cookie.opt-in-url` options dependent on its decision.

The response can be `401 Unauthorized` in case of any errors occurs.

This operation is safe and can be performed many times up to user activity.
