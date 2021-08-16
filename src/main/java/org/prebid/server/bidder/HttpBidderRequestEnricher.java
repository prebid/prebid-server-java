package org.prebid.server.bidder;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import io.vertx.core.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.util.HttpUtil;

import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HttpBidderRequestEnricher {

    private static final Set<CharSequence> HEADERS_TO_COPY = Collections.singleton(HttpUtil.SEC_GPC_HEADER.toString());

    private final String pbsRecord;

    public HttpBidderRequestEnricher(String pbsVersion) {
        pbsRecord = createNameVersionRecord("pbs-java", Objects.requireNonNull(pbsVersion));
    }

    MultiMap enrichHeaders(
            MultiMap bidderRequestHeaders,
            CaseInsensitiveMultiMap originalRequestHeaders,
            BidRequest bidRequest) {

        // some bidders has headers on class level, so we create copy to not affect them
        final MultiMap bidderRequestHeadersCopy = copyMultiMap(bidderRequestHeaders);

        addOriginalRequestHeaders(originalRequestHeaders, bidderRequestHeadersCopy);
        addXPrebidHeader(bidderRequestHeadersCopy, bidRequest);

        return bidderRequestHeadersCopy;
    }

    private static MultiMap copyMultiMap(MultiMap source) {
        final MultiMap copiedMultiMap = MultiMap.caseInsensitiveMultiMap();
        if (source != null && !source.isEmpty()) {
            source.forEach(entry -> copiedMultiMap.add(entry.getKey(), entry.getValue()));
        }
        return copiedMultiMap;
    }

    private static void addOriginalRequestHeaders(CaseInsensitiveMultiMap originalHeaders, MultiMap bidderHeaders) {
        originalHeaders.entries().stream()
                .filter(entry -> HEADERS_TO_COPY.contains(entry.getKey())
                        && !bidderHeaders.contains(entry.getKey()))
                .forEach(entry -> bidderHeaders.add(entry.getKey(), entry.getValue()));
    }

    private void addXPrebidHeader(MultiMap headers, BidRequest bidRequest) {
        final Optional<String> channelRecord = resolveChannelVersionRecord(bidRequest.getExt());
        final Optional<String> sdkRecord = resolveSdkVersionRecord(bidRequest.getApp());
        final String value = Stream.of(channelRecord, sdkRecord, Optional.of(pbsRecord))
                .flatMap(Optional::stream)
                .collect(Collectors.joining(","));
        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_PREBID_HEADER, value);
    }

    private static Optional<String> resolveChannelVersionRecord(ExtRequest extRequest) {
        return Optional.ofNullable(extRequest)
                .map(ExtRequest::getPrebid)
                .map(ExtRequestPrebid::getChannel)
                .map(channel -> createNameVersionRecord(channel.getName(), channel.getVersion()));
    }

    private static Optional<String> resolveSdkVersionRecord(App app) {
        return Optional.ofNullable(app)
                .map(App::getExt)
                .map(ExtApp::getPrebid)
                .map(extPrebid -> createNameVersionRecord(extPrebid.getSource(), extPrebid.getVersion()));
    }

    private static String createNameVersionRecord(String name, String version) {
        return StringUtils.isNotEmpty(name) && StringUtils.isNotEmpty(version)
                ? String.format("%s/%s", name, version)
                : null;
    }
}
