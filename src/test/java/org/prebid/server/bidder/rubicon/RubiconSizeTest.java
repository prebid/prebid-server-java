package org.prebid.server.bidder.rubicon;

import com.iab.openrtb.request.Format;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
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
    public void idToSizeShouldReturnSizesForKnownIds() {
        // given
        final List<Format> formats = Arrays.asList(Format.builder().w(300).h(250).build(),
                Format.builder().w(728).h(90).build());
        final List<Integer> sizeIds = Arrays.asList(15, 2, 3);

        //when
        final List<Format> result = RubiconSize.idToSize(sizeIds);

        //then
        assertThat(result).isEqualTo(formats);
    }

    @Test
    public void idToSizeShouldReturnEmptyListIfNoIdsFound() {
        assertThat(RubiconSize.idToSize(Arrays.asList(3, 11))).isEqualTo(Collections.emptyList());
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
