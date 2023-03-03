package org.prebid.server.auction.gpp.model;

import com.iab.gpp.encoder.GppModel;
import com.iab.gpp.encoder.error.EncodingException;
import org.junit.Test;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class GppContextCreatorTest {

    @Test
    public void fromShouldReturnExpectedDefaultGppContext() {
        // when
        final GppContext gppContext = GppContextCreator.from(null, null).build();

        // then
        assertThat(gppContext.scope()).isEqualTo(GppContext.Scope.of(null, null));
        assertThat(gppContext.regions()).isEqualTo(GppContext.Regions.builder()
                .tcfEuV2Privacy(TcfEuV2Privacy.of(null, null))
                .uspV1Privacy(UspV1Privacy.of(null))
                .build());
        assertThat(gppContext.errors()).isEmpty();
    }

    @Test
    public void fromShouldReturnGppContextWithErrorOnInvalidGpp() {
        // when
        final GppContext gppContext = GppContextCreator.from("invalid", null).build();

        // then
        assertThat(gppContext.scope()).isEqualTo(GppContext.Scope.of(null, null));
        assertThat(gppContext.regions()).isEqualTo(GppContext.Regions.builder()
                .tcfEuV2Privacy(TcfEuV2Privacy.of(null, null))
                .uspV1Privacy(UspV1Privacy.of(null))
                .build());
        assertThat(gppContext.errors())
                .containsExactly("GPP string invalid: Undecodable FibonacciIntegerRange '101111011'");
    }

    @Test
    public void fromShouldReturnExpectedGppContext() {
        // when
        final GppContext gppContext = GppContextCreator.from(givenValidGppString(), List.of(1, 2))
                .with(TcfEuV2Privacy.of(1, "consent"))
                .with(UspV1Privacy.of("usPrivacy"))
                .build();

        // then
        assertThat(gppContext.scope()).satisfies(scope -> {
            assertThat(scope.getGppModel()).isNotNull();
            assertThat(scope.getSectionsIds())
                    .isInstanceOf(Set.class)
                    .containsExactlyInAnyOrder(1, 2);
        });
        assertThat(gppContext.regions()).isEqualTo(GppContext.Regions.builder()
                .tcfEuV2Privacy(TcfEuV2Privacy.of(1, "consent"))
                .uspV1Privacy(UspV1Privacy.of("usPrivacy"))
                .build());
        assertThat(gppContext.errors()).isEmpty();
    }

    private static String givenValidGppString() {
        try {
            return new GppModel().encode();
        } catch (EncodingException e) {
            throw new RuntimeException(e);
        }
    }
}
