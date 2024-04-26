package org.prebid.server.bidder.huaweiads;

import org.junit.jupiter.api.BeforeEach;
import org.junit.Rule;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

public class ClientTimeFormatterTest {

    private static final long FEB_24_2022_04_00_05 = 1645668005650L;

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private Clock clock;

    private ClientTimeFormatter target;

    @BeforeEach
    public void before() {
        target = new ClientTimeFormatter(clock);
        given(clock.instant()).willReturn(Instant.ofEpochMilli(FEB_24_2022_04_00_05));
        given(clock.getZone()).willReturn(ZoneId.of("+02:00"));
    }

    @Test
    public void nowShouldReturnCurrentTime() {
        final String actual = target.now();
        assertThat(actual).isEqualTo("2022-02-24 04:00:05.650+0200");
    }

    @Test
    public void formatShouldReturnCurrentTimeWhenClientTimeIsNull() {
        final String actual = target.format(null);
        assertThat(actual).isEqualTo("2022-02-24 04:00:05.650+0200");
    }

    @Test
    public void formatShouldReturnCurrentTimeWhenClientTimeThatCanNotBeParsed() {
        final String actual = target.format("2022-02-2404:00:05.000+0200");
        assertThat(actual).isEqualTo("2022-02-24 04:00:05.650+0200");
    }

    @Test
    public void formatShouldReturnClientTimeAsIsWhenClientTimeHasCorrectFormatWithOffset() {
        final String actual = target.format("2023-02-24 04:00:05.000+0200");
        assertThat(actual).isEqualTo("2023-02-24 04:00:05.000+0200");
    }

    @Test
    public void formatShouldReturnClientTimeWithCurrentTimeZoneWhenClientTimeHasCorrectFormatWithoutOffset() {
        final String actual = target.format("2023-02-24 05:00:05.000");
        assertThat(actual).isEqualTo("2023-02-24 05:00:05.000+0200");
    }

}
