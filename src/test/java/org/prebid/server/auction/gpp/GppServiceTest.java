package org.prebid.server.auction.gpp;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import org.junit.Test;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.auction.gpp.processor.GppContextProcessor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GppServiceTest {

    @Test
    public void processContextShouldSequentiallyCallEachContextProcessor() {
        // given
        final GppContextProcessor processor1 = gppContext -> {
            gppContext.errors().add("Error added by processor1");
            return gppContext;
        };

        final GppContextProcessor processor2 = gppContext -> {
            assertThat(gppContext.errors()).containsExactly("Error added by processor1");

            return gppContext.with(UspV1Privacy.of("usPrivacy"));
        };

        final GppService gppService = new GppService(List.of(processor1, processor2));
        final GppContext gppContext = GppContextCreator.from(givenValidGppString(), List.of(1)).build();

        // when
        final GppContext result = gppService.processContext(gppContext);

        // then
        assertThat(result.errors()).containsExactly("Error added by processor1");
        assertThat(result.regions().getUspV1Privacy()).isEqualTo(UspV1Privacy.of("usPrivacy"));
    }

    private static String givenValidGppString() {
        try {
            return new GppModel().encode();
        } catch (EncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
