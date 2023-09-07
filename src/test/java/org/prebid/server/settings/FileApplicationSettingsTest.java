package org.prebid.server.settings;

import io.vertx.core.Future;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.file.FileSystem;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.settings.model.Account;
import org.prebid.server.settings.model.AccountAnalyticsConfig;
import org.prebid.server.settings.model.AccountAuctionConfig;
import org.prebid.server.settings.model.AccountAuctionEventConfig;
import org.prebid.server.settings.model.AccountBidValidationConfig;
import org.prebid.server.settings.model.AccountCookieSyncConfig;
import org.prebid.server.settings.model.AccountCoopSyncConfig;
import org.prebid.server.settings.model.AccountEventsConfig;
import org.prebid.server.settings.model.AccountGdprConfig;
import org.prebid.server.settings.model.AccountPrivacyConfig;
import org.prebid.server.settings.model.AccountStatus;
import org.prebid.server.settings.model.BidValidationEnforcement;
import org.prebid.server.settings.model.EnabledForRequestType;
import org.prebid.server.settings.model.EnforcePurpose;
import org.prebid.server.settings.model.Purpose;
import org.prebid.server.settings.model.PurposeOneTreatmentInterpretation;
import org.prebid.server.settings.model.Purposes;
import org.prebid.server.settings.model.SpecialFeature;
import org.prebid.server.settings.model.SpecialFeatures;
import org.prebid.server.settings.model.StoredDataResult;
import org.prebid.server.settings.model.StoredResponseDataResult;

import java.util.HashSet;
import java.util.Map;

import static java.util.Arrays.asList;
import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;
import static java.util.Collections.singletonList;
import static java.util.Collections.singletonMap;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

public class FileApplicationSettingsTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    @Mock
    private FileSystem fileSystem;

    @Test
    public void creationShouldFailIfFileCouldNotBeParsed() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("invalid"));

        // when and then
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore",
                        "ignore", jacksonMapper));
    }

    @Test
    public void getAccountByIdShouldReturnEmptyWhenAccountsAreMissing() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("domains:"));

        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                        jacksonMapper);

        // when
        final Future<Account> account = applicationSettings.getAccountById("123", null);

        // then
        assertThat(account.failed()).isTrue();
    }

    @Test
    public void getAccountByIdShouldReturnPresentAccount() {
        // given
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer(
                "accounts: ["
                        + "{"
                        + "id: 123,"
                        + "status: active,"
                        + "auction: {"
                        + "price-granularity: low,"
                        + "banner-cache-ttl: 100,"
                        + "video-cache-ttl : 100,"
                        + "truncate-target-attr: 20,"
                        + "default-integration: web,"
                        + "bid-validations: {"
                        + "banner-creative-max-size: enforce"
                        + "},"
                        + "events: {"
                        + "enabled: true"
                        + "}"
                        + "},"
                        + "privacy: {"
                        + "gdpr: {"
                        + "enabled: true,"
                        + "channel-enabled: {"
                        + "amp: true,"
                        + "web: true,"
                        + "video: true,"
                        + "app: true"
                        + "},"
                        + "purposes: {"
                        + "p1: {enforce-purpose: basic,enforce-vendors: false,vendor-exceptions: [rubicon, appnexus]},"
                        + "p2: {enforce-purpose: full,enforce-vendors: true,vendor-exceptions: [openx]}"
                        + "},"
                        + "special-features: {"
                        + "sf1: {enforce: true,vendor-exceptions: [rubicon, appnexus]},"
                        + "sf2: {enforce: false,vendor-exceptions: [openx]}"
                        + "},"
                        + "purpose-one-treatment-interpretation: access-allowed"
                        + "}"
                        + "},"
                        + "analytics: {"
                        + "auction-events: {amp: true},"
                        + "modules: {some-analytics: {supported-endpoints: [auction]}}"
                        + "},"
                        + "cookie-sync: {default-limit: 5,max-limit: 8, coop-sync: {default: true}}"
                        + "}"
                        + "]"));

        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                        jacksonMapper);

        // when
        final Future<Account> account = applicationSettings.getAccountById("123", null);

        // then
        assertThat(account.succeeded()).isTrue();
        final AccountAuctionEventConfig expectedEventsConfig = AccountAuctionEventConfig.builder().build();
        expectedEventsConfig.addEvent("amp", true);
        assertThat(account.result()).isEqualTo(Account.builder()
                .id("123")
                .status(AccountStatus.active)
                .auction(AccountAuctionConfig.builder()
                        .priceGranularity("low")
                        .bannerCacheTtl(100)
                        .videoCacheTtl(100)
                        .truncateTargetAttr(20)
                        .defaultIntegration("web")
                        .bidValidations(AccountBidValidationConfig.of(BidValidationEnforcement.enforce))
                        .events(AccountEventsConfig.of(true))
                        .build())
                .privacy(AccountPrivacyConfig.of(
                        AccountGdprConfig.builder()
                                .enabled(true)
                                .enabledForRequestType(EnabledForRequestType.of(true, true, true, true))
                                .purposes(Purposes.builder()
                                        .p1(Purpose.of(EnforcePurpose.basic, false, asList("rubicon", "appnexus")))
                                        .p2(Purpose.of(EnforcePurpose.full, true, singletonList("openx")))
                                        .build())
                                .specialFeatures(SpecialFeatures.builder()
                                        .sf1(SpecialFeature.of(true, asList("rubicon", "appnexus")))
                                        .sf2(SpecialFeature.of(false, singletonList("openx")))
                                        .build())
                                .purposeOneTreatmentInterpretation(PurposeOneTreatmentInterpretation.accessAllowed)
                                .build(),
                        null,
                        null,
                        null))
                .analytics(AccountAnalyticsConfig.of(
                        expectedEventsConfig,
                        singletonMap(
                                "some-analytics",
                                mapper.createObjectNode()
                                        .set("supported-endpoints", mapper.createArrayNode().add("auction")))))
                .cookieSync(AccountCookieSyncConfig.of(5, 8, null, AccountCoopSyncConfig.of(true)))
                .build());
    }

    @Test
    public void getAccountByIdShouldReturnEmptyForUnknownAccount() {
        // given
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts: [ {id: '123'}, {id: '456'} ]"));

        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore",
                        "ignore", jacksonMapper);

        // when
        final Future<Account> account = applicationSettings.getAccountById("789", null);

        // then
        assertThat(account.failed()).isTrue();
    }

    @Test
    public void initializeCategoriesShouldThrowExceptionWhenFileCantBeParsed() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"))
                .willReturn(singletonList("/home/user/categories/iab_1.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")) // stored imp
                .willReturn(Buffer.buffer("value2")) // stored response
                .willReturn(Buffer.buffer("{\"iab-1\": 1}")); // categories

        // when and then
        assertThatThrownBy(() -> new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore",
                "ignore", jacksonMapper))
                .isInstanceOf(PreBidException.class)
                .hasMessage("Failed to decode categories for file /home/user/categories/iab_1.json");
    }

    @Test
    public void getCategoriesShouldReturnResultFoundByAdServerAndPublisherSuccessfully() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"))
                .willReturn(singletonList("/home/user/categories/iab_1.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")) // stored imp
                .willReturn(Buffer.buffer("value2")) // stored response
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories

        final ApplicationSettings applicationSettings = new FileApplicationSettings(fileSystem, "ignore", "ignore",
                "ignore", "ignore", "ignore", jacksonMapper);

        // when
        final Future<Map<String, String>> result = applicationSettings.getCategories("iab", "1", null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(singletonMap("iab-1", "id"));
    }

    @Test
    public void getCategoriesShouldReturnResultFoundByAdServerAndWithoutPublisherSuccessfully() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"))
                .willReturn(singletonList("/home/user/categories/iab.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")) // stored imp
                .willReturn(Buffer.buffer("value2")) // stored response
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories

        final ApplicationSettings applicationSettings = new FileApplicationSettings(fileSystem, "ignore", "ignore",
                "ignore", "ignore", "ignore", jacksonMapper);

        // when
        final Future<Map<String, String>> result = applicationSettings.getCategories("iab", null, null);

        // then
        assertThat(result.succeeded()).isTrue();
        assertThat(result.result()).isEqualTo(singletonMap("iab-1", "id"));
    }

    @Test
    public void getCategoriesShouldReturnFailedFutureWhenFileWasNotFound() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"))
                .willReturn(singletonList("/home/user/categories/iab_1.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")) // stored imp
                .willReturn(Buffer.buffer("value2")) // stored response
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories

        final ApplicationSettings applicationSettings = new FileApplicationSettings(fileSystem, "ignore", "ignore",
                "ignore", "ignore", "ignore", jacksonMapper);

        // when
        final Future<Map<String, String>> result = applicationSettings.getCategories("iab", "2", null);

        // then
        assertThat(result.failed()).isTrue();
        assertThat(result.cause()).isInstanceOf(PreBidException.class)
                .hasMessage("Categories for filename iab_2 were not found");
    }

    @Test
    public void getStoredDataShouldReturnResultWithNotFoundErrorForNonExistingRequestId() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"))
                .willReturn(singletonList("/home/user/categories/iab_1.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")) // stored imp
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories

        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                        jacksonMapper);

        // when
        final Future<StoredDataResult> storedRequestResult =
                applicationSettings.getStoredData(null, singleton("2"), emptySet(), null);

        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/requests/1.json"));
        assertThat(storedRequestResult.succeeded()).isTrue();
        assertThat(storedRequestResult.result().getErrors()).isNotNull().hasSize(1)
                .isEqualTo(singletonList("No stored request found for id: 2"));
    }

    @Test
    public void getStoredDataShouldReturnResultWithNotFoundErrorForNonExistingImpId() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/1.json"))
                .willReturn(singletonList("/home/user/responses/3.json"));

        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")) // stored imp
                .willReturn(Buffer.buffer("value3")) // stored response
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories

        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                        jacksonMapper);

        // when
        final Future<StoredDataResult> storedRequestResult =
                applicationSettings.getStoredData(null, emptySet(), singleton("2"), null);

        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/imps/1.json"));
        assertThat(storedRequestResult.succeeded()).isTrue();
        assertThat(storedRequestResult.result().getErrors()).isNotNull().hasSize(1)
                .isEqualTo(singletonList("No stored imp found for id: 2"));
    }

    @Test
    public void getStoredDataShouldReturnResultWithNoErrorsIfAllIdsArePresent() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"))
                .willReturn(singletonList("/home/user/responses/3.json"));

        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")) // stored imp
                .willReturn(Buffer.buffer("value3")) // stored response
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories

        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                        jacksonMapper);

        // when
        final Future<StoredDataResult> storedRequestResult =
                applicationSettings.getStoredData(null, singleton("1"), singleton("2"), null);

        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/requests/1.json"));
        verify(fileSystem).readFileBlocking(eq("/home/user/imps/2.json"));
        assertThat(storedRequestResult.result().getErrors()).isNotNull().isEmpty();
        assertThat(storedRequestResult.result().getStoredIdToRequest()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("1", "value1"));
        assertThat(storedRequestResult.result().getStoredIdToImp()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("2", "value2"));
    }

    @Test
    public void getAmpStoredDataShouldIgnoreImpIdsArgument() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/1.json"))
                .willReturn(singletonList("/home/user/imps/2.json"));
        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value1")) // stored request
                .willReturn(Buffer.buffer("value2")) // stored imp
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories
        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                        jacksonMapper);

        // when
        final Future<StoredDataResult> storedRequestResult =
                applicationSettings.getAmpStoredData(null, emptySet(), singleton("2"), null);

        // then
        assertThat(storedRequestResult.result().getErrors()).isNotNull().isEmpty();
        assertThat(storedRequestResult.result().getStoredIdToImp()).isEmpty();
    }

    @Test
    public void getStoredResponsesShouldReturnEmptyResultAndErrorsWhenResponseIdsAreEmpty() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/3.json"))
                .willReturn(singletonList("/home/user/imps/2.json"))
                .willReturn(singletonList("/home/user/responses/1.json"))
                .willReturn(singletonList("/home/user/categories"));

        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value3")) // requests
                .willReturn(Buffer.buffer("value2")) // imps
                .willReturn(Buffer.buffer("value1")) // responses
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories
        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                        jacksonMapper);

        // when
        final Future<StoredResponseDataResult> storedResponsesResult =
                applicationSettings.getStoredResponses(emptySet(), null);

        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/responses/1.json"));
        assertThat(storedResponsesResult.succeeded()).isTrue();
        assertThat(storedResponsesResult.result().getErrors()).isNotNull().isEmpty();
        assertThat(storedResponsesResult.result().getIdToStoredResponses()).isNotNull().isEmpty();
    }

    @Test
    public void getStoredResponsesShouldReturnResultWithMissingIdsIfNotAllIdsArePresent() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/3.json"))
                .willReturn(singletonList("/home/user/imps/2.json"))
                .willReturn(singletonList("/home/user/responses/1.json"))
                .willReturn(singletonList("/home/user/categories/iab_1.json"));

        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value3")) // requests
                .willReturn(Buffer.buffer("value2")) // imps
                .willReturn(Buffer.buffer("value1")) // responses
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories

        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                        jacksonMapper);

        // when
        final Future<StoredResponseDataResult> storedResponsesResult =
                applicationSettings.getStoredResponses(new HashSet<>(asList("1", "2")), null);

        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/responses/1.json"));
        assertThat(storedResponsesResult.succeeded()).isTrue();
        assertThat(storedResponsesResult.result().getErrors()).isNotNull().hasSize(1)
                .isEqualTo(singletonList("No stored seatbid found for id: 2"));
        assertThat(storedResponsesResult.result().getIdToStoredResponses()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("1", "value1"));
    }

    @Test
    public void getStoredResponsesShouldReturnResultWithoutErrorsIfAllIdsArePresent() {
        // given
        given(fileSystem.readDirBlocking(anyString()))
                .willReturn(singletonList("/home/user/requests/3.json"))
                .willReturn(singletonList("/home/user/imps/2.json"))
                .willReturn(singletonList("/home/user/responses/1.json"))
                .willReturn(singletonList("/home/user/categories/iab_1.json"));

        given(fileSystem.readFileBlocking(anyString()))
                .willReturn(Buffer.buffer("accounts:")) // settings file
                .willReturn(Buffer.buffer("value3")) // requests
                .willReturn(Buffer.buffer("value2")) // imps
                .willReturn(Buffer.buffer("value1")) // responses
                .willReturn(Buffer.buffer("{\"iab-1\": {\"id\": \"id\"}}")); // categories

        final FileApplicationSettings applicationSettings =
                new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                        jacksonMapper);

        // when
        final Future<StoredResponseDataResult> storedResponsesResult =
                applicationSettings.getStoredResponses(singleton("1"), null);

        // then
        verify(fileSystem).readFileBlocking(eq("/home/user/responses/1.json"));
        assertThat(storedResponsesResult.succeeded()).isTrue();
        assertThat(storedResponsesResult.result().getErrors()).isNotNull().isEmpty();
        assertThat(storedResponsesResult.result().getIdToStoredResponses()).isNotNull().hasSize(1)
                .isEqualTo(singletonMap("1", "value1"));
    }

    @Test
    public void storedDataInitializationShouldNotReadFromNonJsonFiles() {
        // given
        given(fileSystem.readDirBlocking(anyString())).willReturn(singletonList("/home/user/requests/1.txt"));
        given(fileSystem.readFileBlocking(anyString())).willReturn(Buffer.buffer("accounts:")); // settings file

        // when
        new FileApplicationSettings(fileSystem, "ignore", "ignore", "ignore", "ignore", "ignore",
                jacksonMapper);

        // then
        verify(fileSystem, never()).readFileBlocking(eq("1.txt"));
    }
}
