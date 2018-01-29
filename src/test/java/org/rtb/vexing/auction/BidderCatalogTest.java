package org.rtb.vexing.auction;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.rtb.vexing.bidder.rubicon.RubiconBidder;
import org.rtb.vexing.config.ApplicationConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class BidderCatalogTest {

    public static final String RUBICON = "rubicon";
    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private ApplicationConfig applicationConfig;

    private BidderCatalog bidderCatalog;

    @Before
    public void setUp() {
        // given
//        given(applicationConfig.getString(eq("external_url"))).willReturn("http://external-url");

        given(applicationConfig.getString(eq("adapters.rubicon.endpoint"))).willReturn("http://rubiconproject.com/x");
//        given(applicationConfig.getString(eq("adapters.rubicon.usersync_url")))
//                .willReturn("http://rubiconproject.com/x/cookie/x");
        given(applicationConfig.getString(eq("adapters.rubicon.XAPI.Username"))).willReturn("rubicon_user");
        given(applicationConfig.getString(eq("adapters.rubicon.XAPI.Password"))).willReturn("rubicon_password");

//        given(applicationConfig.getString(eq("adapters.appnexus.endpoint"))).willReturn("http://appnexus-endpoint");
//        given(applicationConfig.getString(eq("adapters.appnexus.usersync_url")))
//                .willReturn("http://appnexus-usersync-url");

//        given(applicationConfig.getString(eq("adapters.facebook.endpoint"))).willReturn("http://facebook-endpoint");
//        given(applicationConfig.getString(eq("adapters.facebook.nonSecureEndpoint")))
//                .willReturn("http://facebook-endpoint");
//        given(applicationConfig.getString(eq("adapters.facebook.usersync_url")))
//                .willReturn("http://facebook-usersync-url");
//        given(applicationConfig.getString(eq("adapters.facebook.platform_id"))).willReturn("42");

//        given(applicationConfig.getString(eq("adapters.pulsepoint.endpoint"))).willReturn
// ("http://pulsepoint-endpoint");
//        given(applicationConfig.getString(eq("adapters.pulsepoint.usersync_url")))
//                .willReturn("http://pulsepoint-usersync-url");

//        given(applicationConfig.getString(eq("adapters.indexexchange.endpoint")))
//                .willReturn("http://indexexchange-endpoint");
//        given(applicationConfig.getString(eq("adapters.indexexchange.usersync_url")))
//                .willReturn("http://indexexchange-usersync-url");
//
//        given(applicationConfig.getString(eq("adapters.lifestreet.endpoint"))).willReturn
// ("http://lifestreet-endpoint");
//        given(applicationConfig.getString(eq("adapters.lifestreet.usersync_url")))
//                .willReturn("http://lifestreet-usersync-url");
//
//        given(applicationConfig.getString(eq("adapters.pubmatic.endpoint"))).willReturn("http://pubmatic-endpoint");
//        given(applicationConfig.getString(eq("adapters.pubmatic.usersync_url")))
//                .willReturn("http://pubmatic-usersync-url");
//
//        given(applicationConfig.getString(eq("adapters.conversant.endpoint"))).willReturn
// ("http://conversant-endpoint");
//        given(applicationConfig.getString(eq("adapters.conversant.usersync_url")))
//                .willReturn("http://conversant-usersync-url");

        bidderCatalog = BidderCatalog.create(applicationConfig);
    }

    @Test
    public void createShouldFailOnNullArguments() {
        assertThatNullPointerException().isThrownBy(() -> BidderCatalog.create(null));
    }

    @Test
    public void byNameShouldReturnConfiguredBidder() {
        assertThat(bidderCatalog.byName(RUBICON)).isNotNull().isInstanceOf(RubiconBidder.class);

//        assertThat(bidderCatalog.byName("appnexus"))
//                .isNotNull()
//                .isInstanceOf(AppnexusAdapter.class);
//
//        assertThat(bidderCatalog.byName("audienceNetwork"))
//                .isNotNull()
//                .isInstanceOf(FacebookAdapter.class);
//
//        assertThat(bidderCatalog.byName("pulsepoint"))
//                .isNotNull()
//                .isInstanceOf(PulsepointAdapter.class);
//
//        assertThat(bidderCatalog.byName("indexExchange"))
//                .isNotNull()
//                .isInstanceOf(IndexExchangeAdapter.class);
//
//        assertThat(bidderCatalog.byName("Lifestreet"))
//                .isNotNull()
//                .isInstanceOf(LifestreetAdapter.class);
//
//        assertThat(bidderCatalog.byName("Pubmatic"))
//                .isNotNull()
//                .isInstanceOf(PubmaticAdapter.class);
//
//        assertThat(bidderCatalog.byName("conversant"))
//                .isNotNull()
//                .isInstanceOf(ConversantAdapter.class);
    }

    @Test
    public void isValidNameShouldReturnTrueForKnownBidders() {
        assertThat(bidderCatalog.isValidName(RUBICON)).isTrue();
    }

    @Test
    public void isValidNameShouldReturnFalseForUnknownBidders() {
        assertThat(bidderCatalog.isValidName("unknown_bidder")).isFalse();
    }
}