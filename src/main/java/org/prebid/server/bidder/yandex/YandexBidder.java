package org.prebid.server.bidder.yandex;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.util.HttpUtil;

public class YandexBidder implements Bidder<BidRequest> {
    private final String endpointUrl;
    private final JacksonMapper mapper;

    public YandexBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }


    /*
    curl 'https://bs-metadsp.yandex.ru/metadsp/346580?imp-id=143&ssp-id=10500'
     -H 'authority: bs-metadsp.yandex.ru'
     -H 'pragma: no-cache'
     -H 'cache-control: no-cache'
     -H 'sec-ch-ua: "Chromium";v="92", " Not A;Brand";v="99", "Yandex";v="92"'
     -H 'sec-ch-ua-mobile: ?0'
     -H 'user-agent: Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/92.0.4515.131 YaBrowser/21.8.0.1967 (beta) Yowser/2.5 Safari/537.36'
     -H 'content-type: text/plain'
     -H 'accept: *\/*'
     -H 'origin: https://rn.by'
     -H 'sec-fetch-site: cross-site'
     -H 'sec-fetch-mode: cors'
     -H 'sec-fetch-dest: empty'
     -H 'referer: https://rn.by/'
     -H 'accept-language: ru,en;q=0.9,da;q=0.8,tr;q=0.7'
      --data-raw '{"id":"224c3086a3a50f","imp":[{"id":143,"banner":{"w":300,"h":250}}],"site":{"page":"rn.by"}}'
      --compressed
     */
    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest request) {
//        final List<HttpRequest<BidRequest>> bidRequests = new ArrayList<>();
//        final List<BidderError> errors = new ArrayList<>();
//
//        for (Imp imp : request.getImp()) {
//            HttpRequest.<BidRequest>builder()
//                    .method(HttpMethod.POST)
//                    .uri(endpointUrl)
//                    .headers(HttpUtil.headers())
//                    .payload(request)
//                    .body(mapper.encode(request))
//                    .build()), Collections.emptyList());
//        }
//
        return null;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        return null;
    }
}
