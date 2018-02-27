package org.prebid.server.bidder.rubicon;

import com.iab.openrtb.request.Format;
import org.junit.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RubiconSizeTest {

    @Test
    public void shouldReturnIdForKnownSize() {
        assertThat(RubiconSize.toId(Format.builder().w(300).h(250).build())).isEqualTo(15);
    }

    @Test
    public void shouldReturnZeroForUnknownSize() {
        assertThat(RubiconSize.toId(Format.builder().w(543).h(987).build())).isEqualTo(0);
    }
}
