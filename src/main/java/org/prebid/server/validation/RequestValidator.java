package org.prebid.server.validation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.User;
import com.iab.openrtb.request.Video;
import io.vertx.core.json.Json;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.BidderRequesterCatalog;
import org.prebid.server.proto.openrtb.ext.request.ExtBidRequest;
import org.prebid.server.proto.openrtb.ext.request.ExtRequestPrebid;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserDigiTrust;
import org.prebid.server.validation.model.ValidationResult;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A component that validates {@link BidRequest} objects for openrtb2 auction endpoint.
 * Validations are processed by the validate method and returns {@link ValidationResult}
 */
public class RequestValidator {

    private static final String PREBID_EXT = "prebid";

    private final BidderRequesterCatalog bidderRequesterCatalog;
    private final BidderParamValidator bidderParamValidator;

    /**
     * Constructs a RequestValidator that will use the BidderParamValidator passed in order to validate all critical
     * properties of bidRequest
     */
    public RequestValidator(BidderRequesterCatalog bidderRequesterCatalog, BidderParamValidator bidderParamValidator) {
        this.bidderRequesterCatalog = Objects.requireNonNull(bidderRequesterCatalog);
        this.bidderParamValidator = Objects.requireNonNull(bidderParamValidator);
    }

    /**
     * Validates the {@link BidRequest} against a list of validation checks, however, reports only one problem
     * at a time.
     */
    public ValidationResult validate(BidRequest bidRequest) {
        try {
            if (StringUtils.isBlank(bidRequest.getId())) {
                throw new ValidationException("request missing required field: \"id\"");
            }

            if (bidRequest.getTmax() != null && bidRequest.getTmax() < 0L) {
                throw new ValidationException("request.tmax must be nonnegative. Got %s", bidRequest.getTmax());
            }

            final ExtBidRequest extBidRequest = parseAndValidateExtBidRequest(bidRequest);

            final ExtRequestPrebid extRequestPrebid = extBidRequest != null ? extBidRequest.getPrebid() : null;
            Map<String, String> aliases = extRequestPrebid != null ? extRequestPrebid.getAliases() : null;
            aliases = aliases != null ? aliases : Collections.emptyMap();

            validateAliases(aliases);

            if (CollectionUtils.isEmpty(bidRequest.getImp())) {
                throw new ValidationException("request.imp must contain at least one element.");
            }

            for (int index = 0; index < bidRequest.getImp().size(); index++) {
                validateImp(bidRequest.getImp().get(index), aliases, index);
            }

            if ((bidRequest.getSite() == null && bidRequest.getApp() == null)
                    || (bidRequest.getSite() != null && bidRequest.getApp() != null)) {

                throw new ValidationException("request.site or request.app must be defined, but not both.");
            }
            validateSite(bidRequest.getSite());
            validateUser(bidRequest.getUser());
        } catch (ValidationException ex) {
            return ValidationResult.error(ex.getMessage());
        }
        return ValidationResult.success();
    }

    private ExtBidRequest parseAndValidateExtBidRequest(BidRequest bidRequest) throws ValidationException {
        ExtBidRequest extBidRequest = null;
        if (bidRequest.getExt() != null) {
            try {
                extBidRequest = Json.mapper.treeToValue(bidRequest.getExt(), ExtBidRequest.class);
            } catch (JsonProcessingException e) {
                throw new ValidationException("request.ext is invalid: %s", e.getMessage());
            }
        }
        return extBidRequest;
    }

    /**
     * Validates aliases. Throws {@link ValidationException} in cases when alias points to invalid bidder or when alias
     * is equals to itself.
     */
    private void validateAliases(Map<String, String> aliases) throws ValidationException {
        for (final Map.Entry<String, String> aliasToBidder : aliases.entrySet()) {
            final String alias = aliasToBidder.getKey();
            final String coreBidder = aliasToBidder.getValue();
            if (!bidderRequesterCatalog.isValidName(coreBidder)) {
                throw new ValidationException(String.format(
                        "request.ext.prebid.aliases.%s refers to unknown bidder: %s", alias, coreBidder));
            }
            if (alias.equals(coreBidder)) {
                throw new ValidationException(String.format("request.ext.prebid.aliases.%s defines a no-op alias. "
                        + "Choose a different alias, or remove this entry.", alias));
            }
        }
    }

    private void validateSite(Site site) throws ValidationException {
        if (site != null && StringUtils.isBlank(site.getId()) && StringUtils.isBlank(site.getPage())) {
            throw new ValidationException(
                    "request.site should include at least one of request.site.id or request.site.page.");
        }
    }

    private void validateUser(User user) throws ValidationException {
        if (user != null && user.getExt() != null) {
            try {
                final ExtUser extUser = Json.mapper.treeToValue(user.getExt(), ExtUser.class);
                final ExtUserDigiTrust digitrust = extUser.getDigitrust();
                if (digitrust == null || digitrust.getPref() != 0) {
                    throw new ValidationException("request.user contains a digitrust object that is not valid.");
                }
            } catch (JsonProcessingException e) {
                throw new ValidationException("request.user.ext object is not valid.");
            }
        }
    }

    private void validateImp(Imp imp, Map<String, String> aliases, int index) throws ValidationException {
        if (StringUtils.isBlank(imp.getId())) {
            throw new ValidationException("request.imp[%d] missing required field: \"id\"", index);
        }
        if (imp.getMetric() != null && !imp.getMetric().isEmpty()) {
            throw new ValidationException(
                    "request.imp[%d].metric is not yet supported by prebid-server. Support may be added in the future.",
                    index);
        }
        if (imp.getBanner() == null && imp.getVideo() == null && imp.getAudio() == null && imp.getXNative() == null) {
            throw new ValidationException(
                    "request.imp[%d] must contain at least one of \"banner\", \"video\", \"audio\", or \"native\"",
                    index);
        }

        validateBanner(imp.getBanner(), index);
        validateVideoMimes(imp.getVideo(), index);
        validateAudioMimes(imp.getAudio(), index);

        if (imp.getXNative() != null) {
            if (StringUtils.isBlank(imp.getXNative().getRequest())) {
                throw new ValidationException("request.imp[%d].native.request must be a JSON "
                        + "encoded string conforming to the openrtb 1.2 Native spec", index);
            }
        }

        validatePmp(imp.getPmp(), index);
        validateImpExt(imp.getExt(), aliases, index);
    }

    private void validateImpExt(ObjectNode ext, Map<String, String> aliases, int impIndex) throws ValidationException {
        if (ext == null || ext.size() < 1) {
            throw new ValidationException("request.imp[%s].ext must contain at least one bidder", impIndex);
        }

        final Iterator<Map.Entry<String, JsonNode>> bidderExtensions = ext.fields();
        while (bidderExtensions.hasNext()) {
            final Map.Entry<String, JsonNode> bidderExtension = bidderExtensions.next();
            String bidderName = bidderExtension.getKey();
            if (!PREBID_EXT.equals(bidderName)) {
                bidderName = aliases.getOrDefault(bidderName, bidderName);
                validateImpBidderExtName(impIndex, bidderExtension, bidderName);
            }
        }
    }

    private void validateImpBidderExtName(int impIndex, Map.Entry<String, JsonNode> bidderExtension, String bidderName)
            throws ValidationException {
        if (bidderRequesterCatalog.isValidName(bidderName)) {
            final Set<String> messages = bidderParamValidator.validate(bidderName, bidderExtension.getValue());
            if (!messages.isEmpty()) {
                throw new ValidationException("request.imp[%d].ext.%s failed validation.\n%s", impIndex,
                        bidderName, messages.stream().collect(Collectors.joining("\n")));
            }
        } else {
            throw new ValidationException(
                    "request.imp[%d].ext contains unknown bidder: %s", impIndex, bidderName);
        }
    }

    private void validatePmp(Pmp pmp, int impIndex) throws ValidationException {
        if (pmp != null && pmp.getDeals() != null) {
            for (int dealIndex = 0; dealIndex < pmp.getDeals().size(); dealIndex++) {
                if (StringUtils.isBlank(pmp.getDeals().get(dealIndex).getId())) {
                    throw new ValidationException("request.imp[%d].pmp.deals[%d] missing required field: \"id\"",
                            impIndex, dealIndex);
                }
            }
        }
    }

    private void validateBanner(Banner banner, int impIndex) throws ValidationException {
        if (banner != null && banner.getFormat() != null) {
            for (int formatIndex = 0; formatIndex < banner.getFormat().size(); formatIndex++) {
                validateFormat(banner.getFormat().get(formatIndex), impIndex, formatIndex);
            }
        }
    }

    private void validateFormat(Format format, int impIndex, int formatIndex) throws ValidationException {
        final boolean usesH = hasValue(format.getH());
        final boolean usesW = hasValue(format.getW());
        final boolean usesWmin = hasValue(format.getWmin());
        final boolean usesWratio = hasValue(format.getWratio());
        final boolean usesHratio = hasValue(format.getHratio());
        final boolean usesHW = usesH || usesW;
        final boolean usesRatios = usesWmin || usesWratio || usesHratio;

        if (usesHW && usesRatios) {
            throw new ValidationException("Request imp[%d].banner.format[%d] should define *either*"
                    + " {w, h} *or* {wmin, wratio, hratio}, but not both. If both are valid, send two \"format\" "
                    + "objects in the request.", impIndex, formatIndex);
        }

        if (!usesHW && !usesRatios) {
            throw new ValidationException("Request imp[%d].banner.format[%d] should define *either*"
                    + " {w, h} (for static size requirements) *or* {wmin, wratio, hratio} (for flexible sizes) "
                    + "to be non-zero.", impIndex, formatIndex);
        }

        if (usesHW && (!usesH || !usesW)) {
            throw new ValidationException("Request imp[%d].banner.format[%d] must define non-zero"
                    + " \"h\" and \"w\" properties.", impIndex, formatIndex);
        }

        if (usesRatios && (!usesWmin || !usesWratio || !usesHratio)) {
            throw new ValidationException("Request imp[%d].banner.format[%d] must define non-zero"
                    + " \"wmin\", \"wratio\", and \"hratio\" properties.", impIndex, formatIndex);
        }
    }

    private void validateVideoMimes(Video video, int impIndex) throws ValidationException {
        if (video != null) {
            validateMimes(video.getMimes(),
                    "request.imp[%d].video.mimes must contain at least one supported MIME type", impIndex);
        }
    }

    private void validateAudioMimes(Audio audio, int impIndex) throws ValidationException {
        if (audio != null) {
            validateMimes(audio.getMimes(),
                    "request.imp[%d].audio.mimes must contain at least one supported MIME type", impIndex);
        }
    }

    private void validateMimes(List<String> mimes, String msg, int index) throws ValidationException {
        if (CollectionUtils.isEmpty(mimes)) {
            throw new ValidationException(msg, index);
        }
    }

    private static class ValidationException extends Exception {
        ValidationException(String errorMessageFormat, Object... args) {
            super(String.format(errorMessageFormat, args));
        }
    }

    private static boolean hasValue(Integer value) {
        return value != null && value != 0;
    }
}
