package org.prebid.server.hooks.modules.id5.userid.v1.fetch;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import lombok.extern.slf4j.Slf4j;
import org.prebid.server.hooks.modules.id5.userid.v1.config.Id5IdModuleProperties;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchRequest;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchRequest.PrebidServerMetadata;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchRequest.PrebidServerMetadata.PrebidServerMetadataBuilder;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchRequest.Publisher;
import org.prebid.server.hooks.modules.id5.userid.v1.model.FetchResponse;
import org.prebid.server.hooks.modules.id5.userid.v1.model.Id5UserId;
import org.prebid.server.hooks.v1.auction.AuctionInvocationContext;
import org.prebid.server.hooks.v1.auction.AuctionRequestPayload;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.privacy.ccpa.Ccpa;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.util.VersionInfo;
import org.prebid.server.vertx.httpclient.HttpClient;
import org.prebid.server.vertx.httpclient.model.HttpClientResponse;

import java.time.Clock;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class HttpFetchClient implements FetchClient {

    private final String fetchUrl;
    private final HttpClient httpClient;
    private final Clock clock;
    private final JacksonMapper mapper;
    private final VersionInfo versionInfo;
    private final Id5IdModuleProperties id5IdModuleProperties;

    public HttpFetchClient(String endpoint,
                           HttpClient httpClient,
                           JacksonMapper mapper,
                           Clock clock,
                           VersionInfo versionInfo,
                           Id5IdModuleProperties id5IdModuleProperties) {
        this.fetchUrl = endpoint;
        this.httpClient = httpClient;
        this.mapper = mapper;
        this.clock = clock;
        this.versionInfo = versionInfo;
        this.id5IdModuleProperties = id5IdModuleProperties;
    }

    @Override
    public Future<Id5UserId> fetch(long partnerId, AuctionRequestPayload payload,
                                   AuctionInvocationContext invocationContext) {
        final FetchRequest fetchRequest = createFetchRequest(partnerId, payload, invocationContext);
        try {
            final String body = mapper.encodeToString(fetchRequest);
            final MultiMap headers = HttpUtil.headers();
            final String url = String.format("%s/%s.json", fetchUrl, partnerId);
            final long timeoutMs = invocationContext.timeout().remaining();
            log.debug("id5-user-id: fetching id5Id from endpoint {} with timeout {}. Headers {}, body {}",
                    url, timeoutMs, headers, body);

            return httpClient
                    .post(url, headers, body, timeoutMs)
                    .map(this::parseResponse)
                    .recover(this::handleError);
        } catch (Exception e) {
            return handleError(e);
        }
    }

    private FetchRequest createFetchRequest(long partnerId,
                                            AuctionRequestPayload payload,
                                            AuctionInvocationContext invocationContext) {
        final BidRequest bidRequest = payload.bidRequest();
        final PrivacyContext privacyContext = invocationContext.auctionContext().getPrivacyContext();
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
                        .orElse(null));

        if (privacyContext != null && privacyContext.getPrivacy() != null) {
            final Privacy privacy = privacyContext.getPrivacy();
            fetchRequestBuilder
                    .coppa(Optional.ofNullable(privacy.getCoppa()).map(String::valueOf).orElse(null))
                    .usPrivacy(Optional.ofNullable(privacy.getCcpa()).map(Ccpa::getUsPrivacy).orElse(null))
                    .gppString(privacy.getGpp())
                    .gppSid(Optional.ofNullable(privacy.getGppSid())
                            .filter(gppSid -> !gppSid.isEmpty())
                            .map(gppSid -> gppSid.stream().map(String::valueOf)
                                    .collect(Collectors.joining(",")))
                            .orElse(null))
                    .gdpr(privacy.getGdpr())
                    .gdprConsent(privacy.getConsentString());
        }
        return fetchRequestBuilder.build();
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
        log.error("id5-user-id: failed to fetch id5Id from endpoint {}", fetchUrl, exception);
        return Future.succeededFuture(Id5UserId.empty());
    }

    private Id5UserId parseResponse(HttpClientResponse response) {
        final String body = response.getBody();
        final int statusCode = response.getStatusCode();
        if (response.getStatusCode() == 200) {
            log.debug("id5-user-id: fetched id5Id succeeded, body {}", body);
            return mapper.decodeValue(body, FetchResponse.class);
        } else {
            log.error("id5-user-id: fetched id5Id failed, status {}, body {}", statusCode, body);
            return Id5UserId.empty();
        }
    }
}
