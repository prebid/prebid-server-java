package org.prebid.server.hooks.modules.id5.userid.v1.fetch;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.activity.Activity;
import org.prebid.server.activity.ComponentType;
import org.prebid.server.activity.infrastructure.ActivityInfrastructure;
import org.prebid.server.activity.infrastructure.payload.ActivityInvocationPayload;
import org.prebid.server.activity.infrastructure.payload.impl.ActivityInvocationPayloadImpl;
import org.prebid.server.activity.infrastructure.payload.impl.BidRequestActivityInvocationPayload;
import org.prebid.server.auction.privacy.enforcement.mask.UserFpdActivityMask;
import org.prebid.server.hooks.modules.id5.userid.v1.Id5IdModule;
import org.prebid.server.hooks.modules.id5.userid.v1.config.Id5IdModuleProperties;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchRequest;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchRequest.PrebidServerMetadata;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchRequest.PrebidServerMetadata.PrebidServerMetadataBuilder;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchRequest.Publisher;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchResponse;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.log.Logger;
import org.prebid.server.log.LoggerFactory;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.VersionInfo;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class HttpFetchClient implements FetchClient {

    private static final Logger logger = LoggerFactory.getLogger(HttpFetchClient.class);

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true)
            .enable(StreamReadFeature.INCLUDE_SOURCE_IN_LOCATION)
            .build()
            .setSerializationInclusion(JsonInclude.Include.NON_NULL)
            .registerModule(new JavaTimeModule());

    private final String fetchUrl;
    private final HttpClient httpClient;
    private final Clock clock;
    private final VersionInfo versionInfo;
    private final Id5IdModuleProperties id5IdModuleProperties;
    private final UserFpdActivityMask userFpdActivityMask;

    public HttpFetchClient(String endpoint,
                           HttpClient httpClient,
                           Clock clock,
                           VersionInfo versionInfo,
                           Id5IdModuleProperties id5IdModuleProperties,
                           UserFpdActivityMask userFpdActivityMask) {

        this.fetchUrl = Objects.requireNonNull(endpoint);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.clock = Objects.requireNonNull(clock);
        this.versionInfo = Objects.requireNonNull(versionInfo);
        this.id5IdModuleProperties = Objects.requireNonNull(id5IdModuleProperties);
        this.userFpdActivityMask = Objects.requireNonNull(userFpdActivityMask);
    }

    @Override
    public Future<Id5UserId> fetch(long partnerId,
                                   AuctionRequestPayload payload,
                                   AuctionInvocationContext invocationContext) {

        final FetchRequest fetchRequest = createFetchRequest(partnerId, payload, invocationContext);
        final String body;
        try {
            body = MAPPER.writeValueAsString(fetchRequest);
        } catch (JsonProcessingException e) {
            return Future.failedFuture(e);
        }
        final MultiMap headers = HttpUtil.headers();
        final String url = "%s/%s.json".formatted(fetchUrl, partnerId);
        final long timeoutMs = invocationContext.timeout().remaining();
        logger.debug("id5-user-id: fetching id5Id from endpoint {} with timeout {}. Headers {}, body {}",
                url, timeoutMs, headers, body);

        return httpClient
                .post(url, headers, body, timeoutMs)
                .map(this::parseResponse)
                .recover(this::handleError);
    }

    private FetchRequest createFetchRequest(long partnerId,
                                            AuctionRequestPayload payload,
                                            AuctionInvocationContext invocationContext) {

        final BidRequest bidRequest = maskBidRequest(payload.bidRequest(), invocationContext);
        final Privacy privacy = invocationContext.auctionContext().getPrivacyContext().getPrivacy();
        final Optional<Device> maybeDevice = Optional.ofNullable(bidRequest.getDevice());
        final FetchRequest.FetchRequestBuilder fetchRequestBuilder = FetchRequest.builder()
                .trace(invocationContext.debugEnabled())
                .partnerId(partnerId)
                .origin("pbs-java")
                .version(versionInfo.getVersion())
                .timestamp(clock.instant().toString())
                .provider(id5IdModuleProperties.getProviderName())
                .providerMetadata(createProviderMetadata(bidRequest))
                .bundle(Optional.ofNullable(bidRequest.getApp()).map(App::getBundle).orElse(null))
                .domain(Optional.ofNullable(bidRequest.getSite()).map(Site::getDomain).orElse(null))
                .maid(maybeDevice.map(Device::getIfa).orElse(null))
                .userAgent(maybeDevice.map(Device::getUa).orElse(null))
                .ref(Optional.ofNullable(bidRequest.getSite()).map(Site::getRef).orElse(null))
                .ipv4(maybeDevice.map(Device::getIp).orElse(null))
                .ipv6(maybeDevice.map(Device::getIpv6).orElse(null))
                .att(maybeDevice
                        .map(Device::getExt)
                        .map(ExtDevice::getAtts)
                        .map(String::valueOf)
                        .orElse(null))
                .coppa(Optional.ofNullable(privacy.getCoppa()).map(String::valueOf).orElse(null))
                .usPrivacy(Optional.ofNullable(privacy.getCcpa()).map(Ccpa::getUsPrivacy).orElse(null))
                .gppString(privacy.getGpp())
                .gppSid(toStringOrNull(privacy.getGppSid()))
                .gdpr(privacy.getGdpr())
                .gdprConsent(privacy.getConsentString());

        return fetchRequestBuilder.build();
    }

    private BidRequest maskBidRequest(BidRequest bidRequest, AuctionInvocationContext invocationContext) {
        final ActivityInvocationPayload activityInvocationPayload = BidRequestActivityInvocationPayload.of(
                ActivityInvocationPayloadImpl.of(
                        ComponentType.RTD_MODULE,
                        Id5IdModule.CODE),
                bidRequest);
        final ActivityInfrastructure activityInfrastructure =
                invocationContext.auctionContext().getActivityInfrastructure();

        final boolean disallowTransmitUfpd = !activityInfrastructure.isAllowed(
                Activity.TRANSMIT_UFPD, activityInvocationPayload);
        final boolean disallowTransmitGeo = !activityInfrastructure.isAllowed(
                Activity.TRANSMIT_GEO, activityInvocationPayload);

        final Device maskedDevice = userFpdActivityMask.maskDevice(
                bidRequest.getDevice(), disallowTransmitUfpd, disallowTransmitGeo);

        return bidRequest.toBuilder()
                .device(maskedDevice)
                .build();
    }

    private PrebidServerMetadata createProviderMetadata(BidRequest bidRequest) {
        final PrebidServerMetadataBuilder builder = PrebidServerMetadata.builder()
                .id5ModuleConfig(this.id5IdModuleProperties);

        Optional.ofNullable(bidRequest.getApp()).map(App::getPublisher)
                .or(() -> Optional.ofNullable(bidRequest.getSite()).map(Site::getPublisher))
                .ifPresent(ortbPublisher -> builder.publisher(Publisher.builder()
                        .id(ortbPublisher.getId())
                        .name(ortbPublisher.getName())
                        .domain(ortbPublisher.getDomain())
                        .build()));

        final Optional<ExtRequestPrebid> maybePrebidExt = Optional.ofNullable(bidRequest.getExt())
                .map(ExtRequest::getPrebid);

        maybePrebidExt
                .map(ExtRequestPrebid::getChannel)
                .ifPresent(channel -> builder
                        .channel(channel.getName())
                        .channelVersion(channel.getVersion())
                );

        maybePrebidExt
                .map(ExtRequestPrebid::getData)
                .map(ExtRequestPrebidData::getBidders)
                .ifPresent(builder::bidders);

        return builder.build();
    }

    private Future<Id5UserId> handleError(Throwable exception) {
        logger.error("id5-user-id: failed to fetch id5Id from endpoint {}", fetchUrl, exception);
        return Future.succeededFuture(Id5UserId.empty());
    }

    private Id5UserId parseResponse(HttpClientResponse response) {
        final String body = response.getBody();
        final int statusCode = response.getStatusCode();
        if (response.getStatusCode() == 200) {
            logger.debug("id5-user-id: fetched id5Id succeeded, body {}", body);
            try {
                return MAPPER.readValue(body, FetchResponse.class);
            } catch (JsonProcessingException e) {
                logger.error("id5-user-id: failed to parse response body {}", body, e);
                return Id5UserId.empty();
            }
        } else {
            logger.error("id5-user-id: fetched id5Id failed, status {}, body {}", statusCode, body);
            return Id5UserId.empty();
        }
    }

    private static String toStringOrNull(List<Integer> gppSid) {
        return CollectionUtils.isNotEmpty(gppSid)
                ? gppSid.stream().map(String::valueOf).collect(Collectors.joining(","))
                : null;
    }
}
