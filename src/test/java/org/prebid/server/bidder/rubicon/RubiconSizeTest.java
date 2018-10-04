package org.prebid.server.bidder.rubicon;

import com.iab.openrtb.request.Format;
import org.junit.Test;

import java.util.List;

import static java.util.Arrays.asList;
import static org.assertj.core.api.Assertions.assertThat;

public class RubiconSizeTest {

    @Test
    public void toIdShouldReturnIdForKnownSize() {
        assertThat(RubiconSize.toId(Format.builder().w(300).h(250).build())).isEqualTo(15);
    }

    @Test
    public void toIdShouldReturnZeroForUnknownSize() {
        assertThat(RubiconSize.toId(Format.builder().w(543).h(987).build())).isEqualTo(0);
    }

    @Test
    public void comparatorShouldHonorMasRules() {
        // given
        final List<Integer> sizes = asList(44, 59, 8, 2, 19, 9, 101, 15);

        // when
        sizes.sort(RubiconSize.comparator());

        // then
        assertThat(sizes).containsExactly(15, 2, 9, 8, 19, 44, 59, 101);
    }
}
