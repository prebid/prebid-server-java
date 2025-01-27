package org.prebid.server.bidder.adverxo;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.currency.CurrencyConversionService;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@ExtendWith(MockitoExtension.class)

public class AdverxoBidderTest extends VertxTest {

    private static final String ENDPOINT_URL = "https://test.endpoint.com";

    @Mock
    private CurrencyConversionService currencyConversionService;

    private AdverxoBidder target;

    @BeforeEach
    public void setUp() {
        target = new AdverxoBidder(ENDPOINT_URL, jacksonMapper, currencyConversionService);
    }

    @Test
    public void creationShouldFailOnInvalidEndpointUrl() {
        assertThatIllegalArgumentException().isThrownBy(() -> new AdverxoBidder(
                "invalid_url",
                jacksonMapper,
                currencyConversionService));
    }
}
