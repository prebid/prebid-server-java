package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import org.junit.Test;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class GppContextCreatorTest {

    @Test
    public void fromShouldReturnExpectedDefaultGppContextWrapper() {
        // when
        final GppContextWrapper gppContextWrapper = GppContextCreator.from(null, null).build();

        // then
        assertThat(gppContextWrapper.getGppContext()).satisfies(gppContext -> {
            assertThat(gppContext.scope()).isEqualTo(GppContext.Scope.of(null, null));
            assertThat(gppContext.regions()).isEqualTo(GppContext.Regions.builder().build());
        });

        assertThat(gppContextWrapper.getErrors()).isEmpty();
    }

    @Test
    public void fromShouldReturnGppContextWrapperWithErrorOnInvalidGpp() {
        // when
        final GppContextWrapper gppContextWrapper = GppContextCreator.from("invalid", null).build();

        // then
        assertThat(gppContextWrapper.getGppContext()).satisfies(gppContext -> {
            assertThat(gppContext.scope()).isEqualTo(GppContext.Scope.of(null, null));
            assertThat(gppContext.regions()).isEqualTo(GppContext.Regions.builder().build());
        });
        assertThat(gppContextWrapper.getErrors())
                .containsExactly("GPP string invalid: Undecodable FibonacciIntegerRange '101111011'");
    }

    @Test
    public void fromShouldReturnExpectedGppContextWrapper() {
        // when
        final GppContextWrapper gppContextWrapper = GppContextCreator.from(givenValidGppString(), List.of(1, 2))
                .with(TcfEuV2Privacy.of(1, "consent"))
                .with(UspV1Privacy.of("usPrivacy"))
                .build();

        // then
        assertThat(gppContextWrapper.getGppContext()).satisfies(gppContext -> {
            assertThat(gppContext.scope()).satisfies(scope -> {
                assertThat(scope.getGppModel()).isNotNull();
                assertThat(scope.getSectionsIds())
                        .containsExactlyInAnyOrder(1, 2);
            });
            assertThat(gppContext.regions()).isEqualTo(GppContext.Regions.builder()
                    .tcfEuV2Privacy(TcfEuV2Privacy.of(1, "consent"))
                    .uspV1Privacy(UspV1Privacy.of("usPrivacy"))
                    .build());
        });
        assertThat(gppContextWrapper.getErrors()).isEmpty();
    }

    private static String givenValidGppString() {
        try {
            return new GppModel().encode();
        } catch (EncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
