package org.prebid.server.auction.gpp;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import org.junit.jupiter.api.Test;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;

import java.util.List;

import static java.util.Collections.singletonList;
import static org.assertj.core.api.Assertions.assertThat;

public class GppServiceTest {

    @Test
    public void processContextShouldSequentiallyCallEachContextProcessor() {
        // given
        final GppContextProcessor processor1 = gppContext -> GppContextWrapper.of(
                gppContext.with(TcfEuV2Privacy.of(1, "consent")),
                singletonList("Error added by processor1"));

        final GppContextProcessor processor2 = gppContext -> {
            assertThat(gppContext.regions().getTcfEuV2Privacy())
                    .isEqualTo(TcfEuV2Privacy.of(1, "consent"));

            return GppContextWrapper.of(
                    gppContext.with(UspV1Privacy.of("usPrivacy")),
                    singletonList("Error added by processor2"));
        };

        final GppService gppService = new GppService(List.of(processor1, processor2));
        final GppContextWrapper gppContext = GppContextCreator.from(givenValidGppString(), List.of(1)).build();

        // when
        final GppContextWrapper result = gppService.processContext(gppContext);

        // then
        assertThat(result.getErrors()).containsExactly("Error added by processor1", "Error added by processor2");
        assertThat(result.getGppContext().regions()).isEqualTo(GppContext.Regions.builder()
                .tcfEuV2Privacy(TcfEuV2Privacy.of(1, "consent"))
                .uspV1Privacy(UspV1Privacy.of("usPrivacy"))
                .build());
    }

    private static String givenValidGppString() {
        try {
            return new GppModel().encode();
        } catch (EncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
