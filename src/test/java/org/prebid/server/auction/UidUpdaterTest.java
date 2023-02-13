package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.User;
import io.vertx.core.http.Cookie;
import io.vertx.core.http.CookieSameSite;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.cookie.model.UidWithExpiry;
import org.prebid.server.cookie.proto.Uids;
import org.prebid.server.model.CaseInsensitiveMultiMap;
import org.prebid.server.model.HttpRequestContext;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;

import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.util.Collections.emptyMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class UidUpdaterTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private static final String HOST_COOKIE_FAMILY = "host-cookie-family";
    private static final String HOST_COOKIE_NAME = "host-cookie-name";
    private static final String HOST_COOKIE_DOMAIN = "host-cookie-domain";

    private UidUpdater uidUpdater;

    @Mock
    BidderCatalog bidderCatalog;

    @Mock
    UidsCookieService uidsCookieService;

    @Mock
    BidderAliases bidderAliases;

    @Before
    public void setUp() {
        uidUpdater = new UidUpdater(HOST_COOKIE_FAMILY, bidderCatalog, uidsCookieService);

        given(bidderAliases.resolveBidder(any()))
                .willAnswer(inv -> inv.getArgument(0));
        given(bidderCatalog.cookieFamilyName(eq("bidder")))
                .willReturn(Optional.of("bidder-cookie-family"));
    }

    @Test
    public void updateShouldReturnUnalteredUidWhenPresentInUser() {
        // given
        final User user = User.builder()
                .buyeruid("buyeruid-from-user")
                .ext(ExtUser.builder()
                        .prebid(ExtUserPrebid.of(Map.of("bidder", "buyeruid-from-ext")))
                        .build())
                .build();

        final AuctionContext auctionContext = AuctionContext.builder()
                .httpRequest(givenHttpRequest("buyeruid-from-host-cookie"))
                .bidRequest(BidRequest.builder().user(user).build())
                .uidsCookie(givenUidsCookie(Map.of("bidder-cookie-family", "buyeruid-from-uids-cookie")))
                .build();

        // when
        final UpdateResult<String> result = uidUpdater.updateUid("bidder", auctionContext, bidderAliases);

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered("buyeruid-from-user"));
    }

    @Test
    public void updateShouldReturnUpdatedUidWhenPresentInUserExtAndAbsentInUser() {
        // given
        final User user = User.builder()
                .ext(ExtUser.builder()
                        .prebid(ExtUserPrebid.of(Map.of("bidder", "buyeruid-from-ext")))
                        .build())
                .build();

        final AuctionContext auctionContext = AuctionContext.builder()
                .httpRequest(givenHttpRequest("buyeruid-from-host-cookie"))
                .bidRequest(BidRequest.builder().user(user).build())
                .uidsCookie(givenUidsCookie(Map.of("bidder-cookie-family", "buyeruid-from-uids-cookie")))
                .build();

        // when
        final UpdateResult<String> result = uidUpdater.updateUid("bidder", auctionContext, bidderAliases);

        // then
        assertThat(result).isEqualTo(UpdateResult.updated("buyeruid-from-ext"));
    }

    @Test
    public void updateShouldReturnUpdatedUidWhenPresentInUidsCookieAndAbsentInUserExtAndUser() {
        // given
        final AuctionContext auctionContext = AuctionContext.builder()
                .httpRequest(givenHttpRequest("buyeruid-from-host-cookie"))
                .bidRequest(BidRequest.builder().user(User.builder().build()).build())
                .uidsCookie(givenUidsCookie(Map.of("bidder-cookie-family", "buyeruid-from-uids-cookie")))
                .build();

        // when
        final UpdateResult<String> result = uidUpdater.updateUid("bidder", auctionContext, bidderAliases);

        // then
        assertThat(result).isEqualTo(UpdateResult.updated("buyeruid-from-uids-cookie"));
    }

    @Test
    public void updateShouldReturnUpdatedUidWhenPresentInHostCookieAndAbsentInUserExtAndUserAndUidsCookie() {
        // given
        given(bidderCatalog.cookieFamilyName("bidder")).willReturn(Optional.of(HOST_COOKIE_FAMILY));

        final HttpRequestContext httpRequest = givenHttpRequest("buyeruid-from-host-cookie");
        given(uidsCookieService.parseHostCookie(httpRequest)).willReturn("buyeruid-from-host-cookie");

        final AuctionContext auctionContext = AuctionContext.builder()
                .httpRequest(httpRequest)
                .uidsCookie(givenUidsCookie(emptyMap()))
                .bidRequest(BidRequest.builder().user(User.builder().build()).build())
                .build();

        // when
        final UpdateResult<String> result = uidUpdater.updateUid("bidder", auctionContext, bidderAliases);

        // then
        assertThat(result).isEqualTo(UpdateResult.updated("buyeruid-from-host-cookie"));
    }

    @Test
    public void updateShouldReturnUnalteredUidWhenAbsentInUserAndUserExtAndUidsCookieAndFamilyIsNotHostCookieFamily() {
        // given
        final HttpRequestContext httpRequest = givenHttpRequest("buyeruid-from-host-cookie");
        given(uidsCookieService.parseHostCookie(httpRequest)).willReturn("buyeruid-from-host-cookie");

        final AuctionContext auctionContext = AuctionContext.builder()
                .httpRequest(httpRequest)
                .uidsCookie(givenUidsCookie(emptyMap()))
                .bidRequest(BidRequest.builder().user(User.builder().build()).build())
                .build();

        // when
        final UpdateResult<String> result = uidUpdater.updateUid("bidder", auctionContext, bidderAliases);

        // then
        assertThat(result).isEqualTo(UpdateResult.unaltered(null));
    }

    private static HttpRequestContext givenHttpRequest(String hostCookieValue) {
        final Cookie hostCookie = givenHostCookie(hostCookieValue);

        return HttpRequestContext.builder()
                .headers(CaseInsensitiveMultiMap.builder().add("Cookie", hostCookie.encode()).build())
                .build();
    }

    private static Cookie givenHostCookie(String value) {
        return Cookie
                .cookie(HOST_COOKIE_NAME, Base64.getUrlEncoder().encodeToString(value.getBytes()))
                .setPath("/")
                .setSameSite(CookieSameSite.NONE)
                .setSecure(true)
                .setMaxAge(10000)
                .setDomain(HOST_COOKIE_DOMAIN);
    }

    private static UidsCookie givenUidsCookie(Map<String, String> uidValues) {
        final Map<String, UidWithExpiry> uids = uidValues.entrySet().stream()
                .map(entry -> Map.entry(entry.getKey(), UidWithExpiry.live(entry.getValue())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        return new UidsCookie(Uids.builder().uids(uids).build(), jacksonMapper);
    }
}
