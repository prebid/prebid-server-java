package org.prebid.server.deals;

import io.vertx.core.AsyncResult;
import io.vertx.core.Future;
import io.vertx.core.MultiMap;
import io.vertx.core.Promise;
import io.vertx.core.Vertx;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.http.HttpHeaders;
import org.prebid.server.deals.lineitem.DeliveryProgress;
import org.prebid.server.deals.lineitem.LineItemStatus;
import org.prebid.server.deals.model.AlertPriority;
import org.prebid.server.deals.model.DeliveryStatsProperties;
import org.prebid.server.deals.proto.report.DeliveryProgressReport;
import org.prebid.server.deals.proto.report.DeliveryProgressReportBatch;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.MetricName;
import org.prebid.server.metric.Metrics;
import org.prebid.server.util.HttpUtil;
import org.prebid.server.vertx.http.HttpClient;
import org.prebid.server.vertx.http.model.HttpClientResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.ZonedDateTime;
import java.util.Base64;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.zip.GZIPOutputStream;

public class DeliveryStatsService implements Suspendable {

    private static final Logger logger = LoggerFactory.getLogger(DeliveryStatsService.class);

    private static final String BASIC_AUTH_PATTERN = "Basic %s";
    private static final String PG_TRX_ID = "pg-trx-id";
    private static final String PBS_DELIVERY_CLIENT_ERROR = "pbs-delivery-stats-client-error";
    private static final String SERVICE_NAME = "deliveryStats";
    public static final String GZIP = "gzip";

    private final DeliveryStatsProperties deliveryStatsProperties;
    private final DeliveryProgressReportFactory deliveryProgressReportFactory;
    private final AlertHttpService alertHttpService;
    private final HttpClient httpClient;
    private final Metrics metrics;
    private final Clock clock;
    private final Vertx vertx;
    private final JacksonMapper mapper;

    private final String basicAuthHeader;
    private final NavigableSet<DeliveryProgressReportBatch> requiredBatches;
    private volatile boolean isSuspended;

    public DeliveryStatsService(DeliveryStatsProperties deliveryStatsProperties,
                                DeliveryProgressReportFactory deliveryProgressReportFactory,
                                AlertHttpService alertHttpService,
                                HttpClient httpClient,
                                Metrics metrics,
                                Clock clock,
                                Vertx vertx,
                                JacksonMapper mapper) {

        this.deliveryStatsProperties = Objects.requireNonNull(deliveryStatsProperties);
        this.deliveryProgressReportFactory = Objects.requireNonNull(deliveryProgressReportFactory);
        this.alertHttpService = Objects.requireNonNull(alertHttpService);
        this.httpClient = Objects.requireNonNull(httpClient);
        this.clock = Objects.requireNonNull(clock);
        this.vertx = Objects.requireNonNull(vertx);
        this.metrics = Objects.requireNonNull(metrics);
        this.mapper = Objects.requireNonNull(mapper);
        this.basicAuthHeader = authHeader(deliveryStatsProperties.getUsername(), deliveryStatsProperties.getPassword());

        requiredBatches = new ConcurrentSkipListSet<>(Comparator
                .comparing(DeliveryProgressReportBatch::getDataWindowEndTimeStamp)
                .thenComparing(DeliveryProgressReportBatch::hashCode));
    }

    @Override
    public void suspend() {
        isSuspended = true;
    }

    public void addDeliveryProgress(DeliveryProgress deliveryProgress,
                                    Map<String, LineItemStatus> overallLineItemStatuses) {
        requiredBatches.add(deliveryProgressReportFactory.batchFromDeliveryProgress(deliveryProgress,
                overallLineItemStatuses, null, deliveryStatsProperties.getLineItemsPerReport(), false));
    }

    public void sendDeliveryProgressReports() {
        sendDeliveryProgressReports(ZonedDateTime.now(clock));
    }

    public void sendDeliveryProgressReports(ZonedDateTime now) {
        if (isSuspended) {
            logger.warn("Report will not be sent, as service was suspended from register response");
            return;
        }
        final long batchesIntervalMs = deliveryStatsProperties.getBatchesIntervalMs();
        final int batchesCount = requiredBatches.size();
        final Set<DeliveryProgressReportBatch> sentBatches = new HashSet<>();
        requiredBatches.stream()
                .reduce(Future.<Void>succeededFuture(),
                        (future, batch) -> future.compose(v -> sendBatch(batch, now)
                                .map(aVoid -> sentBatches.add(batch))
                                .compose(aVoid -> batchesIntervalMs > 0 && batchesCount > sentBatches.size()
                                        ? setInterval(batchesIntervalMs)
                                        : Future.succeededFuture())),
                        // combiner does not do any useful operations, just required for this type of reduce operation
                        (a, b) -> Promise.<Void>promise().future())
                .onComplete(result -> handleDeliveryResult(result, batchesCount, sentBatches));
    }

    protected Future<Void> sendBatch(DeliveryProgressReportBatch deliveryProgressReportBatch, ZonedDateTime now) {
        final Promise<Void> promise = Promise.promise();
        final MultiMap headers = headers();
        final Set<DeliveryProgressReport> sentReports = new HashSet<>();
        final long reportIntervalMs = deliveryStatsProperties.getReportsIntervalMs();
        final Set<DeliveryProgressReport> reports = deliveryProgressReportBatch.getReports();
        final int reportsCount = reports.size();
        reports.stream()
                .reduce(Future.<Void>succeededFuture(),
                        (future, report) -> future.compose(v -> sendReport(report, headers, now)
                                .map(aVoid -> sentReports.add(report)))
                                .compose(aVoid -> reportIntervalMs > 0 && reportsCount > sentReports.size()
                                        ? setInterval(reportIntervalMs)
                                        : Future.succeededFuture()),
                        (a, b) -> Promise.<Void>promise().future())
                .onComplete(result -> handleBatchDelivery(result, deliveryProgressReportBatch, sentReports, promise));
        return promise.future();
    }

    protected Future<Void> sendReport(DeliveryProgressReport deliveryProgressReport, MultiMap headers,
                                      ZonedDateTime now) {
        final Promise<Void> promise = Promise.promise();
        final long startTime = clock.millis();
        if (isSuspended) {
            logger.warn("Report will not be sent, as service was suspended from register response");
            promise.complete();
            return promise.future();
        }

        final String body = mapper.encodeToString(deliveryProgressReportFactory
                .updateReportTimeStamp(deliveryProgressReport, now));

        logger.info("Sending delivery progress report to Delivery Stats, {0} is {1}", PG_TRX_ID,
                headers.get(PG_TRX_ID));
        logger.debug("Delivery progress report is: {0}", body);
        if (deliveryStatsProperties.isRequestCompressionEnabled()) {
            headers.add(HttpHeaders.CONTENT_ENCODING, GZIP);
            httpClient.request(HttpMethod.POST, deliveryStatsProperties.getEndpoint(), headers, gzipBody(body),
                    deliveryStatsProperties.getTimeoutMs())
                    .onComplete(result -> handleDeliveryProgressReport(result, deliveryProgressReport, promise,
                            startTime));
        } else {
            httpClient.post(deliveryStatsProperties.getEndpoint(), headers, body,
                    deliveryStatsProperties.getTimeoutMs())
                    .onComplete(result -> handleDeliveryProgressReport(result, deliveryProgressReport, promise,
                            startTime));
        }

        return promise.future();
    }

    /**
     * Handles delivery report response from Planner.
     */
    private void handleDeliveryProgressReport(AsyncResult<HttpClientResponse> result,
                                              DeliveryProgressReport deliveryProgressReport,
                                              Promise<Void> promise,
                                              long startTime) {
        metrics.updateRequestTimeMetric(MetricName.delivery_request_time, clock.millis() - startTime);
        if (result.failed()) {
            logger.warn("Cannot send delivery progress report to delivery stats service", result.cause());
            promise.fail(new PreBidException(String.format("Sending report with id = %s failed in a reason: %s",
                    deliveryProgressReport.getReportId(), result.cause().getMessage())));
        } else {
            final int statusCode = result.result().getStatusCode();
            final String reportId = deliveryProgressReport.getReportId();
            if (statusCode == 200 || statusCode == 409) {
                handleSuccessfulResponse(deliveryProgressReport, promise, statusCode, reportId);
            } else {
                logger.warn("HTTP status code {0}", statusCode);
                promise.fail(new PreBidException(String.format("Delivery stats service responded with status"
                        + " code = %s for report with id = %s", statusCode, deliveryProgressReport.getReportId())));
            }
        }
    }

    private void handleSuccessfulResponse(DeliveryProgressReport deliveryProgressReport, Promise<Void> promise,
                                          int statusCode, String reportId) {
        metrics.updateDeliveryRequestMetric(true);
        promise.complete();
        if (statusCode == 409) {
            logger.info("Delivery stats service respond with 409 duplicated, report with {0} line items and id = {1}"
                            + " was already delivered before and will be removed from from delivery queue",
                    deliveryProgressReport.getLineItemStatus().size(), reportId);
        } else {
            logger.info("Delivery progress report with {0} line items and id = {1} was successfully sent to"
                    + " delivery stats service", deliveryProgressReport.getLineItemStatus().size(), reportId);
        }
    }

    private Future<Void> setInterval(long interval) {
        Promise<Void> promise = Promise.promise();
        vertx.setTimer(interval, event -> promise.complete());
        return promise.future();
    }

    private void handleDeliveryResult(AsyncResult<Void> result, int reportBatchesNumber,
                                      Set<DeliveryProgressReportBatch> sentBatches) {
        if (result.failed()) {
            logger.warn("Failed to send {0} report batches, {1} report batches left to send."
                            + " Reason is: {2}", reportBatchesNumber, reportBatchesNumber - sentBatches.size(),
                    result.cause().getMessage());
            alertHttpService.alertWithPeriod(SERVICE_NAME, PBS_DELIVERY_CLIENT_ERROR, AlertPriority.MEDIUM,
                    String.format("Report was not send to delivery stats service with a reason: %s",
                            result.cause().getMessage()));
            requiredBatches.removeAll(sentBatches);
            handleFailedReportDelivery();
        } else {
            requiredBatches.clear();
            alertHttpService.resetAlertCount(PBS_DELIVERY_CLIENT_ERROR);
            logger.info("{0} report batches were successfully sent.", reportBatchesNumber);
        }
    }

    private void handleBatchDelivery(AsyncResult<Void> result,
                                     DeliveryProgressReportBatch deliveryProgressReportBatch,
                                     Set<DeliveryProgressReport> sentReports,
                                     Promise<Void> promise) {
        final String reportId = deliveryProgressReportBatch.getReportId();
        final String endTimeWindow = deliveryProgressReportBatch.getDataWindowEndTimeStamp();
        final int batchSize = deliveryProgressReportBatch.getReports().size();
        final int sentSize = sentReports.size();
        if (result.succeeded()) {
            logger.info("Batch of reports with reports id = {0}, end time window = {1} and size {2} was successfully"
                    + " sent", reportId, endTimeWindow, batchSize);
            promise.complete();
        } else {
            logger.warn("Failed to sent batch of reports with reports id = {0} end time windows = {1}."
                    + " {2} out of {3} were sent.", reportId, endTimeWindow, sentSize, batchSize);
            deliveryProgressReportBatch.removeReports(sentReports);
            promise.fail(result.cause().getMessage());
        }
    }

    protected MultiMap headers() {
        return MultiMap.caseInsensitiveMultiMap()
                .add(HttpUtil.AUTHORIZATION_HEADER, basicAuthHeader)
                .add(HttpUtil.CONTENT_TYPE_HEADER, HttpUtil.APPLICATION_JSON_CONTENT_TYPE)
                .add(PG_TRX_ID, UUID.randomUUID().toString());
    }

    /**
     * Creates Authorization header value from username and password.
     */
    private static String authHeader(String username, String password) {
        return String.format(
                BASIC_AUTH_PATTERN,
                Base64.getEncoder().encodeToString((username + ':' + password).getBytes()));
    }

    private static byte[] gzipBody(String body) {
        try (ByteArrayOutputStream obj = new ByteArrayOutputStream();
                GZIPOutputStream gzip = new GZIPOutputStream(obj)) {
            gzip.write(body.getBytes(StandardCharsets.UTF_8));
            gzip.finish();
            return obj.toByteArray();
        } catch (IOException e) {
            throw new PreBidException(String.format("Failed to gzip request with a reason : %s", e.getMessage()));
        }
    }

    private void handleFailedReportDelivery() {
        metrics.updateDeliveryRequestMetric(false);
        while (requiredBatches.size() > deliveryStatsProperties.getCachedReportsNumber()) {
            requiredBatches.pollFirst();
        }
    }
}
