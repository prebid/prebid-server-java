-- Issue a POST request to the pre-plex server
-- wrk -t4 -c200 -d60s -R100 --latency -s post.lua http://127.0.0.1:8080/auction
wrk.method = 'POST'
wrk.body = '{"account_id":"6655321","timeout_millis":1000,"ad_units":[{"code":"edoc","sizes":[{"h":250,"w":300}],"bids":[{"bid_id":"dead-beef","bidder":"http://localhost:5150/bid","params":{"cle":"valeur","clave":"valor","kagi":"atai"}}],"app":{"id":"hello-app","name":"Hello App","ext":{"key":"value"}}}]}'
wrk.headers['Content-Type'] = 'application/json;charset=UTF-8'
