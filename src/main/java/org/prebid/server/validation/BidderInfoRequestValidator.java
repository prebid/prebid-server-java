package org.prebid.server.validation;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Imp;
import org.apache.commons.collections4.CollectionUtils;
import org.prebid.server.settings.bidder.CapabilitiesInfo;
import org.prebid.server.settings.bidder.MediaTypeMappings;
import org.prebid.server.settings.bidder.PlatformInfo;
import org.prebid.server.validation.model.ValueValidationResult;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class BidderInfoRequestValidator {

    public ValueValidationResult<BidRequest> validate(BidRequest bidRequest, CapabilitiesInfo capabilities) {
        if (!capabilities.isValidateMediaType()) {
            return ValueValidationResult.success(bidRequest);
        }

        PlatformInfo platformInfo = null;
        if (bidRequest.getApp() != null) {
            platformInfo = capabilities.getApp();
            if (!platformInfo.isEnabled()) {
                return ValueValidationResult.error("App for bidder is not supported, request will be skipped");
            }
        } else if (bidRequest.getSite() != null) {
            platformInfo = capabilities.getSite();
            if (!platformInfo.isEnabled()) {
                return ValueValidationResult.error("Site for bidder is not supported, request will be skipped");
            }
        }

        // Can't be null, see RequestValidator.
        Objects.requireNonNull(platformInfo);

        final MediaTypeMappings supportedMediaTypes = platformInfo.getSupportedMediaTypes();
        final MediaTypeMappings notSupportedMediaTypes = platformInfo.getNotSupportedMediaTypes();
        final ValueValidationResult<List<Imp>> validationImp = validateMediaTypes(bidRequest.getImp(),
                supportedMediaTypes, notSupportedMediaTypes);

        return validationImp.hasErrors() || validationImp.hasWarnings()
                ? toBidRequestValidationResult(bidRequest, validationImp)
                : ValueValidationResult.success(bidRequest);
    }

    /**
     * Method checks all imps if they supported by bidder.
     * Any errors/warning mean that there are changes in passed imp list.
     */
    private ValueValidationResult<List<Imp>> validateMediaTypes(List<Imp> imps,
                                                                MediaTypeMappings supportedMediaTypes,
                                                                MediaTypeMappings notSupportedMediaTypes) {
        final ArrayList<String> warnings = new ArrayList<>();

        final List<Imp> validImps = new ArrayList<>();
        for (Imp imp : imps) {
            final String impId = imp.getId();
            if (!supportedMediaTypes.isAnyCorresponding(imp)) {
                warnings.add(String.format("Imp with id %s doesn't contain media types supported by the "
                        + "bidder, and will be skipped", impId));
                continue;
            }

            final List<MediaTypeMappings.MediaTypeMapping<?>> notSupportedValues =
                    notSupportedMediaTypes.allCorresponding(imp);

            Imp validImp = imp;
            if (CollectionUtils.isNotEmpty(notSupportedValues)) {
                final Imp.ImpBuilder impBuilder = imp.toBuilder();
                notSupportedValues.forEach(notSupportedMapping ->
                        removeValueAndAddWarning(notSupportedMapping, warnings, impBuilder, impId));

                validImp = impBuilder.build();
            }

            validImps.add(validImp);
        }

        return ValueValidationResult.warning(validImps, warnings);
    }

    private void removeValueAndAddWarning(MediaTypeMappings.MediaTypeMapping<?> mediaTypeMapping,
                                          List<String> warnings,
                                          Imp.ImpBuilder impBuilder,
                                          String impId) {
        warnings.add(String.format("Imp with id %s uses %s, but this bidder doesn't this type",
                impId, mediaTypeMapping.getMediaType()));

        mediaTypeMapping.setValue(impBuilder, null);
    }

    private static ValueValidationResult<BidRequest> toBidRequestValidationResult(
            BidRequest bidRequest,
            ValueValidationResult<List<Imp>> validationImp) {
        final List<Imp> validImps = validationImp.getValue();
        final List<String> impWarnings = validationImp.getWarnings();
        final List<String> impErrors = validationImp.getErrors();

        if (CollectionUtils.isEmpty(validImps)) {
            final ArrayList<String> errors = new ArrayList<>(impErrors);
            errors.add("Bid request didn't contain media types supported by the bidder");
            return ValueValidationResult.of(null, impWarnings, errors);
        }

        final BidRequest validBidRequest = bidRequest.toBuilder().imp(validImps).build();
        return ValueValidationResult.of(validBidRequest, impWarnings, impErrors);
    }
}
