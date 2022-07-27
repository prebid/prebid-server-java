package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

public class BidRequestConversionManagerTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private BidRequestOrtbVersionConverterFactory ortbVersionConverterFactory;

    private BidRequestOrtbVersionConversionManager ortbVersionConversionManager;

    @Before
    public void setUp() {
        given(ortbVersionConverterFactory.getConverter(eq(OrtbVersion.ORTB_2_6), eq(OrtbVersion.ORTB_2_5)))
                .willReturn(bidRequest -> bidRequest.toBuilder().id("2.5").build());
        given(ortbVersionConverterFactory.getConverter(eq(OrtbVersion.ORTB_2_5), eq(OrtbVersion.ORTB_2_6)))
                .willReturn(bidRequest -> bidRequest.toBuilder().id("2.6").build());

        ortbVersionConversionManager = new BidRequestOrtbVersionConversionManager(ortbVersionConverterFactory);
    }

    @Test
    public void convertToAuctionSupportedVersionShouldConvertToExpectedVersion() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidRequest result = ortbVersionConversionManager.convertToAuctionSupportedVersion(bidRequest);

        // then
        assertThat(result)
                .extracting(BidRequest::getId)
                .isEqualTo("2.6");
    }

    @Test
    public void convertFromAuctionSupportedVersionShouldConvertToExpectedVersion() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final BidRequest result = ortbVersionConversionManager
                .convertFromAuctionSupportedVersion(bidRequest, OrtbVersion.ORTB_2_5);

        // then
        assertThat(result)
                .extracting(BidRequest::getId)
                .isEqualTo("2.5");
    }
}
