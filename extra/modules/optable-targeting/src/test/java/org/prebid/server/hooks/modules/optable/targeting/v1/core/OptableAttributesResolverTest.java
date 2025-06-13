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
    }

    @Test
    public void shouldResolveTcfAttributesWhenConsentIsValid() {
        // given
        final GppModel gppModel = mock();
        when(tcfContext.isConsentValid()).thenReturn(true);
        when(tcfContext.isInGdprScope()).thenReturn(true);
        when(tcfContext.getConsentString()).thenReturn("consent");
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(1)));
        final AuctionContext auctionContext = givenAuctionContext(tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns(true, OptableAttributes::isGdprApplies)
                .returns("consent", OptableAttributes::getGdprConsent);
    }

    @Test
    public void shouldNotResolveTcfAttributesWhenConsentIsNotValid() {
        // given
        final GppModel gppModel = mock();
        when(tcfContext.isConsentValid()).thenReturn(false);
        when(tcfContext.getConsentString()).thenReturn("consent");
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(1)));
        final AuctionContext auctionContext = givenAuctionContext(tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns(false, OptableAttributes::isGdprApplies)
                .returns(null, OptableAttributes::getGdprConsent)
                .returns(List.of("8.8.8.8"), OptableAttributes::getIps);
    }

    @Test
    public void shouldResolveGppAttributes() {
        // given
        final GppModel gppModel = mock();
        when(tcfContext.isConsentValid()).thenReturn(false);
        when(tcfContext.getConsentString()).thenReturn("consent");
        when(gppModel.encode()).thenReturn("consent");
        when(gppContext.scope()).thenReturn(GppContext.Scope.of(gppModel, Set.of(1)));
        final AuctionContext auctionContext = givenAuctionContext(tcfContext, gppContext);

        // when
        final OptableAttributes result = OptableAttributesResolver.resolveAttributes(auctionContext, properties.getTimeout());

        // then
        assertThat(result).isNotNull()
                .returns(false, OptableAttributes::isGdprApplies)
                .returns("consent", OptableAttributes::getGpp)
                .returns(Set.of(1), OptableAttributes::getGppSid);
    }

    public AuctionContext givenAuctionContext(TcfContext tcfContext) {
        return AuctionContext.builder()
                .privacyContext(PrivacyContext.of(Privacy.builder().build(), tcfContext,
                        "8.8.8.8")).build();
    }

    public AuctionContext givenAuctionContext(GppContext gppContext) {
        return AuctionContext.builder().gppContext(gppContext).build();
    }

    public AuctionContext givenAuctionContext(TcfContext tcfContext, GppContext gppContext) {
        return AuctionContext.builder()
                .privacyContext(PrivacyContext.of(Privacy.builder().build(), tcfContext, "8.8.8.8"))
                .gppContext(gppContext).build();
    }

    public AuctionContext givenAuctionContext(GeoInfo geoInfo) {
        return AuctionContext.builder().geoInfo(geoInfo).build();
    }
}
