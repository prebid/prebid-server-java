package org.prebid.server.bidder.telaria;

import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.response.BidResponse;
import com.iab.openrtb.response.SeatBid;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.MultiMap;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.HttpResponse;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.bidder.telaria.model.TelariaRequestExt;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.telaria.ExtImpOutTelaria;
import org.prebid.server.proto.openrtb.ext.request.telaria.ExtImpTelaria;
import org.prebid.server.proto.openrtb.ext.response.BidType;
import org.prebid.server.util.HttpUtil;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

public class TelariaBidder implements Bidder<BidRequest> {

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
        final List<Imp> validImps = new ArrayList<>();
        if (CollectionUtils.isEmpty(bidRequest.getImp())) {
            return Result.withError(BidderError.badInput("Telaria: Missing Imp Object"));
        }
        try {
            validateImp(bidRequest.getImp());
        } catch (PreBidException e) {
            return Result.withError(BidderError.badInput(e.getMessage()));
        }

        final String publisherId = getPublisherId(bidRequest);
        String seatCode = null;
        final BidRequest.BidRequestBuilder requestBuilder = bidRequest.toBuilder();
        ExtImpTelaria extImp = null;
        for (Imp imp : bidRequest.getImp()) {
            try {
                extImp = parseImpExt(imp);
                seatCode = extImp.getSeatCode();
                validImps.add(updateImp(imp, extImp, publisherId));
            } catch (PreBidException e) {
                return Result.withError(BidderError.badInput(e.getMessage()));
            }
        }

        if (extImp != null && extImp.getExtra() != null) {
            requestBuilder.ext(mapper.fillExtension(ExtRequest.empty(), TelariaRequestExt.of(extImp.getExtra())));
        }

        if (bidRequest.getSite() != null) {
            requestBuilder.site(modifySite(seatCode, bidRequest.getSite()));
        } else if (bidRequest.getApp() != null) {
            requestBuilder.app(modifyApp(seatCode, bidRequest.getApp()));
        }

        final BidRequest outgoingRequest = requestBuilder.imp(validImps).build();
        final String body = mapper.encode(outgoingRequest);

        return Result.of(Collections.singletonList(
                HttpRequest.<BidRequest>builder()
                        .method(HttpMethod.POST)
                        .uri(endpointUrl)
                        .headers(headers(bidRequest))
                        .payload(outgoingRequest)
                        .body(body)
                        .build()),
                Collections.emptyList());
    }

    private void validateImp(List<Imp> imps) {
        boolean hasVideoObject = false;
        for (Imp imp : imps) {
            if (imp.getBanner() != null) {
                throw new PreBidException("Telaria: Banner not supported");
            }
            hasVideoObject = hasVideoObject || imp.getVideo() != null;
        }

        if (!hasVideoObject) {
            throw new PreBidException("Telaria: Only Supports Video");
        }
    }

    private Imp updateImp(Imp imp, ExtImpTelaria extImp, String publisherId) {
        if (StringUtils.isBlank(extImp.getSeatCode())) {
            throw new PreBidException("Telaria: Seat Code required");
        }
        return imp.toBuilder()
                .tagid(extImp.getAdCode())
                .ext(mapper.mapper().valueToTree(ExtImpOutTelaria.of(imp.getTagid(), publisherId)))
                .build();
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

    private Site modifySite(String seatCode, Site site) {
        return site.toBuilder().publisher(createPublisher(seatCode, site.getPublisher())).build();
    }

    private App modifyApp(String seatCode, App app) {
        return app.toBuilder().publisher(createPublisher(seatCode, app.getPublisher())).build();
    }

    private Publisher createPublisher(String seatCode, Publisher publisher) {
        return publisher != null
                ? publisher.toBuilder().id(seatCode).build()
                : Publisher.builder().id(seatCode).build();
    }

    private MultiMap headers(BidRequest bidRequest) {
        final MultiMap headers = HttpUtil.headers()
                .add(HttpUtil.X_OPENRTB_VERSION_HEADER, "2.5")
                .add(HttpUtil.ACCEPT_ENCODING_HEADER, "gzip");

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
            return Result.empty();
        }

        try {
            return Result.of(extractBids(getBidResponse(httpCall.getResponse())),
                    Collections.emptyList());
        } catch (DecodeException | PreBidException | IOException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
    }

    private static List<BidderBid> extractBids(BidResponse bidResponse) {
        return bidResponse == null || CollectionUtils.isEmpty(bidResponse.getSeatbid())
                ? Collections.emptyList()
                : bidsFromResponse(bidResponse);
    }

    private BidResponse getBidResponse(HttpResponse response) throws IOException {
        if ("gzip".equals(response.getHeaders().get("Content-Encoding"))) {
            response.getHeaders().remove("Content-Encoding");
            return mapper.decodeValue(Buffer.buffer(decompress(response.getBody())), BidResponse.class);
        }
        return mapper.decodeValue(response.getBody(), BidResponse.class);
    }

    private static List<BidderBid> bidsFromResponse(BidResponse bidResponse) {
        return bidResponse.getSeatbid().stream()
                .filter(Objects::nonNull)
                .map(SeatBid::getBid)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .map(bid -> BidderBid.of(bid, BidType.video, bidResponse.getCur()))
                .collect(Collectors.toList());
    }

    private static byte[] decompress(String file) throws IOException {
        try (GZIPInputStream gzipInput = new GZIPInputStream(new FileInputStream(file))) {
            return IOUtils.toByteArray(gzipInput);
        }
    }
}
