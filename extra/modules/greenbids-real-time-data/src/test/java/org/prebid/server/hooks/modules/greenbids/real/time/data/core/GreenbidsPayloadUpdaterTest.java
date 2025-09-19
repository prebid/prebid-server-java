package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.getAppnexusNode;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.getPubmaticNode;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.getRubiconNode;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenBidRequest;
import static org.prebid.server.hooks.modules.greenbids.real.time.data.util.TestBidRequestProvider.givenImpExt;

public class GreenbidsPayloadUpdaterTest {

    @Test
    public void updateShouldReturnUpdatedBidRequest() {
        // given
        final Imp givenImp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt(getRubiconNode(), getAppnexusNode(), getPubmaticNode()))
                .build();

        // when
        final BidRequest result = GreenbidsPayloadUpdater.update(
                givenBidRequest(identity(), List.of(givenImp)),
                Map.of("adunitcodevalue", Map.of("rubicon", true, "appnexus", false, "pubmatic", false)));

        // then
        final Imp expectedImp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt(getRubiconNode(), null, null))
                .build();

        assertThat(result.getImp()).containsOnly(expectedImp);
    }

    @Test
    public void updateShouldRemoveImpFromUpdateBidRequestWhenAllBiddersFiltered() {
        // given
        final Imp givenImp = Imp.builder()
                .id("adunitcodevalue")
                .ext(givenImpExt(getRubiconNode(), null, null))
                .build();

        // when
        final BidRequest result = GreenbidsPayloadUpdater.update(
                givenBidRequest(identity(), List.of(givenImp)),
                Map.of("adunitcodevalue", Map.of("rubicon", false, "appnexus", false, "pubmatic", false)));

        // then
        assertThat(result.getImp()).isEmpty();

    }

}
