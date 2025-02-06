package org.prebid.server.auction.versionconverter;

import com.iab.openrtb.request.BidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;

@ExtendWith(MockitoExtension.class)
public class BidRequestConversionManagerTest extends VertxTest {

    @Mock(strictness = LENIENT)
    private BidRequestOrtbVersionConverterFactory ortbVersionConverterFactory;

    private BidRequestOrtbVersionConversionManager ortbVersionConversionManager;

    @BeforeEach
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
