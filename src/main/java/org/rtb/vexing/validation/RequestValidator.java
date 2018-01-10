package org.rtb.vexing.validation;

import com.iab.openrtb.request.Audio;
import com.iab.openrtb.request.Banner;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Format;
import com.iab.openrtb.request.Imp;
import com.iab.openrtb.request.Pmp;
import com.iab.openrtb.request.Site;
import com.iab.openrtb.request.Video;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

public class RequestValidator {

    public ValidationResult validate(BidRequest bidRequest) {
        try {
            if (StringUtils.isBlank(bidRequest.getId())) {
                throw new ValidationException("request missing required field: \"id\"");
            }

            if (bidRequest.getTmax() != null && bidRequest.getTmax() < 0L) {
                throw new ValidationException("request.tmax must be nonnegative. Got %s",
                        bidRequest.getTmax());
            }

            if (CollectionUtils.isEmpty(bidRequest.getImp())) {
                throw new ValidationException("request.imp must contain at least one element.");
            }

            for (int index = 0; index < bidRequest.getImp().size(); index++) {
                validateImp(bidRequest.getImp().get(index), index);
            }

            if ((bidRequest.getSite() == null && bidRequest.getApp() == null)
                    || (bidRequest.getSite() != null && bidRequest.getApp() != null)) {

                throw new ValidationException("request.site or request.app must be defined, "
                        + "but not both.");
            }
            validateSite(bidRequest.getSite());
        } catch (ValidationException ex) {
            return ValidationResult.error(ex.getMessage());
        }
        return ValidationResult.success();
    }

    private void validateSite(Site site) throws ValidationException {
        if (site != null && StringUtils.isBlank(site.getId()) && StringUtils.isBlank(site.getPage())) {
            throw new ValidationException("request.site should include at least one of "
                    + "request.site.id or request.site.page.");
        }
    }

    private void validateImp(Imp imp, int index) throws ValidationException {
        if (StringUtils.isBlank(imp.getId())) {
            throw new ValidationException("request.imp[%d] missing required field: \"id\"", index);
        }
        if (imp.getMetric() != null && !imp.getMetric().isEmpty()) {
            throw new ValidationException("request.imp[%d].metric is not yet supported by "
                    + "prebid-server. Support may be added in the future.", index);
        }
        if (imp.getBanner() == null && imp.getVideo() == null && imp.getAudio() == null && imp.getXNative() == null) {
            throw new ValidationException("request.imp[%d] must contain at least one of \"banner\","
                    + " \"video\", \"audio\", or \"native\"", index);
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
    }

    private void validatePmp(Pmp pmp, int impIndex) throws ValidationException {
        if (pmp != null && pmp.getDeals() != null) {
            for (int dealIndex = 0; dealIndex < pmp.getDeals().size(); dealIndex++) {
                if (StringUtils.isBlank(pmp.getDeals().get(dealIndex).getId())) {
                    throw new ValidationException("request.imp[%d].pmp.deals[%d] missing "
                            + "required field: \"id\"", impIndex, dealIndex);
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
            validateMimes(video.getMimes(), "request.imp[%d].video.mimes must contain at least "
                    + "one supported MIME type", impIndex);
        }
    }

    private void validateAudioMimes(Audio audio, int impIndex) throws ValidationException {
        if (audio != null) {
            validateMimes(audio.getMimes(), "request.imp[%d].audio.mimes must contain at least "
                    + "one supported MIME type", impIndex);
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
