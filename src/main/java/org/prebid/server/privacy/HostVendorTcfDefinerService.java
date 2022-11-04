package org.prebid.server.privacy;

import io.vertx.core.Future;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import lombok.experimental.Delegate;
import org.prebid.server.privacy.gdpr.TcfDefinerService;
import org.prebid.server.privacy.gdpr.model.HostVendorTcfResponse;
import org.prebid.server.privacy.gdpr.model.PrivacyEnforcementAction;
import org.prebid.server.privacy.gdpr.model.TcfContext;
import org.prebid.server.privacy.gdpr.model.TcfResponse;

import java.util.Collections;
import java.util.Map;
import java.util.Objects;

public class HostVendorTcfDefinerService {

    private static final Logger logger = LoggerFactory.getLogger(HostVendorTcfDefinerService.class);

    @Delegate
    private final TcfDefinerService tcfDefinerService;
    private final Integer gdprHostVendorId;

    public HostVendorTcfDefinerService(TcfDefinerService tcfDefinerService, Integer gdprHostVendorId) {
        this.tcfDefinerService = Objects.requireNonNull(tcfDefinerService);
        this.gdprHostVendorId = validateHostVendorId(gdprHostVendorId);
    }

    private static Integer validateHostVendorId(Integer gdprHostVendorId) {
        if (gdprHostVendorId == null) {
            logger.warn("gdpr.host-vendor-id not specified. Will skip host company GDPR checks");
        }
        return gdprHostVendorId;
    }

    public Integer getGdprHostVendorId() {
        return gdprHostVendorId;
    }

    /**
     * If host vendor id is null, host allowed to sync cookies.
     */
    public Future<HostVendorTcfResponse> isAllowedForHostVendorId(TcfContext tcfContext) {
        return gdprHostVendorId == null
                ? Future.succeededFuture(HostVendorTcfResponse.allowedVendor())
                : tcfDefinerService.resultForVendorIds(Collections.singleton(gdprHostVendorId), tcfContext)
                .map(this::toHostVendorTcfResponse);
    }

    private HostVendorTcfResponse toHostVendorTcfResponse(TcfResponse<Integer> tcfResponse) {
        return HostVendorTcfResponse.of(tcfResponse.getUserInGdprScope(), tcfResponse.getCountry(),
                isCookieSyncAllowed(tcfResponse));
    }

    private boolean isCookieSyncAllowed(TcfResponse<Integer> hostTcfResponse) {
        final Map<Integer, PrivacyEnforcementAction> vendorIdToAction = hostTcfResponse.getActions();
        final PrivacyEnforcementAction hostActions = vendorIdToAction != null
                ? vendorIdToAction.get(gdprHostVendorId)
                : null;

        return hostActions != null && !hostActions.isBlockPixelSync();
    }
}
