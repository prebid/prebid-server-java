package org.prebid.server.deals;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.ListUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.deals.events.AdminEventProcessor;
import org.prebid.server.deals.model.AdminAccounts;
import org.prebid.server.deals.model.AdminCentralResponse;
import org.prebid.server.deals.model.AdminLineItems;
import org.prebid.server.deals.model.Command;
import org.prebid.server.deals.model.LogTracer;
import org.prebid.server.deals.model.ServicesCommand;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.CriteriaManager;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.settings.proto.request.InvalidateSettingsCacheRequest;
import org.prebid.server.settings.proto.request.UpdateSettingsCacheRequest;
import org.prebid.server.util.ObjectUtils;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AdminCentralService implements AdminEventProcessor {

    private static final Logger logger = LoggerFactory.getLogger(AdminCentralService.class);

    private static final String START = "start";
    private static final String STOP = "stop";
    private static final String INVALIDATE = "invalidate";
    private static final String SAVE = "save";
    private static final String STORED_REQUEST_CACHE = "stored request cache";
    private static final String AMP_STORED_REQUEST_CACHE = "amp stored request cache";

    private final CriteriaManager criteriaManager;
    private final LineItemService lineItemService;
    private final DeliveryProgressService deliveryProgressService;
    private final SettingsCache settingsCache;
    private final SettingsCache ampSettingsCache;
    private final CachingApplicationSettings cachingApplicationSettings;
    private final JacksonMapper mapper;
    private final List<Suspendable> suspendableServices;

    public AdminCentralService(CriteriaManager criteriaManager,
                               LineItemService lineItemService,
                               DeliveryProgressService deliveryProgressService,
                               SettingsCache settingsCache,
                               SettingsCache ampSettingsCache,
                               CachingApplicationSettings cachingApplicationSettings,
                               JacksonMapper mapper,
                               List<Suspendable> suspendableServices) {
        this.criteriaManager = Objects.requireNonNull(criteriaManager);
        this.lineItemService = Objects.requireNonNull(lineItemService);
        this.deliveryProgressService = Objects.requireNonNull(deliveryProgressService);
        this.settingsCache = settingsCache;
        this.ampSettingsCache = ampSettingsCache;
        this.cachingApplicationSettings = cachingApplicationSettings;
        this.mapper = Objects.requireNonNull(mapper);
        this.suspendableServices = Objects.requireNonNull(suspendableServices);
    }

    @Override
    public void processAdminCentralEvent(AdminCentralResponse centralAdminResponse) {
        final LogTracer logTracer = centralAdminResponse.getTracer();
        if (logTracer != null) {
            handleLogTracer(centralAdminResponse.getTracer());
        }

        final Command lineItemsCommand = centralAdminResponse.getLineItems();
        if (lineItemsCommand != null) {
            handleLineItems(lineItemsCommand);
        }

        final Command storedRequestCommand = centralAdminResponse.getStoredRequest();
        if (storedRequestCommand != null && settingsCache != null) {
            handleStoredRequest(settingsCache, storedRequestCommand, STORED_REQUEST_CACHE);
        }

        final Command storedRequestAmpCommand = centralAdminResponse.getStoredRequestAmp();
        if (storedRequestAmpCommand != null && ampSettingsCache != null) {
            handleStoredRequest(ampSettingsCache, storedRequestAmpCommand, AMP_STORED_REQUEST_CACHE);
        }

        final Command accountCommand = centralAdminResponse.getAccount();
        if (accountCommand != null && cachingApplicationSettings != null) {
            handleAccountCommand(accountCommand);
        }

        final ServicesCommand servicesCommand = centralAdminResponse.getServices();
        if (servicesCommand != null) {
            handleServiceCommand(servicesCommand);
        }
    }

    private void handleAccountCommand(Command accountCommand) {
        final String cmd = accountCommand.getCmd();
        if (StringUtils.isBlank(cmd)) {
            logger.warn("Command for account action was not defined in register response");
            return;
        }

        if (!Objects.equals(cmd, INVALIDATE)) {
            logger.warn("Account commands supports only `invalidate` command, but received {0}", cmd);
            return;
        }

        final ObjectNode body = accountCommand.getBody();
        final AdminAccounts adminAccounts;
        try {
            adminAccounts = body != null
                    ? mapper.mapper().convertValue(body, AdminAccounts.class)
                    : null;
        } catch (IllegalArgumentException e) {
            logger.warn("Can't parse admin accounts body, failed with exception message : {0}", e.getMessage());
            return;
        }

        final List<String> accounts = ObjectUtils.getIfNotNull(adminAccounts, AdminAccounts::getAccounts);
        if (CollectionUtils.isNotEmpty(accounts)) {
            accounts.forEach(cachingApplicationSettings::invalidateAccountCache);
        } else {
            cachingApplicationSettings.invalidateAllAccountCache();
        }
    }

    private void handleLineItems(Command lineItemsCommand) {
        final String cmd = lineItemsCommand.getCmd();
        if (StringUtils.isBlank(cmd)) {
            logger.warn("Command for line-items action was not defined in register response.");
            return;
        }

        if (!Objects.equals(cmd, INVALIDATE)) {
            logger.warn("Line Items section supports only `invalidate` command, but received {0}", cmd);
            return;
        }

        final ObjectNode body = lineItemsCommand.getBody();
        final AdminLineItems adminLineItems;
        try {
            adminLineItems = body != null
                    ? mapper.mapper().convertValue(body, AdminLineItems.class)
                    : null;
        } catch (IllegalArgumentException e) {
            logger.warn("Can't parse admin line items body, failed with exception message : {0}", e.getMessage());
            return;
        }

        final List<String> lineItemIds = ObjectUtils.getIfNotNull(adminLineItems, AdminLineItems::getIds);

        if (CollectionUtils.isNotEmpty(lineItemIds)) {
            lineItemService.invalidateLineItemsByIds(lineItemIds);
            deliveryProgressService.invalidateLineItemsByIds(lineItemIds);
        } else {
            lineItemService.invalidateLineItems();
            deliveryProgressService.invalidateLineItems();
        }
    }

    private void handleStoredRequest(SettingsCache settingsCache, Command storedRequestCommand, String serviceName) {
        final String cmd = storedRequestCommand.getCmd();
        if (StringUtils.isBlank(cmd)) {
            logger.warn("Command for {0} was not defined.", serviceName);
            return;
        }

        final ObjectNode body = storedRequestCommand.getBody();
        if (body == null) {
            logger.warn("Command body for {0} was not defined.", serviceName);
            return;
        }

        switch (cmd) {
            case INVALIDATE:
                invalidateStoredRequests(settingsCache, serviceName, body);
                break;
            case SAVE:
                saveStoredRequests(settingsCache, serviceName, body);
                break;
            default:
                logger.warn("Command for {0} should has value 'save' or 'invalidate' but was {1}.",
                        serviceName, cmd);
        }
    }

    private void saveStoredRequests(SettingsCache settingsCache, String serviceName, ObjectNode body) {
        final UpdateSettingsCacheRequest saveRequest;
        try {
            saveRequest = mapper.mapper().convertValue(body, UpdateSettingsCacheRequest.class);
        } catch (IllegalArgumentException e) {
            logger.warn("Can't parse save settings cache request object for {0},"
                    + " failed with exception message : {1}", serviceName, e.getMessage());
            return;
        }
        final Map<String, String> storedRequests = MapUtils.emptyIfNull(saveRequest.getRequests());
        final Map<String, String> storedImps = MapUtils.emptyIfNull(saveRequest.getImps());
        settingsCache.save(storedRequests, storedImps);
        logger.info("Stored request with ids {0} and stored impressions with ids {1} were successfully saved",
                String.join(", ", storedRequests.keySet()), String.join(", ", storedImps.keySet()));
    }

    private void invalidateStoredRequests(SettingsCache settingsCache, String serviceName, ObjectNode body) {
        final InvalidateSettingsCacheRequest invalidateRequest;
        try {
            invalidateRequest = mapper.mapper().convertValue(body, InvalidateSettingsCacheRequest.class);
        } catch (IllegalArgumentException e) {
            logger.warn("Can't parse invalidate settings cache request object for {0},"
                    + " failed with exception message : {1}", serviceName, e.getMessage());
            return;
        }
        final List<String> requestIds = ListUtils.emptyIfNull(invalidateRequest.getRequests());
        final List<String> impIds = ListUtils.emptyIfNull(invalidateRequest.getImps());
        settingsCache.invalidate(requestIds, impIds);
        logger.info("Stored requests with ids {0} and impression with ids {1} were successfully invalidated",
                String.join(", ", requestIds), String.join(", ", impIds));
    }

    private void handleLogTracer(LogTracer logTracer) {
        final String command = logTracer.getCmd();
        if (StringUtils.isBlank(command)) {
            logger.warn("Command for traceLogger was not defined");
            return;
        }

        switch (command) {
            case START:
                criteriaManager.addCriteria(logTracer.getFilters(), logTracer.getDurationInSeconds());
                break;
            case STOP:
                criteriaManager.stop();
                break;
            default:
                logger.warn("Command for trace logger should has value 'start' or 'stop' but was {0}.", command);
        }
    }

    private void handleServiceCommand(ServicesCommand servicesCommand) {
        final String command = servicesCommand.getCmd();
        if (command != null && command.equalsIgnoreCase(STOP)) {
            suspendableServices.forEach(Suspendable::suspend);
        }
        logger.info("PBS services were successfully suspended");
    }
}
