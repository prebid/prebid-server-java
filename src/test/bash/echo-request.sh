# Should issue a single request to localhost:5150/bid
curl -X POST \
 -H "Content-Type: application/json;charset=UTF-8" \
 -d '{"account_id":"6655321","timeout_millis":1000,"ad_units":[{"code":"edoc","sizes":[{"h":250,"w":300}],"bids":[{"bid_id":"dead-beef","bidder":"http://localhost:5150/bid","params":{"cle":"valeur","clave":"valor","kagi":"atai"}}],"app":{"id":"hello-app","name":"Hello App","ext":{"key":"value"}}}]}' \
 'http://localhost:8080/echo' 
