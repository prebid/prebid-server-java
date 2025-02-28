package org.prebid.server.hooks.modules.optable.targeting.v1.core;

import com.iab.gpp.encoder.GppModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.geolocation.model.GeoInfo;
import org.prebid.server.hooks.modules.optable.targeting.model.OptableAttributes;
import org.prebid.server.hooks.modules.optable.targeting.model.config.OptableTargetingProperties;
import org.prebid.server.hooks.modules.optable.targeting.v1.BaseOptableTest;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.model.Privacy;
import org.prebid.server.privacy.model.PrivacyContext;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class OptableAttributesResolverTest extends BaseOptableTest {

    private OptableAttributesResolver target;

    @Mock(strictness = LENIENT)
    private TcfContext tcfContext;

    @Mock(strictness = LENIENT)
    private GppContext gppContext;

    @Mock(strictness = LENIENT)
    private GeoInfo geoInfo;

    @Mock
    private OptableTargetingProperties properties;

    @BeforeEach
    public void setUp() {
        when(properties.getTimeout()).thenReturn(100L);
        target = new OptableAttributesResolver(new IpResolver());
    }

    @Test
    public void shouldResolveTcfAttributesWhenConsentIsValid() {
        // given
        when(tcfContext.isConsentValid()).thenReturn(true);
        when(tcfContext.getConsentString()).thenReturn("consent");
        final AuctionContext auctionContext = givenAuctionContext(tcfContext);

        // when
        final OptableAttributes result = target.reloveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns("gdpr", OptableAttributes::getReg)
                .returns("consent", OptableAttributes::getTcf);
    }

    @Test
    public void shouldNotResolveTcfAttributesWhenConsentIsNotValid() {
        // given
        when(tcfContext.isConsentValid()).thenReturn(false);
        when(tcfContext.getConsentString()).thenReturn("consent");
        final AuctionContext auctionContext = givenAuctionContext(tcfContext);

        // when
        final OptableAttributes result = target.reloveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns(null, OptableAttributes::getReg)
                .returns(null, OptableAttributes::getTcf)
                .returns(List.of("8.8.8.8"), OptableAttributes::getIps);
    }

    @Test
    public void shouldResolveGppGdprAttributes() {
        // given
        final GppModel gppModel = mock();
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(1)));
        final AuctionContext auctionContext = givenAuctionContext(gppContext);

        // when

        final OptableAttributes result = target.reloveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns("gdpr", OptableAttributes::getReg)
                .returns("consent", OptableAttributes::getGpp)
                .returns(Set.of(1), OptableAttributes::getGppSid);
    }

    @Test
    public void shouldResolveGppCanadaAttributes() {
        // given
        final GppModel gppModel = mock();
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(5)));
        final AuctionContext auctionContext = givenAuctionContext(gppContext);

        // when

        final OptableAttributes result = target.reloveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns("can", OptableAttributes::getReg)
                .returns("consent", OptableAttributes::getGpp)
                .returns(Set.of(5), OptableAttributes::getGppSid);
    }

    @Test
    public void shouldResolveGppUSAttributes() {
        // given
        final GppModel gppModel = mock();
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(8)));
        final AuctionContext auctionContext = givenAuctionContext(gppContext);

        // when

        final OptableAttributes result = target.reloveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns("us", OptableAttributes::getReg)
                .returns("consent", OptableAttributes::getGpp)
                .returns(Set.of(8), OptableAttributes::getGppSid);
    }

    @Test
    public void shouldResolveGeoInfoUSAttributes() {
        // given
        when(geoInfo.getCountry()).thenReturn("United States");
        final AuctionContext auctionContext = givenAuctionContext(geoInfo);

        // when
        final OptableAttributes result = target.reloveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns("us", OptableAttributes::getReg)
                .returns(null, OptableAttributes::getGpp)
                .returns(null, OptableAttributes::getTcf);
    }

    @Test
    public void shouldResolveGeoInfoGDPRAttributes() {
        // given
        when(geoInfo.getCountry()).thenReturn("Malta");
        final AuctionContext auctionContext = givenAuctionContext(geoInfo);

        // when
        final OptableAttributes result = target.reloveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns("gdpr", OptableAttributes::getReg)
                .returns(null, OptableAttributes::getGpp)
                .returns(null, OptableAttributes::getTcf);
    }

    @Test
    public void shouldResolveGeoInfoCanadaAttributes() {
        // given
        when(geoInfo.getCountry()).thenReturn("Quebec");
        final AuctionContext auctionContext = givenAuctionContext(geoInfo);

        // when
        final OptableAttributes result = target.reloveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns("can", OptableAttributes::getReg)
                .returns(null, OptableAttributes::getGpp)
                .returns(null, OptableAttributes::getTcf);
    }

    @Test
    public void shouldResolveGeoInfoGDPRForRegionAttributes() {
        // given
        when(geoInfo.getRegion()).thenReturn("Mayotte");
        final AuctionContext auctionContext = givenAuctionContext(geoInfo);

        // when
        final OptableAttributes result = target.reloveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns("gdpr", OptableAttributes::getReg)
                .returns(null, OptableAttributes::getGpp)
                .returns(null, OptableAttributes::getTcf);
    }

    public AuctionContext givenAuctionContext(TcfContext tcfContext) {
        return AuctionContext.builder()
                .privacyContext(PrivacyContext.of(Privacy.builder().build(), tcfContext,
                        "8.8.8.8")).build();
    }

    public AuctionContext givenAuctionContext(GppContext gppContext) {
        return AuctionContext.builder().gppContext(gppContext).build();
    }

    public AuctionContext givenAuctionContext(GeoInfo geoInfo) {
        return AuctionContext.builder().geoInfo(geoInfo).build();
    }
}
