package org.prebid.server.deals;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.prebid.server.VertxTest;
import org.prebid.server.deals.model.AdminAccounts;
import org.prebid.server.deals.model.AdminCentralResponse;
import org.prebid.server.deals.model.AdminLineItems;
import org.prebid.server.deals.model.Command;
import org.prebid.server.deals.model.LogCriteriaFilter;
import org.prebid.server.deals.model.LogTracer;
import org.prebid.server.deals.model.ServicesCommand;
import org.prebid.server.log.CriteriaManager;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.settings.proto.request.InvalidateSettingsCacheRequest;
import org.prebid.server.settings.proto.request.UpdateSettingsCacheRequest;

import java.util.Collections;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyNoInteractions;

public class AdminCentralServiceTest extends VertxTest {

    @Rule
    public final MockitoRule mockitoRule = MockitoJUnit.rule();

    private AdminCentralService adminCentralService;

    @Mock
    private LineItemService lineItemService;

    @Mock
    private DeliveryProgressService deliveryProgressService;

    @Mock
    private CriteriaManager criteriaManager;

    @Mock
    private SettingsCache settingsCache;

    @Mock
    private SettingsCache ampSettingsCache;

    @Mock
    private CachingApplicationSettings cachingApplicationSettings;

    @Mock
    private Suspendable suspendable;

    @Before
    public void setUp() {
        adminCentralService = new AdminCentralService(criteriaManager, lineItemService, deliveryProgressService,
                settingsCache, ampSettingsCache, cachingApplicationSettings,
                jacksonMapper, singletonList(suspendable));
    }

    @Test
    public void processAdminCentralEventShouldAddCriteriaWhenTraceLogAndCriteriaFilterArePresentAndCmdIsStart() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(
                LogTracer.of("start", false, 800L, LogCriteriaFilter.of(null, null, null)), null, null, null, null,
                null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(criteriaManager).addCriteria(any(), anyLong());
    }

    @Test
    public void processAdminCentralEventShouldAddCriteriaWhenTraceLogAndCriteriaFilterArePresentAndCmdIsStop() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(LogTracer.of("stop", false, 0L, null),
                null, null, null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(criteriaManager).stop();
    }

    @Test
    public void processAdminCentralEventShouldStopServicesWhenServicesStopCommandIsPresent() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null, null, null, null, null,
                ServicesCommand.of("stop"));

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(suspendable).suspend();
    }

    @Test
    public void processAdminCentralEventShouldNotStopServicesWhenServicesCommandIsNotStop() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null, null, null, null, null,
                ServicesCommand.of("invalid"));

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(suspendable);
    }

    @Test
    public void processAdminCentralEventShouldAddCriteriaAndStopServices() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(
                LogTracer.of("start", false, 800L, LogCriteriaFilter.of(null, null, null)), null, null, null, null,
                ServicesCommand.of("stop"));

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(suspendable).suspend();
        verify(criteriaManager).addCriteria(any(), anyLong());
    }

    @Test
    public void processAdminCentralEventShouldNotCallCriteriaManagerWhenCommandIsNull() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(
                LogTracer.of(null, false, 800L, LogCriteriaFilter.of(null, null, null)), null, null, null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(criteriaManager);
    }

    @Test
    public void processAdminCentralEventShouldNotCallCriteriaManagerWhenItIsNotStartOrStop() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(
                LogTracer.of("invalid", false, 800L, LogCriteriaFilter.of(null, null, null)), null, null,
                null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(criteriaManager);
    }

    @Test
    public void processAdminCentralEventShouldNotCallSettingsCacheWhenCommandWasNotDefined() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null, Command.of(null, null),
                Command.of(null, null), null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(settingsCache);
        verifyNoInteractions(ampSettingsCache);
    }

    @Test
    public void processAdminCentralEventShouldNotCallSettingsCacheWhenBodyWasNotDefined() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null, Command.of("save", null),
                Command.of("save", null), null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(settingsCache);
        verifyNoInteractions(ampSettingsCache);
    }

    @Test
    public void processAdminCentralEventShouldCallSettings() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null, Command.of("save", null),
                Command.of("save", null), null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(settingsCache);
        verifyNoInteractions(ampSettingsCache);
    }

    @Test
    public void processAdminCentralEventShouldCallSaveAmpSettingsCache() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null, null,
                Command.of("save", jacksonMapper.mapper().valueToTree(UpdateSettingsCacheRequest
                        .of(Collections.emptyMap(), Collections.emptyMap()))), null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(ampSettingsCache).save(any(), any());
    }

    @Test
    public void processAdminCentralEventShouldCallInvalidateAmpSettingsCache() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null, null,
                Command.of("invalidate", jacksonMapper.mapper().valueToTree(InvalidateSettingsCacheRequest
                        .of(Collections.emptyList(), Collections.emptyList()))), null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(ampSettingsCache).invalidate(any(), any());
    }

    @Test
    public void processAdminCentralEventShouldNotCallAmpSettingsCacheWhenCantParseBody() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null, null,
                Command.of("save", jacksonMapper.mapper().createObjectNode().put("requests", 1)), null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(ampSettingsCache);
    }

    @Test
    public void processAdminCentralEventShouldCallSaveSettingsCache() throws JsonProcessingException {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                Command.of("save", jacksonMapper.mapper().valueToTree(UpdateSettingsCacheRequest
                        .of(Collections.singletonMap("requestId",
                                jacksonMapper.mapper().writeValueAsString(BidRequest.builder().id("requestId")
                                        .build())),
                                Collections.singletonMap("impId",
                                        jacksonMapper.mapper().writeValueAsString(Imp.builder().id("impId")
                                                .build()))))),
                null, null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(settingsCache).save(any(), any());
    }

    @Test
    public void processAdminCentralEventShouldCallInvalidateSettingsCache() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                Command.of("invalidate", jacksonMapper.mapper().valueToTree(InvalidateSettingsCacheRequest
                        .of(Collections.emptyList(), Collections.emptyList()))), null, null, null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(settingsCache).invalidate(any(), any());
    }

    @Test
    public void processAdminCentralEventShouldNotCallSettingsCacheWhenCantParseBody() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                Command.of("save", jacksonMapper.mapper().createObjectNode().put("requests", 1)), null, null, null,
                null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(settingsCache);
    }

    @Test
    public void processAdminCentralEventShouldCallInvalidateLineItemsById() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, Command.of("invalidate", jacksonMapper.mapper()
                        .valueToTree(AdminLineItems.of(singletonList("lineItemId")))), null, null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(lineItemService).invalidateLineItemsByIds(eq(singletonList("lineItemId")));
        verify(deliveryProgressService).invalidateLineItemsByIds(eq(singletonList("lineItemId")));
    }

    @Test
    public void processAdminCentralEventShouldCallInvalidateAllLineItems() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, Command.of("invalidate", null), null, null);
        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(lineItemService).invalidateLineItems();
        verify(deliveryProgressService).invalidateLineItems();
    }

    @Test
    public void processAdminCentralEventShouldNotCallInvalidateWhenCmdNotDefined() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, Command.of(null, null), null, null);
        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoMoreInteractions(lineItemService);
        verifyNoMoreInteractions(deliveryProgressService);
    }

    @Test
    public void processAdminCentralEventShouldNotCallInvalidateWhenCmdHasValueOtherToInvalidate() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, Command.of("save", null), null, null);
        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoMoreInteractions(lineItemService);
        verifyNoMoreInteractions(deliveryProgressService);
    }

    @Test
    public void processAdminCentralEventShouldNotCallInvalidateWhenCantParseBody() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, Command.of("invalidate", mapper.createObjectNode()
                        .set("ids", mapper.valueToTree(AdminLineItems.of(singletonList("5"))))), null,
                null);
        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoMoreInteractions(lineItemService);
        verifyNoMoreInteractions(deliveryProgressService);
    }

    @Test
    public void processAdminCentralEventShouldNotCallInvalidateAccountsWhenCommandIsNotDefined() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, null, Command.of(null, mapper.createObjectNode()
                        .set("ids", mapper.valueToTree(AdminAccounts.of(singletonList("1001"))))), null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(cachingApplicationSettings);
    }

    @Test
    public void processAdminCentralEventShouldNotCallInvalidateAccountsWhenInvalidCommandValue() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, null, Command.of("invalid", mapper.valueToTree(AdminAccounts.of(singletonList("1001")))),
                null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(cachingApplicationSettings);
    }

    @Test
    public void processAdminCentralEventShouldNotCallInvalidateAccountsCantParseBody() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, null, Command.of("invalidate", mapper.createObjectNode()
                        .set("accounts", mapper.valueToTree(AdminAccounts.of(singletonList("5"))))), null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verifyNoInteractions(cachingApplicationSettings);
    }

    @Test
    public void processAdminCentralEventShouldInvalidateAccounts() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, null, Command.of("invalidate",
                        mapper.valueToTree(AdminAccounts.of(asList("1001", "1002")))), null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(cachingApplicationSettings).invalidateAccountCache(eq("1001"));
        verify(cachingApplicationSettings).invalidateAccountCache(eq("1002"));
    }

    @Test
    public void processAdminCentralEventShouldInvalidateAllAccounts() {
        // given
        final AdminCentralResponse adminCentralResponse = AdminCentralResponse.of(null,
                null, null, null, Command.of("invalidate", mapper.createObjectNode()
                        .set("ids", mapper.valueToTree(AdminAccounts.of(null)))), null);

        // when
        adminCentralService.processAdminCentralEvent(adminCentralResponse);

        // then
        verify(cachingApplicationSettings).invalidateAllAccountCache();
    }
}
