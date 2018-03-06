package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.ImplicitParametersExtractor;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.PreBidException;

import java.util.Objects;

public class AuctionRequestFactory {

    private static final Logger logger = LoggerFactory.getLogger(AuctionRequestFactory.class);

    private final ImplicitParametersExtractor paramsExtractor;
    private final UidsCookieService uidsCookieService;

    public AuctionRequestFactory(ImplicitParametersExtractor paramsExtractor, UidsCookieService uidsCookieService) {
        this.paramsExtractor = Objects.requireNonNull(paramsExtractor);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
    }

    /**
     * Method determines {@link BidRequest} properties which were not set explicitly by the client, but can be
     * updated by values derived from headers and other request attributes.
     */
    public BidRequest fromRequest(BidRequest bidRequest, RoutingContext context) {
        final BidRequest result;

        final HttpServerRequest request = context.request();

        final Device populatedDevice = populateDevice(bidRequest.getDevice(), request);
        final Site populatedSite = bidRequest.getApp() == null ? populateSite(bidRequest.getSite(), request) : null;
        final User populatedUser = populateUser(bidRequest.getUser(), context);

        if (populatedDevice != null || populatedSite != null || populatedUser != null) {
            result = bidRequest.toBuilder()
                    .device(populatedDevice != null ? populatedDevice : bidRequest.getDevice())
                    .site(populatedSite != null ? populatedSite : bidRequest.getSite())
                    .user(populatedUser != null ? populatedUser : bidRequest.getUser())
                    .build();
        } else {
            result = bidRequest;
        }

        return result;
    }

    /**
     * Populates the request body's 'device' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (User-Agent, IP-address).
     */
    private Device populateDevice(Device device, HttpServerRequest request) {
        final Device result;

        final String ip = device != null ? device.getIp() : null;
        final String ua = device != null ? device.getUa() : null;

        if (StringUtils.isBlank(ip) || StringUtils.isBlank(ua)) {
            final Device.DeviceBuilder builder = device == null ? Device.builder() : device.toBuilder();
            builder.ip(StringUtils.isNotBlank(ip) ? ip : paramsExtractor.ipFrom(request));
            builder.ua(StringUtils.isNotBlank(ua) ? ua : paramsExtractor.uaFrom(request));

            result = builder.build();
        } else {
            result = null;
        }

        return result;
    }

    /**
     * Populates the request body's 'site' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (domain, page).
     */
    private Site populateSite(Site site, HttpServerRequest request) {
        Site result = null;

        final String page = site != null ? site.getPage() : null;
        final String domain = site != null ? site.getDomain() : null;

        if (StringUtils.isBlank(page) || StringUtils.isBlank(domain)) {
            final String referer = paramsExtractor.refererFrom(request);
            if (StringUtils.isNotBlank(referer)) {
                try {
                    final String parsedDomain = paramsExtractor.domainFrom(referer);
                    final Site.SiteBuilder builder = site == null ? Site.builder() : site.toBuilder();
                    builder.domain(StringUtils.isNotBlank(domain) ? domain : parsedDomain);
                    builder.page(StringUtils.isNotBlank(page) ? page : referer);
                    result = builder.build();
                } catch (PreBidException e) {
                    logger.warn("Error occurred while populating bid request", e);
                }
            }
        }
        return result;
    }

    /**
     * Populates the request body's 'user' section from the incoming http request if the original is partially filled
     * and the request contains necessary info (id).
     */
    private User populateUser(User user, RoutingContext context) {
        User result = null;

        final String id = user != null ? user.getId() : null;

        if (StringUtils.isBlank(id)) {
            final String parsedId = uidsCookieService.parseHostCookie(context);
            if (StringUtils.isNotBlank(parsedId)) {
                final User.UserBuilder builder = user == null ? User.builder() : user.toBuilder();
                builder.id(parsedId);
                result = builder.build();
            }
        }

        return result;
    }
}
