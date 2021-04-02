package org.prebid.server.privacy.gdpr.vendorlist;

import io.vertx.core.Vertx;
import io.vertx.core.file.FileSystem;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.metric.Metrics;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorListV1;
import org.prebid.server.privacy.gdpr.vendorlist.proto.VendorV1;
import org.prebid.server.vertx.http.HttpClient;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class VendorListServiceV1 extends VendorListService<VendorListV1, VendorV1> {

    private static final Logger logger = LoggerFactory.getLogger(VendorListServiceV1.class);

    private static final int TCF_VERSION = 1;

    public VendorListServiceV1(String cacheDir,
                               String endpointTemplate,
                               int defaultTimeoutMs,
                               long refreshMissingListPeriodMs,
                               boolean deprecated,
                               Integer gdprHostVendorId,
                               String fallbackVendorListPath,
                               BidderCatalog bidderCatalog,
                               Vertx vertx,
                               FileSystem fileSystem,
                               HttpClient httpClient,
                               Metrics metrics,
                               JacksonMapper mapper) {

        super(
                cacheDir,
                endpointTemplate,
                defaultTimeoutMs,
                refreshMissingListPeriodMs,
                deprecated,
                gdprHostVendorId,
                fallbackVendorListPath,
                bidderCatalog,
                vertx,
                fileSystem,
                httpClient,
                metrics,
                mapper);
    }

    protected VendorListV1 toVendorList(String content) {
        try {
            return mapper.mapper().readValue(content, VendorListV1.class);
        } catch (IOException e) {
            final String message = String.format("Cannot parse vendor list from: %s", content);

            logger.error(message, e);
            throw new PreBidException(message, e);
        }
    }

    protected Map<Integer, VendorV1> filterVendorIdToVendors(VendorListV1 vendorList) {
        return vendorList.getVendors().stream()
                .filter(vendor -> knownVendorIds.contains(vendor.getId())) // optimize cache to use only known vendors
                .collect(Collectors.toMap(VendorV1::getId, Function.identity()));
    }

    protected boolean isValid(VendorListV1 vendorList) {
        return vendorList.getVendorListVersion() != null
                && vendorList.getLastUpdated() != null
                && CollectionUtils.isNotEmpty(vendorList.getVendors())
                && isValidVendors(vendorList.getVendors());
    }

    @Override
    protected int getTcfVersion() {
        return TCF_VERSION;
    }

    private static boolean isValidVendors(Collection<VendorV1> vendors) {
        return vendors.stream()
                .allMatch(vendor -> vendor != null
                        && vendor.getId() != null
                        && vendor.getPurposeIds() != null
                        && vendor.getLegIntPurposeIds() != null);
    }
}
