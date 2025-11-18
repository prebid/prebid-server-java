package org.prebid.server.validation;

import com.fasterxml.jackson.databind.node.TextNode;
import com.iab.openrtb.request.App;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Deal;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Eid;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.prebid.server.VertxTest;
import org.prebid.server.auction.model.debug.DebugContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.proto.openrtb.ext.request.ExtDevice;
import org.prebid.server.proto.openrtb.ext.request.ExtDeviceInt;
import org.prebid.server.proto.openrtb.ext.request.ExtDevicePrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtGranularityRange;
import org.prebid.server.proto.openrtb.ext.request.ExtMediaTypePriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtPriceGranularity;
import org.prebid.server.proto.openrtb.ext.request.ExtRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestBidAdjustmentFactors;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodes;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidAlternateBidderCodesBidder;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidData;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidDataEidPermissions;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebidSchain;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestTargeting;
import org.prebid.server.proto.openrtb.ext.request.ExtSite;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;
import org.prebid.server.proto.openrtb.ext.request.ImpMediaType;
import org.prebid.server.settings.model.Account;
import org.prebid.server.validation.model.ValidationResult;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mock.Strictness.LENIENT;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
public class RequestValidatorTest extends VertxTest {

    private static final String RUBICON = "rubicon";
    private static final String ACCOUNT_ID = "accountId";

    @Mock(strictness = LENIENT)
    private BidderCatalog bidderCatalog;
    @Mock
    private ImpValidator impValidator;
    @Mock
    private Metrics metrics;

    private RequestValidator target;

    @BeforeEach
    public void setUp() {
        given(bidderCatalog.isValidName(eq(RUBICON))).willReturn(true);
        given(bidderCatalog.isActive(eq(RUBICON))).willReturn(true);

        target = new RequestValidator(bidderCatalog, impValidator, metrics, jacksonMapper, 0.01, false, true, true);
    }

    @Test
    public void validateShouldReturnOnlyOneErrorAtATime() {
        // given
        final BidRequest bidRequest = BidRequest.builder().build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result).isNotNull();
        assertThat(result.getErrors()).hasSize(1);
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestIdIsEmpty() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id("").build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestIdIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id(null).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request missing required field: \"id\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenTmaxIsNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().id("1").tmax(-100L).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.tmax must be nonnegative. Got -100");
    }

    @Test
    public void validateShouldNotReturnValidationMessageWhenTmaxIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().tmax(null).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenCurIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().cur(null).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "currency was not defined either in request.cur or in configuration field adServerCurrency");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenNumberOfImpsIsZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().imp(null).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp must contain at least one element");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAliasesKeyDoesntContainAliasgvlidsKey() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("pubmatic", "rubicon"))
                        .aliasgvlids(singletonMap("between", 2))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors())
                .containsExactly("request.ext.prebid.aliasgvlids. vendorId 2 refers to unknown bidder alias: between");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAliasgvlidsValueLowerThatOne() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("pubmatic", "rubicon"))
                        .aliasgvlids(singletonMap("pubmatic", 0))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors())
                .containsExactly("request.ext.prebid.aliasgvlids. Invalid vendorId 0 for alias: pubmatic. "
                        + "Choose a different vendorId, or remove this entry.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteIdAndPageIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().site(Site.builder().id(null).build()).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site should include at least one of request.site.id or request.site.page");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteIdIsEmptyStringAndPageIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().site(Site.builder().id("").build()).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site should include at least one of request.site.id or request.site.page");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenPageIdIsNullAndSiteIdIsPresent() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().site(Site.builder().id("1").page(null).build()).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldEmptyValidationMessagesWhenSitePageIsEmptyString() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().site(Site.builder().id("1").page("").build()).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteIdAndPageBothEmpty() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().site(Site.builder().id("").page("").build()).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site should include at least one of request.site.id or request.site.page");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteExtAmpIsNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .site(Site.builder().id("id").page("page").ext(ExtSite.of(-1, null)).build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site.ext.amp must be either 1, 0, or undefined");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenSiteExtAmpIsGreaterThanOne() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .site(Site.builder().id("id").page("page").ext(ExtSite.of(2, null)).build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.site.ext.amp must be either 1, 0, or undefined");
    }

    @Test
    public void validateShouldFailWhenDoohIdAndVenuetypeAreNulls() {
        // given
        final Dooh invalidDooh = Dooh.builder().id(null).venuetype(null).build();
        final BidRequest bidRequest = validBidRequestBuilder().site(null).dooh(invalidDooh).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.dooh should include at least one of request.dooh.id or request.dooh.venuetype.");
    }

    @Test
    public void validateShouldFailWhenDoohIdIsNullAndVenuetypeIsEmpty() {
        // given
        final Dooh invalidDooh = Dooh.builder().id(null).venuetype(Collections.emptyList()).build();
        final BidRequest bidRequest = validBidRequestBuilder().site(null).dooh(invalidDooh).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.dooh should include at least one of request.dooh.id or request.dooh.venuetype.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestAppAndRequestSiteBothMissed() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .site(null)
                .app(null)
                .dooh(null)
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("One of request.site or request.app or request.dooh must be defined");
    }

    @Test
    public void validateShouldFailWhenDoohSiteAndAppArePresentInRequestAndStrictValidationIsEnabled() {
        // when
        target = new RequestValidator(bidderCatalog, impValidator, metrics, jacksonMapper, 0.01, true, true, true);
        final BidRequest invalidRequest = validBidRequestBuilder()
                .dooh(Dooh.builder().build())
                .app(App.builder().build())
                .site(Site.builder().build())
                .build();
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), invalidRequest, null, null);

        // then
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.app and request.dooh and request.site are present, "
                        + "but no more than one of request.site or request.app or request.dooh can be defined");
    }

    @Test
    public void validateShouldFailWhenSiteAndAppArePresentInRequestAndStrictValidationIsEnabled() {
        // when
        target = new RequestValidator(bidderCatalog, impValidator, metrics, jacksonMapper, 0.01, true, true, true);
        final BidRequest invalidRequest = validBidRequestBuilder()
                .app(App.builder().build())
                .site(Site.builder().build())
                .build();
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), invalidRequest, null, null);

        // then
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.app and request.site are present, "
                        + "but no more than one of request.site or request.app or request.dooh can be defined");
    }

    @Test
    public void validateShouldFailWhenDoohAndSiteArePresentInRequestAndStrictValidationIsEnabled() {
        // when
        target = new RequestValidator(bidderCatalog, impValidator, metrics, jacksonMapper, 0.01, true, true, true);
        final BidRequest invalidRequest = validBidRequestBuilder()
                .dooh(Dooh.builder().build())
                .site(Site.builder().build())
                .build();
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), invalidRequest, null, null);

        // then
        verify(metrics).updateAlertsMetrics(MetricName.general);
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.dooh and request.site are present, "
                        + "but no more than one of request.site or request.app or request.dooh can be defined");
    }

    @Test
    public void validateShouldFailWhenDoohAndAppArePresentInRequestAndStrictValidationIsEnabled() {
        // when
        target = new RequestValidator(bidderCatalog, impValidator, metrics, jacksonMapper, 0.01, true, true, true);
        final BidRequest invalidRequest = validBidRequestBuilder()
                .dooh(Dooh.builder().build())
                .app(App.builder().build())
                .build();
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), invalidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.app and request.dooh and request.site are present, "
                        + "but no more than one of request.site or request.app or request.dooh can be defined");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinWidthPercIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(ExtDevice.of(null, null, ExtDevicePrebid.of(ExtDeviceInt.of(null, null))))
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.device.ext.prebid.interstitial.minwidthperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinWidthPercIsLessThanZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(ExtDevice.of(null, null, ExtDevicePrebid.of(ExtDeviceInt.of(-1, null))))
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.device.ext.prebid.interstitial.minwidthperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinWidthPercGreaterThanHundred() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(ExtDevice.of(null, null, ExtDevicePrebid.of(ExtDeviceInt.of(101, null))))
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.device.ext.prebid.interstitial.minwidthperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinHeightPercIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(ExtDevice.of(null, null, ExtDevicePrebid.of(ExtDeviceInt.of(50, null))))
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.device.ext.prebid.interstitial.minheightperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinHeightPercIsLessThanZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(ExtDevice.of(null, null, ExtDevicePrebid.of(ExtDeviceInt.of(50, -1))))
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.device.ext.prebid.interstitial.minheightperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMinHeightPercGreaterThanHundred() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .device(Device.builder()
                        .ext(ExtDevice.of(null, null, ExtDevicePrebid.of(ExtDeviceInt.of(50, 101))))
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.device.ext.prebid.interstitial.minheightperc must be a number between 0 and 100");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenBidRequestIsOk() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder().build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRequestHaveDuplicatedImpIds() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .imp(asList(Imp.builder()
                                .id("11")
                                .build(),
                        Imp.builder()
                                .id("11")
                                .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.imp[0].id and request.imp[1].id are both \"11\". Imp IDs must be unique.");
    }

    @Test
    public void validateShouldNotReturnValidationMessageIfUserExtIsEmptyJsonObject() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(ExtUser.builder().build())
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldNotReturnErrorMessageWhenRegsIsEmptyObject() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .regs(Regs.builder().build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPrebidBuyerIdsContainsNoValues() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(emptyMap()))
                                .build())
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.prebid requires a \"buyeruids\" property with at least one ID defined."
                        + " If none exist, then request.user.ext.prebid should not be defined");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidsPermissionsHasNullElement() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(null, singletonList(null)))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.ext.prebid.data.eidpermissions[i] can't be null");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidsPermissionsBiddersIsNull() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(null,
                                singletonList(ExtRequestPrebidDataEidPermissions.of("source", null))))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.ext.prebid.data.eidpermissions[].bidders[] required values but was empty or"
                        + " null");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidsPermissionsBiddersIsEmpty() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(null,
                                singletonList(ExtRequestPrebidDataEidPermissions.of("source", emptyList()))))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.ext.prebid.data.eidpermissions[].bidders[] required values but was empty or"
                        + " null");
    }

    @Test
    public void validateShouldReturnWarningsMessageWhenEidsPermissionsBidderIsNotRecognizedBidderAndDebugEnabled() {
        // given
        given(bidderCatalog.isValidName(eq("bidder1"))).willReturn(false);
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(null,
                                singletonList(
                                        ExtRequestPrebidDataEidPermissions.of("source", singletonList("bidder1")))))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(
                Account.empty(ACCOUNT_ID), bidRequest, null, DebugContext.of(true, false, null));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).hasSize(1)
                .containsOnly(
                        "request.ext.prebid.data.eidPermissions[].bidders[] unrecognized biddercode: 'bidder1'");
    }

    @Test
    public void validateShouldNotReturnWarningsMessageWhenEidsPermissionsBidderIsNotRecognizedBidderAndDebugDisabled() {
        // given
        given(bidderCatalog.isValidName(eq("bidder1"))).willReturn(false);
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(null,
                                singletonList(
                                        ExtRequestPrebidDataEidPermissions.of("source", singletonList("bidder1")))))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(
                Account.empty(ACCOUNT_ID), bidRequest, null, DebugContext.of(false, false, null));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).isEmpty();
    }

    @Test
    public void validateShouldReturnWarningMessageWhenEidsPermissionsBidderHasBlankValueAndDebugEnabled() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(null,
                                singletonList(
                                        ExtRequestPrebidDataEidPermissions.of("source", singletonList(" ")))))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(
                Account.empty(ACCOUNT_ID), bidRequest, null, DebugContext.of(true, false, null));

        // then
        assertThat(result.getErrors()).isEmpty();
        assertThat(result.getWarnings()).hasSize(1)
                .containsOnly("request.ext.prebid.data.eidPermissions[].bidders[] unrecognized biddercode: ' '");
    }

    @Test
    public void validateShouldNotReturnValidationErrorWhenBidderIsAlias() {
        // given
        given(bidderCatalog.isValidName(eq("bidder1Alias"))).willReturn(false);
        given(bidderCatalog.isValidName(eq("bidder1"))).willReturn(true);
        given(bidderCatalog.isActive(eq("bidder1"))).willReturn(true);

        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("bidder1Alias", "bidder1"))
                        .data(ExtRequestPrebidData.of(null,
                                singletonList(
                                        ExtRequestPrebidDataEidPermissions.of("source", singletonList("bidder1")))))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldNotReturnValidationErrorWhenBidderIsAsterisk() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(null,
                                singletonList(
                                        ExtRequestPrebidDataEidPermissions.of("source", singletonList("*")))))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidsPermissionsHasMissingSource() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .data(ExtRequestPrebidData.of(null,
                                singletonList(
                                        ExtRequestPrebidDataEidPermissions.of(null, singletonList("bidder1")))))
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Missing required value request.ext.prebid.data.eidPermissions[].source");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenCantParseTargetingPriceGranularity() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(new TextNode("pricegranularity"))
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Error while parsing request.ext.prebid.targeting.pricegranularity");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRangesAreEmptyList() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(ExtPriceGranularity.of(2, emptyList())))
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: empty granularity definition supplied");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenIncrementIsZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(ExtPriceGranularity
                                        .of(2, singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5),
                                                BigDecimal.valueOf(0))))))
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: increment must be a nonzero positive number");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenIncrementIsMissed() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(ExtPriceGranularity.of(
                                        2,
                                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), null)))))
                                .build())
                        .build()))
                .build();
        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: increment must be a nonzero positive number");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenIncrementIsNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(ExtPriceGranularity.of(
                                        2,
                                        singletonList(ExtGranularityRange.of(
                                                BigDecimal.valueOf(5), BigDecimal.valueOf(-1))))))
                                .build())
                        .build()))
                .build();
        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: increment must be a nonzero positive number");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPrecisionIsNegative() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(ExtPriceGranularity.of(-1, singletonList(
                                        ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01))))))
                                .build())
                        .build()))
                .build();
        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: precision must be non-negative");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMediaTypePriceGranularityTypesAreAllNull() {
        // given
        final ExtPriceGranularity priceGranularity = ExtPriceGranularity.of(1, singletonList(
                ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01))));

        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(priceGranularity))
                                .mediatypepricegranularity(ExtMediaTypePriceGranularity.of(null, null, null))
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Media type price granularity error: must have at least one media type present");
    }

    @Test
    public void validateShouldReturnValidationMessageWithCorrectMediaType() {
        // given
        final ExtPriceGranularity priceGranularity = ExtPriceGranularity.of(1, singletonList(
                ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01))));
        final ExtMediaTypePriceGranularity mediaTypePriceGranuality = ExtMediaTypePriceGranularity.of(
                mapper.valueToTree(ExtPriceGranularity.of(
                        -1,
                        singletonList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(1))))),
                null,
                null);
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(priceGranularity))
                                .mediatypepricegranularity(mediaTypePriceGranuality)
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Banner price granularity error: precision must be non-negative");
    }

    @Test
    public void validateShouldReturnValidationMessageForInvalidTargetingPrefix() {
        // given
        final ExtPriceGranularity priceGranularity = ExtPriceGranularity.of(1, singletonList(
                ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01))));
        final String prefix = "1234567890";
        final int truncateattrchars = 10;
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(priceGranularity))
                                .includebidderkeys(true)
                                .includewinners(true)
                                .truncateattrchars(truncateattrchars)
                                .prefix(prefix)
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("ext.prebid.targeting: decrease prefix length or increase truncateattrchars"
                        + " by " + (prefix.length() + 11 - truncateattrchars) + " characters");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRangesContainsMissedMaxValue() {
        final ExtPriceGranularity priceGranuality = ExtPriceGranularity.of(2,
                asList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01)),
                        ExtGranularityRange.of(null, BigDecimal.valueOf(0.05))));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(priceGranuality))
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: max value should not be missed");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRangesAreNotOrderedByMaxValue() {
        final ExtPriceGranularity priceGranuality = ExtPriceGranularity.of(2,
                asList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01)),
                        ExtGranularityRange.of(BigDecimal.valueOf(2), BigDecimal.valueOf(0.05))));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(priceGranuality))
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: range list must be ordered with increasing \"max\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenRangesAreNotOrderedByMaxValueInTheMiddleOfRangeList() {
        // given
        final ExtPriceGranularity priceGranuality = ExtPriceGranularity.of(2,
                asList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01)),
                        ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(0.05)),
                        ExtGranularityRange.of(BigDecimal.valueOf(8), BigDecimal.valueOf(0.05))));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(priceGranuality))
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: range list must be ordered with increasing \"max\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenIncrementIsNegativeInNotLeadingElement() {
        // given
        final ExtPriceGranularity priceGranularity = ExtPriceGranularity.of(2,
                asList(ExtGranularityRange.of(BigDecimal.valueOf(5), BigDecimal.valueOf(0.01)),
                        ExtGranularityRange.of(BigDecimal.valueOf(10), BigDecimal.valueOf(-0.05))));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .targeting(ExtRequestTargeting.builder()
                                .pricegranularity(mapper.valueToTree(priceGranularity))
                                .build())
                        .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("Price granularity error: increment must be a nonzero positive number");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenPrebidBuyerIdsContainsUnknownBidder() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("unknown-bidder", "42")))
                                .build())
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.ext.unknown-bidder is neither a known bidder name "
                        + "nor an alias in request.ext.prebid.aliases");
    }

    @Test
    public void validateShouldNotReturnAnyErrorInValidationResultWhenPrebidBuyerIdIsKnownBidderAlias() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(ExtRequestPrebid.builder()
                        .aliases(singletonMap("unknown-bidder", "rubicon"))
                        .build()))
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("unknown-bidder", "42")))
                                .build())
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldNotReturnAnyErrorInValidationResultWhenPrebidBuyerIdIsKnownBidder() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .ext(ExtUser.builder()
                                .prebid(ExtUserPrebid.of(singletonMap("rubicon", "42")))
                                .build())
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldNotReturnValidationMessageWhenEidsIsEmpty() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .eids(emptyList())
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenEidHasEmptySource() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .user(User.builder()
                        .eids(singletonList(Eid.builder().build()))
                        .build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.user.eids[0] missing required field: \"source\"");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAliasNameEqualsToBidderItPointsOn() {
        // given
        final ExtRequest ext = ExtRequest.of(ExtRequestPrebid.builder()
                .aliases(singletonMap("rubicon", "rubicon"))
                .build());
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("""
                        request.ext.prebid.aliases.rubicon defines a no-op alias. \
                        Choose a different alias, or remove this entry""");
    }

    @Test
    public void validateShouldReturnValidationErrorMessageWhenAliasPointOnNotValidBidderName() {
        // given
        final ExtRequest ext = ExtRequest.of(ExtRequestPrebid.builder()
                .aliases(singletonMap("alias", "fake"))
                .build());
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.ext.prebid.aliases.alias refers to unknown bidder: fake");
    }

    @Test
    public void validateShouldReturnValidationWarningMessageWhenAliasPointOnNotValidBidderName() {
        // given
        final ExtRequest ext = ExtRequest.of(ExtRequestPrebid.builder()
                .aliases(singletonMap("alias", "fake"))
                .build());
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        target = new RequestValidator(bidderCatalog, impValidator, metrics, jacksonMapper, 0.01, false, true, false);
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getWarnings()).hasSize(1)
                .containsOnly("request.ext.prebid.aliases.alias refers to unknown bidder: fake");
    }

    @Test
    public void validateShouldReturnValidationErrorMessageWhenAliasPointOnDisabledBidder() {
        // given
        final ExtRequest ext = ExtRequest.of(ExtRequestPrebid.builder()
                .aliases(singletonMap("alias", "appnexus"))
                .build());
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();
        given(bidderCatalog.isValidName("appnexus")).willReturn(true);
        given(bidderCatalog.isActive("appnexus")).willReturn(false);

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.ext.prebid.aliases.alias refers to disabled bidder: appnexus");
    }

    @Test
    public void validateShouldReturnValidationWarningMessageWhenAliasPointOnDisabledBidder() {
        // given
        final ExtRequest ext = ExtRequest.of(ExtRequestPrebid.builder()
                .aliases(singletonMap("alias", "appnexus"))
                .build());
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();
        given(bidderCatalog.isValidName("appnexus")).willReturn(true);
        given(bidderCatalog.isActive("appnexus")).willReturn(false);

        // when
        target = new RequestValidator(bidderCatalog, impValidator, metrics, jacksonMapper, 0.01, false, false, true);
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getWarnings()).hasSize(1)
                .containsOnly("request.ext.prebid.aliases.alias refers to disabled bidder: appnexus");
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenAliasesWasUsed() {
        // given
        final ExtRequest ext = ExtRequest.of(ExtRequestPrebid.builder()
                .aliases(singletonMap("alias", "rubicon"))
                .build());
        final BidRequest bidRequest = validBidRequestBuilder().ext(ext).build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationResultWithErrorsWhenGdprIsNotOneOrZero() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .regs(Regs.builder().gdpr(2).build())
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly("request.regs.ext.gdpr must be either 0 or 1");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAdjustmentFactorNegative() {
        // given
        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor("rubicon", BigDecimal.valueOf(-1.1));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(givenAdjustments)
                                .build()))
                .build();
        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.ext.prebid.bidadjustmentfactors.rubicon must be a positive number. Got -1.100000");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAdjustmentMediaFactorNegative() {
        // given
        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(Collections.singletonMap(ImpMediaType.banner,
                        Collections.singletonMap("rubicon", BigDecimal.valueOf(-1.1)))))
                .build();
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(givenAdjustments)
                                .build()))
                .build();
        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.ext.prebid.bidadjustmentfactors.banner.rubicon "
                                + "must be a positive number. Got -1.100000");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenAlternateBidderUnknown() {
        // given
        final ExtRequestPrebidAlternateBidderCodes unknownBidderCodes = ExtRequestPrebidAlternateBidderCodes.of(
                true,
                Map.of("unknownBidder", ExtRequestPrebidAlternateBidderCodesBidder.of(true, Set.of("*"))));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .alternateBidderCodes(unknownBidderCodes)
                                .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                "request.ext.prebid.alternatebiddercodes.bidders.unknownBidder is not a known bidder or alias");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenBidderUnknown() {
        // given
        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor("unknownBidder", BigDecimal.valueOf(1.1F));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(givenAdjustments)
                                .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1).containsOnly(
                "request.ext.prebid.bidadjustmentfactors.unknownBidder is not a known bidder or alias");
    }

    @Test
    public void validateShouldNotFailWhenBidderIsKnownAsAlternativeBidderCode() {
        // given
        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor("unknownBidder", BigDecimal.valueOf(1.1F));
        final ExtRequestPrebidAlternateBidderCodes givenAlternativeBidderCodes =
                ExtRequestPrebidAlternateBidderCodes.of(true, Map.of("rubicon",
                        ExtRequestPrebidAlternateBidderCodesBidder.of(true, Set.of("unknownBidder"))));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(givenAdjustments)
                                .alternateBidderCodes(givenAlternativeBidderCodes)
                                .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMediaBidderUnknown() {
        // given
        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(Collections.singletonMap(ImpMediaType.xNative,
                        Collections.singletonMap("unknownBidder", BigDecimal.valueOf(1.1)))))
                .build();
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(givenAdjustments)
                                .build()))
                .build();
        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).hasSize(1)
                .containsOnly(
                        "request.ext.prebid.bidadjustmentfactors.native.unknownBidder is not a known bidder or alias");
    }

    @Test
    public void validateShouldNotFailWhenMediaBidderIsKnownAsAlternativeBidderCode() {
        // given
        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(Collections.singletonMap(ImpMediaType.xNative,
                        Collections.singletonMap("unknownBidder", BigDecimal.valueOf(1.1)))))
                .build();
        givenAdjustments.addFactor("unknownBidder", BigDecimal.valueOf(1.1F));
        final ExtRequestPrebidAlternateBidderCodes givenAlternativeBidderCodes =
                ExtRequestPrebidAlternateBidderCodes.of(true, Map.of("rubicon",
                        ExtRequestPrebidAlternateBidderCodesBidder.of(true, Set.of("unknownBidder"))));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(givenAdjustments)
                                .alternateBidderCodes(givenAlternativeBidderCodes)
                                .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenBidderIsKnownAndAdjustmentIsValid() {
        // given
        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(Collections.singletonMap(ImpMediaType.xNative,
                        Collections.singletonMap("rubicon", BigDecimal.valueOf(2.1)))))
                .build();
        givenAdjustments.addFactor("rubicon", BigDecimal.valueOf(1.1));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .bidadjustmentfactors(givenAdjustments)
                                .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenBidderIsKnownAliasForCoreBidderAndAdjustmentIsValid() {
        // given
        final String rubiconAlias = "rubicon_alias";
        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder()
                .mediatypes(new EnumMap<>(Collections.singletonMap(ImpMediaType.xNative,
                        Collections.singletonMap("rubicon_alias", BigDecimal.valueOf(2.1)))))
                .build();
        givenAdjustments.addFactor(rubiconAlias, BigDecimal.valueOf(1.1));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .aliases(singletonMap(rubiconAlias, "rubicon"))
                                .bidadjustmentfactors(givenAdjustments)
                                .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnEmptyValidationMessagesWhenBidderIsKnownBidderConfigAliasAndAdjustmentIsValid() {
        // given
        final String rubiconAlias = "rubicon_alias";
        final ExtRequestBidAdjustmentFactors givenAdjustments = ExtRequestBidAdjustmentFactors.builder().build();
        givenAdjustments.addFactor(rubiconAlias, BigDecimal.valueOf(1.1));
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .aliases(singletonMap(rubiconAlias, "rubicon"))
                                .bidadjustmentfactors(givenAdjustments)
                                .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        verify(bidderCatalog).isValidName(rubiconAlias);

        assertThat(result.getErrors()).isEmpty();
    }

    @Test
    public void validateShouldReturnValidationMessageWhenMultipleSchainsForSameBidder() {
        // given
        final BidRequest bidRequest = validBidRequestBuilder()
                .ext(ExtRequest.of(
                        ExtRequestPrebid.builder()
                                .schains(asList(
                                        ExtRequestPrebidSchain.of(asList("bidder1", "bidder2"), null),
                                        ExtRequestPrebidSchain.of(asList("bidder2", "bidder3"), null)))
                                .build()))
                .build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors())
                .containsOnly("request.ext.prebid.schains contains multiple schains for bidder bidder2; "
                        + "it must contain no more than one per bidder.");
    }

    @Test
    public void validateShouldReturnValidationMessageWhenImpValidationFailed() throws ValidationException {
        // given
        doThrow(new ValidationException("imp[0] validation failed"))
                .when(impValidator).validateImps(any(), any(), any());

        final BidRequest bidRequest = validBidRequestBuilder().build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getErrors()).containsOnly("imp[0] validation failed");
    }

    @Test
    public void validateShouldReturnWarningMessageWhenImpValidationWarns() throws ValidationException {
        // given
        doAnswer(invocation -> ((List<String>) invocation.getArgument(2)).add("imp[0] validation warning"))
                .when(impValidator).validateImps(any(), any(), any());

        final BidRequest bidRequest = validBidRequestBuilder().build();

        // when
        final ValidationResult result = target.validate(Account.empty(ACCOUNT_ID), bidRequest, null, null);

        // then
        assertThat(result.getWarnings()).containsOnly("imp[0] validation warning");
    }

    private static BidRequest.BidRequestBuilder validBidRequestBuilder() {
        return BidRequest.builder().id("1").tmax(300L)
                .cur(singletonList("USD"))
                .imp(singletonList(validImpBuilder().build()))
                .site(Site.builder().id("1").page("2").build());
    }

    private static Imp.ImpBuilder validImpBuilder() {
        return Imp.builder().id("200")
                .video(Video.builder().mimes(singletonList("vmime"))
                        .build())
                .banner(Banner.builder()
                        .format(singletonList(Format.builder().wmin(1).wratio(5).hratio(1).build()))
                        .build())
                .pmp(Pmp.builder().deals(singletonList(Deal.builder().id("1").build())).build())
                .ext(mapper.valueToTree(singletonMap("prebid", singletonMap("bidder", singletonMap("rubicon", 0)))));
    }

}
