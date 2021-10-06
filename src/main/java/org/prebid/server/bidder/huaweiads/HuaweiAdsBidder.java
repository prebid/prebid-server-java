package org.prebid.server.bidder.huaweiads;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import io.vertx.core.MultiMap;
import io.vertx.core.http.HttpMethod;
import org.apache.commons.codec.binary.Hex;
import org.prebid.server.bidder.Bidder;
import org.prebid.server.bidder.huaweiads.model.HuaweiRequest;
import org.prebid.server.bidder.huaweiads.model.HuaweiResponse;
import org.prebid.server.bidder.model.BidderBid;
import org.prebid.server.bidder.model.BidderError;
import org.prebid.server.bidder.model.HttpCall;
import org.prebid.server.bidder.model.HttpRequest;
import org.prebid.server.bidder.model.Result;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.DecodeException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.ExtPrebid;
import org.prebid.server.proto.openrtb.ext.request.huaweiads.ExtImpHuawei;
import org.prebid.server.util.HttpUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Objects;

public class HuaweiAdsBidder implements Bidder<HuaweiRequest> {

    private static final TypeReference<ExtPrebid<?, ExtImpHuawei>> HUAWEIADS_EXT_TYPE_REFERENCE =
            new TypeReference<ExtPrebid<?, ExtImpHuawei>>() {
            };

    private final String endpointUrl;
    private final JacksonMapper mapper;

    public HuaweiAdsBidder(String endpointUrl, JacksonMapper mapper) {
        this.endpointUrl = HttpUtil.validateUrl(Objects.requireNonNull(endpointUrl));
        this.mapper = Objects.requireNonNull(mapper);
    }

    @Override
    public Result<List<HttpRequest<HuaweiRequest>>> makeHttpRequests(BidRequest request) {
        HuaweiRequest huaweiRequest = null;
        ExtImpHuawei extImpHuawei = null;
        for (Imp imp : request.getImp()) {
            try {
                extImpHuawei = HuaweiUtilResponse.parseImpExt(imp, mapper, HUAWEIADS_EXT_TYPE_REFERENCE);
                huaweiRequest = HuaweiUtilRequest.resolveHuaweiReq(extImpHuawei, imp, request, mapper);
            } catch (PreBidException e) {
                return Result.withErrors(Collections.singletonList(BidderError.badInput(e.getMessage())));
            }
        }

        final String reqJson;
        try {
            reqJson = mapper.mapper().writeValueAsString(huaweiRequest);
        } catch (JsonProcessingException e) {
            return Result.withErrors(Collections.singletonList(BidderError.badInput(e.getMessage())));
        }
        boolean isTestAuthorization = false;
        if (extImpHuawei != null && extImpHuawei.getIsTestAuthorization().equals("true")) {
            isTestAuthorization = true;
        }
        return Result.withValue(HttpRequest.<HuaweiRequest>builder()
                .method(HttpMethod.POST)
                .uri(endpointUrl)
                .headers(resolveHeaders(extImpHuawei, request, isTestAuthorization))
                .payload(huaweiRequest)
                .body(reqJson)
                .build());
    }

    private MultiMap resolveHeaders(ExtImpHuawei extImpHuawei, BidRequest request, boolean isTestAuthorization) {
        final MultiMap headers = HttpUtil.headers();
        if (extImpHuawei == null) {
            return headers;
        }
        headers.add("Authorization", getDigestAuthorization(extImpHuawei, isTestAuthorization));
        Device device = request.getDevice();
        if (device != null && device.getUa().length() > 0) {
            headers.add("User-Agent", device.getUa());
        }
        return headers;
    }

    @Override
    public Result<List<BidderBid>> makeBids(HttpCall<HuaweiRequest> httpCall, BidRequest bidRequest) {
        final HuaweiResponse huaweiAdsResponse;
        final List<BidderBid> bidderResponse;
        try {
            huaweiAdsResponse = mapper.decodeValue(httpCall.getResponse().getBody(), HuaweiResponse.class);
            checkHuaweiAdsResponseRetcode(huaweiAdsResponse);
            bidderResponse = HuaweiUtilResponse.convertHuaweiAdsRespToBidderResp(huaweiAdsResponse, bidRequest, mapper,
                    HUAWEIADS_EXT_TYPE_REFERENCE).getSeatBid().getBids();
        } catch (DecodeException | PreBidException e) {
            return Result.withError(BidderError.badServerResponse(e.getMessage()));
        }
        return Result.of(bidderResponse, Collections.emptyList());
    }

    private void checkHuaweiAdsResponseRetcode(HuaweiResponse response) {
        final Integer retcode = response.getRetcode();
        if (retcode == null) {
            throw new PreBidException("HuaweiAdsResponse.Retcode is null");
        }
        if ((retcode < 600 && retcode >= 400) || (retcode < 300 && retcode > 200)) {
            throw new PreBidException("HuaweiAdsResponse retcode: " + retcode);
        }
    }

    private String getDigestAuthorization(ExtImpHuawei extImpHuawei, boolean isTestAuthorization) {
        String nonce = generateNonce();
        // this is for test case, time 2021/8/20 19:30
        if (isTestAuthorization) {
            nonce = "1629473330823";
        }
        String publisherId = extImpHuawei.getPublisherId();
        var apiKey = publisherId + ":ppsadx/getResult:" + extImpHuawei.getSignKey();
        return "Digest username=" + publisherId + ","
                + "realm=ppsadx/getResult,"
                + "nonce=" + nonce + ","
                + "response=" + computeHmacSha256(nonce + ":POST:/ppsadx/getResult", apiKey) + ","
                + "algorithm=HmacSHA256,usertype=1,keyid=" + extImpHuawei.getKeyId();
    }

    private String computeHmacSha256(String data, String apiKey) {
        String output;
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec secretKey = new SecretKeySpec(apiKey.getBytes("UTF-8"), "HmacSHA256");
            sha256Hmac.init(secretKey);
            output = Hex.encodeHexString(sha256Hmac.doFinal(data.getBytes("UTF-8")));
        } catch (NoSuchAlgorithmException | UnsupportedEncodingException | InvalidKeyException e) {
            throw new PreBidException(e.getMessage());
        }
        return output;
    }

    private static String generateNonce() {
        final String dateTimeString = Long.toString(new Date().getTime());
        final byte[] nonceByte = dateTimeString.getBytes();
        return Base64.getEncoder().encodeToString(nonceByte);
    }
}

