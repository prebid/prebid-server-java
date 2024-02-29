package org.prebid.server.bidder;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.vertx.core.MultiMap;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.BidderAliases;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.proto.openrtb.ext.request.ExtApp;
import org.prebid.server.proto.openrtb.ext.request.ExtAppPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidChannel;
import org.prebid.server.spring.config.bidder.model.CompressionType;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.version.PrebidVersionProvider;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class HttpBidderRequestEnricher {

    private static final Set<CharSequence> HEADERS_TO_COPY = Set.of(
            HttpUtil.SEC_GPC_HEADER.toString(),
            HttpUtil.SAVE_DATA.toString(),
            HttpUtil.SEC_CH_UA.toString(),
            HttpUtil.SEC_CH_UA_MOBILE.toString(),
            HttpUtil.SEC_CH_UA_PLATFORM.toString()
    );

    private final PrebidVersionProvider prebidVersionProvider;

    private final BidderCatalog bidderCatalog;

    public HttpBidderRequestEnricher(PrebidVersionProvider prebidVersionProvider, BidderCatalog bidderCatalog) {
        this.prebidVersionProvider = Objects.requireNonNull(prebidVersionProvider);
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
    }

    MultiMap enrichHeaders(
            String bidderName,
            MultiMap bidderRequestHeaders,
            CaseInsensitiveMultiMap originalRequestHeaders,
            BidderAliases aliases,
            BidRequest bidRequest) {

        // some bidders has headers on class level, so we create copy to not affect them
        final MultiMap bidderRequestHeadersCopy = copyMultiMap(bidderRequestHeaders);

        addOriginalRequestHeaders(bidderRequestHeadersCopy, originalRequestHeaders);
        addXPrebidHeader(bidderRequestHeadersCopy, bidRequest);
        addContentEncodingHeader(bidderRequestHeadersCopy, resolveCompressionType(bidderName, aliases));

        return bidderRequestHeadersCopy;
    }

    private static MultiMap copyMultiMap(MultiMap source) {
        final MultiMap copiedMultiMap = MultiMap.caseInsensitiveMultiMap();
        if (source != null && !source.isEmpty()) {
            source.forEach(entry -> copiedMultiMap.add(entry.getKey(), entry.getValue()));
        }
        return copiedMultiMap;
    }

    private static void addOriginalRequestHeaders(MultiMap bidderHeaders, CaseInsensitiveMultiMap originalHeaders) {
        originalHeaders.entries().stream()
                .filter(entry -> HEADERS_TO_COPY.contains(entry.getKey()) && !bidderHeaders.contains(entry.getKey()))
                .forEach(entry -> bidderHeaders.add(entry.getKey(), entry.getValue()));
    }

    private void addXPrebidHeader(MultiMap headers, BidRequest bidRequest) {
        final String channelRecord = resolveChannelVersionRecord(bidRequest.getExt());
        final String sdkRecord = resolveSdkVersionRecord(bidRequest.getApp());
        final String value = Stream.of(channelRecord, sdkRecord, prebidVersionProvider.getNameVersionRecord())
                .filter(Objects::nonNull)
                .collect(Collectors.joining(","));

        HttpUtil.addHeaderIfValueIsNotEmpty(headers, HttpUtil.X_PREBID_HEADER, value);
    }

    private static String resolveChannelVersionRecord(ExtRequest extRequest) {
        final ExtRequestPrebid extPrebid = extRequest != null ? extRequest.getPrebid() : null;
        final ExtRequestPrebidChannel channel = extPrebid != null ? extPrebid.getChannel() : null;
        final String channelName = channel != null ? channel.getName() : null;
        final String channelVersion = channel != null ? channel.getVersion() : null;

        return createNameVersionRecord(channelName, channelVersion);
    }

    private static String resolveSdkVersionRecord(App app) {
        final ExtApp extApp = app != null ? app.getExt() : null;
        final ExtAppPrebid extPrebid = extApp != null ? extApp.getPrebid() : null;
        final String sdkSource = extPrebid != null ? extPrebid.getSource() : null;
        final String sdkVersion = extPrebid != null ? extPrebid.getVersion() : null;

        return createNameVersionRecord(sdkSource, sdkVersion);
    }

    private static String createNameVersionRecord(String name, String version) {
        return StringUtils.isNoneEmpty(name, version)
                ? "%s/%s".formatted(name, version)
                : null;
    }

    private static void addContentEncodingHeader(MultiMap bidderHeaders, CompressionType compressionType) {
        if (compressionType == CompressionType.GZIP) {
            bidderHeaders.add(HttpUtil.CONTENT_ENCODING_HEADER, HttpHeaderValues.GZIP);
        }
    }

    private CompressionType resolveCompressionType(String bidderName, BidderAliases aliases) {
        return Optional.ofNullable(bidderCatalog.bidderInfoByName(aliases.resolveBidder(bidderName)))
                .map(BidderInfo::getCompressionType)
                .orElse(CompressionType.NONE);
    }
}
