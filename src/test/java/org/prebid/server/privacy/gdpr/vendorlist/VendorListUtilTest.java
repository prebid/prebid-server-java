package org.prebid.server.privacy.gdpr.vendorlist;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.Test;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Feature;
import org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialFeature;
import org.prebid.server.privacy.gdpr.vendorlist.proto.SpecialPurpose;
import org.prebid.server.privacy.gdpr.vendorlist.proto.Vendor;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorList;

import java.util.Date;
import java.util.EnumSet;
import java.util.Map;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.ONE;
import static org.prebid.server.privacy.gdpr.vendorlist.proto.PurposeCode.TWO;

public class VendorListUtilTest extends VertxTest {

    @Test
    public void parseVendorListShouldReturnVendorListWhenContentIsValid() throws JsonProcessingException {
        // given
        final VendorList vendorList = givenVendorList();
        final String content = mapper.writeValueAsString(vendorList);

        // when
        final VendorList result = VendorListUtil.parseVendorList(content, jacksonMapper);

        // then
        assertThat(result).isEqualTo(vendorList);
    }

    @Test
    public void parseVendorListShouldThrowExceptionWhenContentCannotBeParsed() {
        // when and then
        assertThatThrownBy(() -> VendorListUtil.parseVendorList("invalid", jacksonMapper))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Cannot parse vendor list from: invalid");
    }

    @Test
    public void vendorListIsValidShouldReturnTrueWhenVendorListIsValid() {
        // when and then
        assertThat(VendorListUtil.vendorListIsValid(givenVendorList())).isTrue();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorListVersionIsNull() {
        // given
        final VendorList vendorList = VendorList.of(null, new Date(), givenVendorMap());

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenLastUpdatedIsNull() {
        // given
        final VendorList vendorList = VendorList.of(1, null, givenVendorMap());

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorsIsNull() {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(), null);

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorsIsEmpty() {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(), emptyMap());

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorIsNull() {
        // given
        final VendorList vendorList = VendorList.of(1, new Date(), singletonMap(1, null));

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorIdIsNull() {
        // given
        final Vendor vendor = givenVendor().toBuilder().id(null).build();
        final VendorList vendorList = givenVendorListWithVendor(vendor);

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorPurposesIsNull() {
        // given
        final Vendor vendor = givenVendor().toBuilder().purposes(null).build();
        final VendorList vendorList = givenVendorListWithVendor(vendor);

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorLegIntPurposesIsNull() {
        // given
        final Vendor vendor = givenVendor().toBuilder().legIntPurposes(null).build();
        final VendorList vendorList = givenVendorListWithVendor(vendor);

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorFlexiblePurposesIsNull() {
        // given
        final Vendor vendor = givenVendor().toBuilder().flexiblePurposes(null).build();
        final VendorList vendorList = givenVendorListWithVendor(vendor);

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorSpecialPurposesIsNull() {
        // given
        final Vendor vendor = givenVendor().toBuilder().specialPurposes(null).build();
        final VendorList vendorList = givenVendorListWithVendor(vendor);

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorFeaturesIsNull() {
        // given
        final Vendor vendor = givenVendor().toBuilder().features(null).build();
        final VendorList vendorList = givenVendorListWithVendor(vendor);

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    @Test
    public void vendorListIsValidShouldReturnFalseWhenVendorSpecialFeaturesIsNull() {
        // given
        final Vendor vendor = givenVendor().toBuilder().specialFeatures(null).build();
        final VendorList vendorList = givenVendorListWithVendor(vendor);

        // when and then
        assertThat(VendorListUtil.vendorListIsValid(vendorList)).isFalse();
    }

    private static VendorList givenVendorList() {
        return VendorList.of(1, new Date(), givenVendorMap());
    }

    private static VendorList givenVendorListWithVendor(Vendor vendor) {
        return VendorList.of(1, new Date(), singletonMap(vendor.getId() != null ? vendor.getId() : 1, vendor));
    }

    private static Map<Integer, Vendor> givenVendorMap() {
        return singletonMap(52, givenVendor());
    }

    private static Vendor givenVendor() {
        return Vendor.builder()
                .id(52)
                .purposes(EnumSet.of(ONE))
                .legIntPurposes(EnumSet.of(TWO))
                .flexiblePurposes(EnumSet.noneOf(PurposeCode.class))
                .specialPurposes(EnumSet.noneOf(SpecialPurpose.class))
                .features(EnumSet.noneOf(Feature.class))
                .specialFeatures(EnumSet.noneOf(SpecialFeature.class))
                .build();
    }
}
