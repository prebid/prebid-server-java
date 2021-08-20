package org.prebid.server.spring.config;

import io.vertx.core.Vertx;
import io.vertx.core.eventbus.EventBus;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.bidder.BidderErrorNotifier;
import org.prebid.server.bidder.BidderRequestCompletionTrackerFactory;
import org.prebid.server.bidder.DealsBidderRequestCompletionTrackerFactory;
import org.prebid.server.bidder.HttpBidderRequestEnricher;
import org.prebid.server.bidder.HttpBidderRequester;
import org.prebid.server.currency.CurrencyConversionService;
import org.prebid.server.deals.AdminCentralService;
import org.prebid.server.deals.AlertHttpService;
import org.prebid.server.deals.DealsProcessor;
import org.prebid.server.deals.DeliveryProgressReportFactory;
import org.prebid.server.deals.DeliveryProgressService;
import org.prebid.server.deals.DeliveryStatsService;
import org.prebid.server.deals.LineItemService;
import org.prebid.server.deals.PlannerService;
import org.prebid.server.deals.RegisterService;
import org.prebid.server.deals.Suspendable;
import org.prebid.server.deals.TargetingService;
import org.prebid.server.deals.UserService;
import org.prebid.server.deals.deviceinfo.DeviceInfoService;
import org.prebid.server.deals.events.AdminEventProcessor;
import org.prebid.server.deals.events.AdminEventService;
import org.prebid.server.deals.events.ApplicationEventProcessor;
import org.prebid.server.deals.events.ApplicationEventService;
import org.prebid.server.deals.events.EventServiceInitializer;
import org.prebid.server.deals.simulation.DealsSimulationAdminHandler;
import org.prebid.server.deals.simulation.SimulationAwareDeliveryProgressService;
import org.prebid.server.deals.simulation.SimulationAwareDeliveryStatsService;
import org.prebid.server.deals.simulation.SimulationAwareHttpBidderRequester;
import org.prebid.server.deals.simulation.SimulationAwareLineItemService;
import org.prebid.server.deals.simulation.SimulationAwarePlannerService;
import org.prebid.server.deals.simulation.SimulationAwareRegisterService;
import org.prebid.server.deals.simulation.SimulationAwareUserService;
import org.prebid.server.geolocation.GeoLocationService;
import org.prebid.server.health.HealthMonitor;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.log.CriteriaLogManager;
import org.prebid.server.log.CriteriaManager;
import org.prebid.server.metric.Metrics;
import org.prebid.server.settings.CachingApplicationSettings;
import org.prebid.server.settings.SettingsCache;
import org.prebid.server.util.ObjectUtils;
import org.prebid.server.vertx.ContextRunner;
import org.prebid.server.vertx.http.HttpClient;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.validation.annotation.Validated;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Configuration
@ConditionalOnProperty(prefix = "deals", name = "enabled", havingValue = "true")
public class DealsConfiguration {

    @Configuration
    @ConditionalOnExpression("${deals.enabled} == true and ${deals.simulation.enabled} == false")
    public static class ProductionConfiguration {

        @Bean
        PlannerService plannerService(
                PlannerProperties plannerProperties,
                DeploymentProperties deploymentProperties,
                DeliveryProgressService deliveryProgressService,
                LineItemService lineItemService,
                AlertHttpService alertHttpService,
                HttpClient httpClient,
                Metrics metrics,
                Clock clock,
                JacksonMapper mapper) {

            return new PlannerService(
                    plannerProperties.toComponentProperties(),
                    deploymentProperties.toComponentProperties(),
                    lineItemService,
                    deliveryProgressService,
                    alertHttpService,
                    httpClient,
                    metrics,
                    clock,
                    mapper);
        }

        @Bean
        RegisterService registerService(
                PlannerProperties plannerProperties,
                DeploymentProperties deploymentProperties,
                AdminEventService adminEventService,
                DeliveryProgressService deliveryProgressService,
                AlertHttpService alertHttpService,
                HealthMonitor healthMonitor,
                CurrencyConversionService currencyConversionService,
                HttpClient httpClient,
                Vertx vertx,
                JacksonMapper mapper) {

            return new RegisterService(
                    plannerProperties.toComponentProperties(),
                    deploymentProperties.toComponentProperties(),
                    adminEventService,
                    deliveryProgressService,
                    alertHttpService,
                    healthMonitor,
                    currencyConversionService,
                    httpClient,
                    vertx,
                    mapper);
        }

        @Bean
        DeliveryStatsService deliveryStatsService(
                DeliveryStatsProperties deliveryStatsProperties,
                DeliveryProgressReportFactory deliveryProgressReportFactory,
                AlertHttpService alertHttpService,
                HttpClient httpClient,
                Metrics metrics,
                Clock clock,
                Vertx vertx,
                JacksonMapper mapper) {

            return new DeliveryStatsService(
                    deliveryStatsProperties.toComponentProperties(),
                    deliveryProgressReportFactory,
                    alertHttpService,
                    httpClient,
                    metrics,
                    clock,
                    vertx,
                    mapper);
        }

        @Bean
        LineItemService lineItemService(
                @Value("${deals.max-deals-per-bidder}") int maxDealsPerBidder,
                TargetingService targetingService,
                BidderCatalog bidderCatalog,
                CurrencyConversionService conversionService,
                ApplicationEventService applicationEventService,
                @Value("${auction.ad-server-currency}") String adServerCurrency,
                Clock clock,
                CriteriaLogManager criteriaLogManager) {

            return new LineItemService(maxDealsPerBidder,
                    targetingService,
                    bidderCatalog,
                    conversionService,
                    applicationEventService,
                    adServerCurrency,
                    clock,
                    criteriaLogManager);
        }

        @Bean
        DeliveryProgressService deliveryProgressService(
                DeliveryProgressProperties deliveryProgressProperties,
                LineItemService lineItemService,
                DeliveryStatsService deliveryStatsService,
                DeliveryProgressReportFactory deliveryProgressReportFactory,
                Clock clock,
                CriteriaLogManager criteriaLogManager) {

            return new DeliveryProgressService(
                    deliveryProgressProperties.toComponentProperties(),
                    lineItemService,
                    deliveryStatsService,
                    deliveryProgressReportFactory,
                    clock,
                    criteriaLogManager);
        }

        @Bean
        UserService userService(
                UserDetailsProperties userDetailsProperties,
                @Value("${datacenter-region}") String dataCenterRegion,
                LineItemService lineItemService,
                HttpClient httpClient,
                Clock clock,
                Metrics metrics,
                JacksonMapper mapper) {

            return new UserService(
                    userDetailsProperties.toComponentProperties(),
                    dataCenterRegion,
                    lineItemService,
                    httpClient,
                    clock,
                    metrics,
                    mapper);
        }
    }

    @Configuration
    @ConditionalOnExpression("${deals.enabled} == true and ${deals.simulation.enabled} == false")
    @EnableScheduling
    public static class SchedulerConfiguration {

        @Bean
        GeneralPlannerScheduler generalPlannerScheduler(PlannerService plannerService,
                                                        ContextRunner contextRunner) {
            return new GeneralPlannerScheduler(plannerService, contextRunner);
        }

        @Bean
        @ConditionalOnExpression(
                "'${deals.delivery-stats.delivery-period}'"
                        + ".equals('${deals.delivery-progress.report-reset-period}')")
        ImmediateDeliveryScheduler immediateDeliveryScheduler(DeliveryProgressService deliveryProgressService,
                                                              DeliveryStatsService deliveryStatsService,
                                                              Clock clock,
                                                              ContextRunner contextRunner) {
            return new ImmediateDeliveryScheduler(deliveryProgressService, deliveryStatsService, clock,
                    contextRunner);
        }

        @Bean
        @ConditionalOnExpression(
                "not '${deals.delivery-stats.delivery-period}'"
                        + ".equals('${deals.delivery-progress.report-reset-period}')")
        DeliveryScheduler deliveryScheduler(DeliveryProgressService deliveryProgressService,
                                            DeliveryStatsService deliveryStatsService,
                                            Clock clock,
                                            ContextRunner contextRunner) {
            return new DeliveryScheduler(deliveryProgressService, deliveryStatsService, clock,
                    contextRunner);
        }

        @Bean
        AdvancePlansScheduler advancePlansScheduler(LineItemService lineItemService,
                                                    ContextRunner contextRunner,
                                                    Clock clock) {
            return new AdvancePlansScheduler(lineItemService, contextRunner, clock);
        }

        private static class GeneralPlannerScheduler {

            private final PlannerService plannerService;
            private final ContextRunner contextRunner;

            GeneralPlannerScheduler(PlannerService plannerService, ContextRunner contextRunner) {
                this.plannerService = plannerService;
                this.contextRunner = contextRunner;
            }

            @Scheduled(cron = "${deals.planner.update-period}")
            public void fetchPlansFromGeneralPlanner() {
                contextRunner.runOnServiceContext(future -> {
                    plannerService.updateLineItemMetaData();
                    future.complete();
                });
            }
        }

        private static class AdvancePlansScheduler {
            private final LineItemService lineItemService;
            private final ContextRunner contextRunner;
            private final Clock clock;

            AdvancePlansScheduler(LineItemService lineItemService, ContextRunner contextRunner, Clock clock) {
                this.lineItemService = lineItemService;
                this.contextRunner = contextRunner;
                this.clock = clock;
            }

            @Scheduled(cron = "${deals.planner.plan-advance-period}")
            public void advancePlans() {
                contextRunner.runOnServiceContext(future -> {
                    lineItemService.advanceToNextPlan(ZonedDateTime.now(clock));
                    future.complete();
                });
            }
        }

        private static class ImmediateDeliveryScheduler {

            private final DeliveryProgressService deliveryProgressService;
            private final DeliveryStatsService deliveryStatsService;
            private final Clock clock;
            private final ContextRunner contextRunner;

            ImmediateDeliveryScheduler(DeliveryProgressService deliveryProgressService,
                                       DeliveryStatsService deliveryStatsService,
                                       Clock clock,
                                       ContextRunner contextRunner) {
                this.deliveryProgressService = deliveryProgressService;
                this.deliveryStatsService = deliveryStatsService;
                this.clock = clock;
                this.contextRunner = contextRunner;
            }

            @Scheduled(cron = "${deals.delivery-stats.delivery-period}")
            public void createAndSendDeliveryReport() {
                contextRunner.runOnServiceContext(future -> {
                    final ZonedDateTime now = ZonedDateTime.now(clock);
                    deliveryProgressService.createDeliveryProgressReports(now);
                    deliveryStatsService.sendDeliveryProgressReports(now);
                    future.complete();
                });
            }
        }
    }

    private static class DeliveryScheduler {

        private final DeliveryProgressService deliveryProgressService;
        private final DeliveryStatsService deliveryStatsService;
        private final Clock clock;
        private final ContextRunner contextRunner;

        DeliveryScheduler(DeliveryProgressService deliveryProgressService,
                          DeliveryStatsService deliveryStatsService,
                          Clock clock,
                          ContextRunner contextRunner) {
            this.deliveryProgressService = deliveryProgressService;
            this.deliveryStatsService = deliveryStatsService;
            this.clock = clock;
            this.contextRunner = contextRunner;
        }

        @Scheduled(cron = "${deals.delivery-progress.report-reset-period}")
        public void createDeliveryReport() {
            contextRunner.runOnServiceContext(future -> {
                deliveryProgressService.createDeliveryProgressReports(ZonedDateTime.now(clock));
                future.complete();
            });
        }

        @Scheduled(cron = "${deals.delivery-stats.delivery-period}")
        public void sendDeliveryReport() {
            contextRunner.runOnServiceContext(future -> {
                deliveryStatsService.sendDeliveryProgressReports(ZonedDateTime.now(clock));
                future.complete();
            });
        }
    }

    @Configuration
    @ConditionalOnExpression("${deals.enabled} == true and ${deals.simulation.enabled} == true")
    public static class SimulationConfiguration {

        @Bean
        @ConditionalOnProperty(prefix = "deals", name = "call-real-bidders-in-simulation", havingValue = "false",
                matchIfMissing = true)
        SimulationAwareHttpBidderRequester simulationAwareHttpBidderRequester(
                HttpClient httpClient,
                BidderRequestCompletionTrackerFactory completionTrackerFactory,
                BidderErrorNotifier bidderErrorNotifier,
                HttpBidderRequestEnricher requestEnricher,
                LineItemService lineItemService,
                JacksonMapper mapper) {

            return new SimulationAwareHttpBidderRequester(
                    httpClient, completionTrackerFactory, bidderErrorNotifier, requestEnricher, lineItemService,
                    mapper);
        }

        @Bean
        SimulationAwarePlannerService plannerService(
                PlannerProperties plannerProperties,
                DeploymentProperties deploymentProperties,
                DeliveryProgressService deliveryProgressService,
                SimulationAwareLineItemService lineItemService,
                AlertHttpService alertHttpService,
                HttpClient httpClient,
                Metrics metrics,
                Clock clock,
                JacksonMapper mapper) {

            return new SimulationAwarePlannerService(
                    plannerProperties.toComponentProperties(),
                    deploymentProperties.toComponentProperties(),
                    lineItemService,
                    deliveryProgressService,
                    alertHttpService,
                    httpClient,
                    metrics,
                    clock,
                    mapper);
        }

        @Bean
        SimulationAwareRegisterService registerService(
                PlannerProperties plannerProperties,
                DeploymentProperties deploymentProperties,
                AdminEventService adminEventService,
                DeliveryProgressService deliveryProgressService,
                AlertHttpService alertHttpService,
                HealthMonitor healthMonitor,
                CurrencyConversionService currencyConversionService,
                HttpClient httpClient,
                Vertx vertx,
                JacksonMapper mapper) {

            return new SimulationAwareRegisterService(
                    plannerProperties.toComponentProperties(),
                    deploymentProperties.toComponentProperties(),
                    adminEventService,
                    deliveryProgressService,
                    alertHttpService,
                    healthMonitor,
                    currencyConversionService,
                    httpClient,
                    vertx,
                    mapper);
        }

        @Bean
        SimulationAwareDeliveryStatsService deliveryStatsService(
                DeliveryStatsProperties deliveryStatsProperties,
                DeliveryProgressReportFactory deliveryProgressReportFactory,
                AlertHttpService alertHttpService,
                HttpClient httpClient,
                Metrics metrics,
                Clock clock,
                Vertx vertx,
                JacksonMapper mapper) {

            return new SimulationAwareDeliveryStatsService(
                    deliveryStatsProperties.toComponentProperties(),
                    deliveryProgressReportFactory,
                    alertHttpService,
                    httpClient,
                    metrics,
                    clock,
                    vertx,
                    mapper);
        }

        @Bean
        SimulationAwareLineItemService lineItemService(
                @Value("${deals.max-deals-per-bidder}") int maxDealsPerBidder,
                TargetingService targetingService,
                BidderCatalog bidderCatalog,
                CurrencyConversionService conversionService,
                ApplicationEventService applicationEventService,
                @Value("${auction.ad-server-currency}") String adServerCurrency,
                Clock clock,
                CriteriaLogManager criteriaLogManager) {

            return new SimulationAwareLineItemService(
                    maxDealsPerBidder,
                    targetingService,
                    bidderCatalog,
                    conversionService,
                    applicationEventService,
                    adServerCurrency,
                    clock,
                    criteriaLogManager);
        }

        @Bean
        SimulationAwareDeliveryProgressService deliveryProgressService(
                DeliveryProgressProperties deliveryProgressProperties,
                LineItemService lineItemService,
                DeliveryStatsService deliveryStatsService,
                DeliveryProgressReportFactory deliveryProgressReportFactory,
                @Value("${deals.simulation.ready-at-adjustment-ms}") long readyAtAdjustment,
                Clock clock,
                CriteriaLogManager criteriaLogManager) {

            return new SimulationAwareDeliveryProgressService(
                    deliveryProgressProperties.toComponentProperties(),
                    lineItemService,
                    deliveryStatsService,
                    deliveryProgressReportFactory,
                    readyAtAdjustment,
                    clock,
                    criteriaLogManager);
        }

        @Bean
        SimulationAwareUserService userService(
                UserDetailsProperties userDetailsProperties,
                SimulationProperties simulationProperties,
                @Value("${datacenter-region}") String dataCenterRegion,
                LineItemService lineItemService,
                HttpClient httpClient,
                Clock clock,
                Metrics metrics,
                JacksonMapper mapper) {

            return new SimulationAwareUserService(
                    userDetailsProperties.toComponentProperties(),
                    simulationProperties.toComponentProperties(),
                    dataCenterRegion,
                    lineItemService,
                    httpClient,
                    clock,
                    metrics,
                    mapper);
        }

        @Bean
        DealsSimulationAdminHandler dealsSimulationAdminHandler(
                SimulationAwareRegisterService registerService,
                SimulationAwarePlannerService plannerService,
                SimulationAwareDeliveryProgressService deliveryProgressService,
                SimulationAwareDeliveryStatsService deliveryStatsService,
                @Autowired(required = false) SimulationAwareHttpBidderRequester httpBidderRequester,
                JacksonMapper mapper,
                @Value("${admin-endpoints.e2eadmin.path}") String path) {

            return new DealsSimulationAdminHandler(
                    registerService,
                    plannerService,
                    deliveryProgressService,
                    deliveryStatsService,
                    httpBidderRequester,
                    mapper,
                    path);
        }

        @Bean
        BeanPostProcessor simulationCustomizationBeanPostProcessor(
                @Autowired(required = false) SimulationAwareHttpBidderRequester httpBidderRequester) {

            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
                    // there are HttpBidderRequester and SimulationAwareHttpBidderRequester in context by now, we would
                    // like to replace former with latter everywhere
                    if (httpBidderRequester != null && bean.getClass().isAssignableFrom(HttpBidderRequester.class)
                            && !(bean instanceof SimulationAwareHttpBidderRequester)) {
                        return httpBidderRequester;
                    }

                    return bean;
                }
            };
        }
    }

    @Bean
    @ConfigurationProperties
    DeploymentProperties deploymentProperties() {
        return new DeploymentProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "deals.planner")
    PlannerProperties plannerProperties() {
        return new PlannerProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "deals.delivery-stats")
    DeliveryStatsProperties deliveryStatsProperties() {
        return new DeliveryStatsProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "deals.delivery-progress")
    DeliveryProgressProperties deliveryProgressProperties() {
        return new DeliveryProgressProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "deals.user-data")
    UserDetailsProperties userDetailsProperties() {
        return new UserDetailsProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "deals.alert-proxy")
    AlertProxyProperties alertProxyProperties() {
        return new AlertProxyProperties();
    }

    @Bean
    @ConfigurationProperties(prefix = "deals.simulation")
    SimulationProperties simulationProperties() {
        return new SimulationProperties();
    }

    @Bean
    BidderRequestCompletionTrackerFactory bidderRequestCompletionTrackerFactory() {
        return new DealsBidderRequestCompletionTrackerFactory();
    }

    @Bean
    DealsProcessor dealsProcessor(
            LineItemService lineItemService,
            @Autowired(required = false) DeviceInfoService deviceInfoService,
            @Autowired(required = false) GeoLocationService geoLocationService,
            UserService userService,
            Clock clock,
            JacksonMapper mapper,
            CriteriaLogManager criteriaLogManager) {

        return new DealsProcessor(
                lineItemService, deviceInfoService, geoLocationService, userService, clock, mapper, criteriaLogManager);
    }

    @Bean
    DeliveryProgressReportFactory deliveryProgressReportFactory(
            DeploymentProperties deploymentProperties,
            @Value("${deals.delivery-progress-report.competitors-number}") int competitorsNumber,
            LineItemService lineItemService) {

        return new DeliveryProgressReportFactory(
                deploymentProperties.toComponentProperties(), competitorsNumber, lineItemService);
    }

    @Bean
    AlertHttpService alertHttpService(JacksonMapper mapper,
                                      HttpClient httpClient,
                                      Clock clock,
                                      DeploymentProperties deploymentProperties,
                                      AlertProxyProperties alertProxyProperties) {
        return new AlertHttpService(mapper, httpClient, clock, deploymentProperties.toComponentProperties(),
                alertProxyProperties.toComponentProperties());
    }

    @Bean
    TargetingService targetingService(JacksonMapper mapper) {
        return new TargetingService(mapper);
    }

    @Bean
    AdminCentralService adminCentralService(
            CriteriaManager criteriaManager,
            LineItemService lineItemService,
            DeliveryProgressService deliveryProgressService,
            @Autowired(required = false) @Qualifier("settingsCache") SettingsCache settingsCache,
            @Autowired(required = false) @Qualifier("ampSettingsCache") SettingsCache ampSettingsCache,
            @Autowired(required = false) CachingApplicationSettings cachingApplicationSettings,
            JacksonMapper mapper,
            List<Suspendable> suspendables) {
        return new AdminCentralService(criteriaManager, lineItemService, deliveryProgressService,
                settingsCache, ampSettingsCache, cachingApplicationSettings, mapper, suspendables);
    }

    @Bean
    ApplicationEventService applicationEventService(EventBus eventBus) {
        return new ApplicationEventService(eventBus);
    }

    @Bean
    AdminEventService adminEventService(EventBus eventBus) {
        return new AdminEventService(eventBus);
    }

    @Bean
    EventServiceInitializer eventServiceInitializer(List<ApplicationEventProcessor> applicationEventProcessors,
                                                    List<AdminEventProcessor> adminEventProcessors,
                                                    EventBus eventBus) {
        return new EventServiceInitializer(applicationEventProcessors, adminEventProcessors, eventBus);
    }

    @Validated
    @Data
    @NoArgsConstructor
    private static class DeploymentProperties {

        @NotBlank
        private String hostId;

        @NotBlank
        private String datacenterRegion;

        @NotBlank
        private String vendor;

        @NotBlank
        private String profile;

        @NotBlank
        private String infra;

        @NotBlank
        private String dataCenter;

        @NotBlank
        private String system;

        @NotBlank
        private String subSystem;

        public org.prebid.server.deals.model.DeploymentProperties toComponentProperties() {
            return org.prebid.server.deals.model.DeploymentProperties.builder()
                    .pbsHostId(getHostId()).pbsRegion(getDatacenterRegion()).pbsVendor(getVendor())
                    .profile(getProfile()).infra(getInfra()).dataCenter(getDataCenter()).system(getSystem())
                    .subSystem(getSubSystem()).build();
        }
    }

    @Validated
    @Data
    @NoArgsConstructor
    private static class PlannerProperties {

        @NotBlank
        private String planEndpoint;
        @NotBlank
        private String registerEndpoint;
        @NotNull
        private Long timeoutMs;
        @NotNull
        private Long registerPeriodSec;
        @NotBlank
        private String username;
        @NotBlank
        private String password;

        public org.prebid.server.deals.model.PlannerProperties toComponentProperties() {
            return org.prebid.server.deals.model.PlannerProperties.builder()
                    .planEndpoint(getPlanEndpoint())
                    .registerEndpoint(getRegisterEndpoint())
                    .timeoutMs(getTimeoutMs())
                    .registerPeriodSeconds(getRegisterPeriodSec())
                    .username(getUsername())
                    .password(getPassword())
                    .build();
        }
    }

    @Validated
    @Data
    @NoArgsConstructor
    private static class DeliveryStatsProperties {

        @NotBlank
        private String endpoint;
        @NotNull
        private Integer cachedReportsNumber;
        @NotNull
        private Long timeoutMs;
        @NotNull
        private Integer lineItemsPerReport;
        @NotNull
        private Integer reportsIntervalMs;
        @NotNull
        private Integer batchesIntervalMs;
        @NotNull
        private Boolean requestCompressionEnabled;
        @NotBlank
        private String username;
        @NotBlank
        private String password;

        public org.prebid.server.deals.model.DeliveryStatsProperties toComponentProperties() {
            return org.prebid.server.deals.model.DeliveryStatsProperties.builder()
                    .endpoint(getEndpoint())
                    .cachedReportsNumber(getCachedReportsNumber())
                    .timeoutMs(getTimeoutMs())
                    .lineItemsPerReport(getLineItemsPerReport())
                    .reportsIntervalMs(getReportsIntervalMs())
                    .batchesIntervalMs(getBatchesIntervalMs())
                    .requestCompressionEnabled(getRequestCompressionEnabled())
                    .username(getUsername())
                    .password(getPassword())
                    .build();
        }
    }

    @Validated
    @Data
    @NoArgsConstructor
    private static class DeliveryProgressProperties {

        @NotNull
        private Long lineItemStatusTtlSec;
        @NotNull
        private Integer cachedPlansNumber;

        public org.prebid.server.deals.model.DeliveryProgressProperties toComponentProperties() {
            return org.prebid.server.deals.model.DeliveryProgressProperties.of(getLineItemStatusTtlSec(),
                    getCachedPlansNumber());
        }
    }

    @Validated
    @Data
    @NoArgsConstructor
    private static class UserDetailsProperties {

        @NotBlank
        private String userDetailsEndpoint;
        @NotBlank
        private String winEventEndpoint;
        @NotNull
        private Long timeout;
        @NotNull
        private List<UserIdRule> userIds;

        public org.prebid.server.deals.model.UserDetailsProperties toComponentProperties() {
            final List<org.prebid.server.deals.model.UserIdRule> componentUserIds = getUserIds().stream()
                    .map(DealsConfiguration.UserIdRule::toComponentProperties)
                    .collect(Collectors.toList());

            return org.prebid.server.deals.model.UserDetailsProperties.of(
                    getUserDetailsEndpoint(), getWinEventEndpoint(), getTimeout(), componentUserIds);
        }
    }

    @Validated
    @Data
    @NoArgsConstructor
    private static class AlertProxyProperties {

        @NotNull
        private boolean enabled;

        @NotBlank
        private String url;

        @NotNull
        private Integer timeoutSec;

        Map<String, Long> alertTypes;

        @NotBlank
        private String username;

        @NotBlank
        private String password;

        public org.prebid.server.deals.model.AlertProxyProperties toComponentProperties() {
            return org.prebid.server.deals.model.AlertProxyProperties.builder()
                    .enabled(isEnabled()).url(getUrl()).timeoutSec(getTimeoutSec())
                    .alertTypes(ObjectUtils.firstNonNull(this::getAlertTypes, HashMap::new))
                    .username(getUsername())
                    .password(getPassword()).build();
        }
    }

    @Validated
    @NoArgsConstructor
    @Data
    private static class UserIdRule {

        @NotBlank
        private String type;

        @NotBlank
        private String source;

        @NotBlank
        private String location;

        org.prebid.server.deals.model.UserIdRule toComponentProperties() {
            return org.prebid.server.deals.model.UserIdRule.of(getType(), getSource(), getLocation());
        }
    }

    @Validated
    @NoArgsConstructor
    @Data
    private static class SimulationProperties {

        @NotNull
        boolean enabled;

        Boolean winEventsEnabled;

        Boolean userDetailsEnabled;

        org.prebid.server.deals.model.SimulationProperties toComponentProperties() {
            return org.prebid.server.deals.model.SimulationProperties.builder()
                    .enabled(isEnabled())
                    .winEventsEnabled(getWinEventsEnabled() != null ? getWinEventsEnabled() : false)
                    .userDetailsEnabled(getUserDetailsEnabled() != null ? getUserDetailsEnabled() : false)
                    .build();
        }
    }
}
