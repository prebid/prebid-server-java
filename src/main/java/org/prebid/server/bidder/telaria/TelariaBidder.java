package org.prebid.server.bidder.telaria;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.telaria.ExtImpOutTelaria;
import org.prebid.server.proto.openrtb.ext.request.telaria.ExtImpTelaria;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.util.Objects;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;

public class TelariaBidder implements Bidder<BidRequest> {
    private static final String DEFAULT_BID_CURRENCY = "USD";
    private static final TypeReference<ExtPrebid<?, ExtImpTelaria>> TELARIA_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpTelaria>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public TelariaBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<BidRequest>>> makeHttpRequests(BidRequest bidRequest) {
        final List<BidderError> errors = new ArrayList<>();
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            return Result.emptyWithError(BidderError.badInput("Telaria: Missing Imp Object"));
        }
        try {
            validateImp(bidRequest.getImp());
        } catch (PreBidException e) {
            return Result.emptyWithError(BidderError.badInput(e.getMessage()));
        }

        final String publisherId = getPublisherId(bidRequest);
        String seatCode = null;
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
        for (Imp imp : bidRequest.getImp()) {
            try {
                final ExtImpTelaria extImp = parseImpExt(imp);
                if (StringUtils.isBlank(extImp.getSeatCode())) {
                    throw new PreBidException("Telaria: Seat Code required");
                }
                seatCode = extImp.getSeatCode();
                requestBuilder
                        .imp(Collections.singletonList(imp.toBuilder()
                                .tagid(extImp.getAdCode())
                                .ext(mapper.mapper().valueToTree(ExtImpOutTelaria.of(imp.getTagid(), publisherId)))
                                .build()));
            } catch (PreBidException e) {
                errors.add(BidderError.badInput(e.getMessage()));
            }
        }

        if (bidRequest.getSite() != null) {
            modifySite(seatCode, bidRequest, requestBuilder);
        } else if (bidRequest.getApp() != null) {
            modifyApp(seatCode, bidRequest, requestBuilder);
        }

        final BidRequest outgoingRequest = requestBuilder.build();
        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(headers(bidRequest))
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                errors);
    }

    private void validateImp(List<Imp> imps) {
        for (Imp imp : imps) {
            if (imp.getVideo() == null) {
                throw new PreBidException("Telaria: Only Supports Video");
            }
        }
    }

    private String getPublisherId(BidRequest bidRequest) {
        if (bidRequest.getSite() != null && bidRequest.getSite().getPublisher() != null) {
            return bidRequest.getSite().getPublisher().getId();
        } else if (bidRequest.getApp() != null && bidRequest.getApp().getPublisher() != null) {
            return bidRequest.getApp().getPublisher().getId();
        }
        return "";
    }

    private ExtImpTelaria parseImpExt(Imp imp) {
        try {
            return mapper.mapper().convertValue(imp.getExt(), TELARIA_EXT_TYPE_REFERENCE).getBidder();
        } catch (IllegalArgumentException e) {
            throw new PreBidException(e.getMessage(), e);
        }
    }

    private static void modifySite(String seatCode, BidRequest bidRequest,
                                   BidRequest.BidRequestBuilder bidRequestBuilder) {
        final Site site = bidRequest.getSite();
        if (site.getPublisher() != null) {
            final Publisher modifiedPublisher = site.getPublisher().toBuilder().id(seatCode).build();
            bidRequestBuilder.site(site.toBuilder().publisher(modifiedPublisher).build());
        }
    }

    private static void modifyApp(String seatCode, BidRequest bidRequest,
                                  BidRequest.BidRequestBuilder bidRequestBuilder) {
        final App app = bidRequest.getApp();
        if (app.getPublisher() != null) {
            final Publisher modifiedPublisher = app.getPublisher().toBuilder().id(seatCode).build();
            bidRequestBuilder.app(app.toBuilder().publisher(modifiedPublisher).build());
        }
    }

    private MultiMap headers(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers().add("x-openrtb-version", "2.5");
        final Device device = bidRequest.getDevice();
        if (device != null) {
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.USER_AGENT_HEADER, device.getUa());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_FORWARDED_FOR_HEADER, device.getIp());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.ACCEPT_LANGUAGE_HEADER, device.getLanguage());
            HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.DNT_HEADER, Objects.toString(device.getDnt(), null));
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<BidRequest> httpCall, BidRequest bidRequest) {
        final int statusCode = httpCall.getResponse().getStatusCode();
        if (statusCode == HttpResponseStatus.NO_CONTENT.code()) {
            return Result.of(Collections.emptyList(), Collections.emptyList());
        } else if (statusCode == HttpResponseStatus.BAD_REQUEST.code()) {
            return Result.emptyWithError(BidderError.badInput("Invalid request."));
        } else if (statusCode != HttpResponseStatus.OK.code()) {
            return Result.emptyWithError(BidderError.badServerResponse(String.format("Unexpected HTTP status %s.",
                    statusCode)));
        }

        try {
            return Result.of(extractBids(httpCall.getRequest().getPayload(), getBidResponse(httpCall.getResponse())),
                    Collections.emptyList());
        } catch (DecodeException | PreBidException e) {
            return Result.emptyWithError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse == null || bidResponse.getSeatbid() == null
                ? Collections.emptyList()
                : bidsFromResponse(bidRequest, bidResponse);
    }

    private BidResponse getBidResponse(HttpResponse response) {
        if ("gzip".equals(response.getHeaders().get("Content-Encoding"))) {
            response.getHeaders().remove("Content-Encoding");
            return mapper.decodeValue(Buffer.buffer(response.getBody()), BidResponse.class);
        }
        return mapper.decodeValue(response.getBody(), BidResponse.class);
    }

    private static List<BidderBid> bidsFromResponse(BidRequest bidRequest, BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.video, DEFAULT_BID_CURRENCY))
                .collect(Collectors.toList());
    }

    @Override
    public Map<String, String> extractTargeting(ObjectNode ext) {
        return Collections.emptyMap();
    }
}
