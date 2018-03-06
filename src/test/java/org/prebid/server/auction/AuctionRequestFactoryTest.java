package org.prebid.server.auction;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import io.vertx.ext.web.RoutingContext;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.ImplicitParametersExtractor;
import org.prebid.server.VertxTest;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.exception.PreBidException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

public class AuctionRequestFactoryTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ImplicitParametersExtractor paramsExtractor;
    @Mock
    private UidsCookieService uidsCookieService;

    private AuctionRequestFactory factory;

    @Mock
    private RoutingContext routingContext;

    @Before
    public void setUp() {
        factory = new AuctionRequestFactory(paramsExtractor, uidsCookieService);
    }

    @Test
    public void shouldSetFieldsFromHeadersIfBodyFieldsEmpty() {
        // given
        givenImplicitParams("http://example.com", "example.com", "192.168.244.1", "UnitTest");
        given(uidsCookieService.parseHostCookie(any())).willReturn("userId");

        // when
        final BidRequest populatedBidRequest = factory.fromRequest(BidRequest.builder().build(), routingContext);

        // then
        assertThat(populatedBidRequest.getSite()).isEqualTo(
                Site.builder().page("http://example.com").domain("example.com").build());
        assertThat(populatedBidRequest.getDevice()).isEqualTo(
                Device.builder().ip("192.168.244.1").ua("UnitTest").build());
        assertThat(populatedBidRequest.getUser()).isEqualTo(User.builder().id("userId").build());
    }

    @Test
    public void shouldNotSetFieldsFromHeadersIfRequestFieldsNotEmpty() {
        // given
        givenImplicitParams("http://anotherexample.com", "anotherexample.com", "192.168.244.2", "UnitTest2");
        given(uidsCookieService.parseHostCookie(any())).willReturn("userId");
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("test.com").page("http://test.com").build())
                .device(Device.builder().ua("UnitTestUA").ip("56.76.12.3").build())
                .user(User.builder().id("userId").build())
                .build();

        // when
        final BidRequest populatedBidRequest = factory.fromRequest(bidRequest, routingContext);

        // then
        assertThat(populatedBidRequest).isSameAs(bidRequest);
    }

    @Test
    public void shouldNotSetSiteIfNoReferer() {
        // when
        final BidRequest populatedBidRequest = factory.fromRequest(BidRequest.builder().build(), routingContext);

        // then
        assertThat(populatedBidRequest.getSite()).isNull();
    }

    @Test
    public void shouldNotSetSitePageIfNoReferer() {
        // given
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("home.com").build())
                .build();

        // when
        final BidRequest result = factory.fromRequest(bidRequest, routingContext);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder().domain("home.com").build());
    }

    @Test
    public void shouldNotSetSitePageIfDomainCouldNotBeDerived() {
        // given
        given(paramsExtractor.domainFrom(anyString())).willThrow(new PreBidException("Couldn't derive domain"));
        final BidRequest bidRequest = BidRequest.builder()
                .site(Site.builder().domain("home.com").build())
                .build();

        // when
        final BidRequest result = factory.fromRequest(bidRequest, routingContext);

        // then
        assertThat(result.getSite()).isEqualTo(Site.builder().domain("home.com").build());
    }

    @Test
    public void shouldNotSetUserIfNoHostCookie() {
        // given
        given(uidsCookieService.parseHostCookie(any())).willReturn(null);

        // when
        final BidRequest result = factory.fromRequest(BidRequest.builder().build(), routingContext);

        // then
        assertThat(result.getUser()).isNull();
    }

    private void givenImplicitParams(String referer, String domain, String ip, String ua) {
        given(paramsExtractor.refererFrom(any())).willReturn(referer);
        given(paramsExtractor.domainFrom(anyString())).willReturn(domain);
        given(paramsExtractor.ipFrom(any())).willReturn(ip);
        given(paramsExtractor.uaFrom(any())).willReturn(ua);
    }
}