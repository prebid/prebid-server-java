package org.rtb.vexing.auction;

import org.junit.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

public class CpmBucketTest {

    @Test
    public void shouldReturnMaxBucketIfCpmExceedsIt() {
        assertThat(CpmBucket.fromCpm(new BigDecimal(21), CpmBucket.PriceGranularity.auto)).isEqualTo("20.00");
    }

    @Test
    public void shouldReturnCpmGivenLowGranularity() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(3.87), CpmBucket.PriceGranularity.low)).isEqualTo("3.50");
    }

    @Test
    public void shouldReturnCpmGivenMedGranularity() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(3.87), CpmBucket.PriceGranularity.med)).isEqualTo("3.80");
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(3.87), CpmBucket.PriceGranularity.medium)).isEqualTo("3.80");
    }

    @Test
    public void shouldReturnCpmGivenHighGranularity() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(3.87), CpmBucket.PriceGranularity.high)).isEqualTo("3.87");
    }

    @Test
    public void shouldReturnCpmGivenAutoGranularityAndFirstBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(3.87), CpmBucket.PriceGranularity.auto)).isEqualTo("3.85");
    }

    @Test
    public void shouldReturnCpmGivenAutoGranularityAndSecondBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(5.32), CpmBucket.PriceGranularity.auto)).isEqualTo("5.30");
    }

    @Test
    public void shouldReturnCpmGivenAutoGranularityAndThirdBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(13.59), CpmBucket.PriceGranularity.auto)).isEqualTo("13.50");
    }

    @Test
    public void shouldReturnCpmGivenDenseGranularityAndFirstBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(2.87), CpmBucket.PriceGranularity.dense)).isEqualTo("2.87");
    }

    @Test
    public void shouldReturnCpmGivenDenseGranularityAndSecondBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(5.36), CpmBucket.PriceGranularity.dense)).isEqualTo("5.35");
    }

    @Test
    public void shouldReturnCpmGivenDenseGranularityAndThirdBucket() {
        assertThat(CpmBucket.fromCpm(BigDecimal.valueOf(13.69), CpmBucket.PriceGranularity.dense)).isEqualTo("13.50");
    }
}