package org.prebid.server.bidder.huaweiads;

import org.junit.Test;

import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

import static org.assertj.core.api.Assertions.assertThat;

public class ClientTimeFormatterTest {

    private static final DateTimeFormatter TIME_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSZ");

    @Test
    public void formatShouldReturnCurrentTimeWhenClientTimeIsNull() {
        final String actual = ClientTimeFormatter.format(null);
        assertThat(actual).isNotBlank();
        assertThat(OffsetDateTime.parse(actual, TIME_FORMATTER).getOffset())
                .isEqualTo(OffsetDateTime.now().getOffset());
    }

    @Test
    public void formatShouldReturnCurrentTimeWhenClientTimeThatCanNotBeParsed() {
        final String actual = ClientTimeFormatter.format("2022-02-2404:00:05.000+0200");

        assertThat(actual).isNotBlank();
        assertThat(actual).isNotEqualTo("2022-02-2404:00:05.000+0200");
        assertThat(OffsetDateTime.parse(actual, TIME_FORMATTER).getOffset())
                .isEqualTo(OffsetDateTime.now().getOffset());
    }

    @Test
    public void formatShouldReturnClientTimeAsIsWhenClientTimeHasCorrectFormatWithOffset() {
        final String actual = ClientTimeFormatter.format("2022-02-24 04:00:05.000+0200");
        assertThat(actual).isEqualTo("2022-02-24 04:00:05.000+0200");
    }

}
