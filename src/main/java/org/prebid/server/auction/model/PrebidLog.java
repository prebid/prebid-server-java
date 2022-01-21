package org.prebid.server.auction.model;

import lombok.Value;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class PrebidLog {

    private static final int ACCOUNT_LEVEL_DEBUG_DISABLED = 10002;
    private static final int BIDDER_LEVEL_DEBUG_DISABLED = 10003;
    private static final int MULTIBID = 10005;
    private static final int VAST_XML = 10006;
    private static final int BIDREQUEST_CONTAINS_APP_AND_SITE = 10007;
    private static final int INVALID_PRICE = 10008;
    private static final int CATEGORY_MAPPING = 10009;
    private static final int VALIDATION = 10010;
    private static final int TCF = 10011;
    private static final int CCPA = 10012;
    private static final int COPPA = 10013;
    private static final int GENERIC_PRIVACY = 10014;
    private static final int INCORRECT_FIRST_PARTY_DATA = 10015;
    private static final int INCORRECT_NODE = 10016;

    WarningLog warning;

    ErrorLog error;

    public static PrebidLog empty() {
        return PrebidLog.of(WarningLog.empty(), ErrorLog.empty());
    }

    public void merge(PrebidLog prebidLog) {
        if (prebidLog != null) {
            this.warning().merge(prebidLog.warning());
            this.error().merge(prebidLog.error());
        }
    }

    @Value(staticConstructor = "empty")
    public static class WarningLog {

        List<PrebidMessage> bidder = new ArrayList<>();

        List<PrebidMessage> privacy = new ArrayList<>();

        List<PrebidMessage> account = new ArrayList<>();

        List<PrebidMessage> fpd = new ArrayList<>();

        List<PrebidMessage> validation = new ArrayList<>();

        public void tcf(String message) {
            privacy.add(PrebidMessage.of(TCF, message));
        }

        public void ccpa(String message) {
            privacy.add(PrebidMessage.of(CCPA, message));
        }

        public void coppa(String message) {
            privacy.add(PrebidMessage.of(COPPA, message));
        }

        public void generic(String message) {
            privacy.add(PrebidMessage.of(GENERIC_PRIVACY, message));
        }

        public void multibid(String message) {
            bidder.add(PrebidMessage.of(MULTIBID, message));
        }

        public void invalidBidPrice(String message) {
            bidder.add(PrebidMessage.of(INVALID_PRICE, message));
        }

        public void bidRequestContainsAppAndSite(String message) {
            validation.add(PrebidMessage.of(BIDREQUEST_CONTAINS_APP_AND_SITE, message));
        }

        public void validation(String message) {
            validation.add(PrebidMessage.of(VALIDATION, message));
        }

        public void accountLevelDebugDisabled(String message) {
            account.add(PrebidMessage.of(ACCOUNT_LEVEL_DEBUG_DISABLED, message));
        }

        public void bidderLevelDebugDisabled(String message) {
            bidder.add(PrebidMessage.of(BIDDER_LEVEL_DEBUG_DISABLED, message));
        }

        public void invalidTrackingUrl(String message) {
            bidder.add(PrebidMessage.of(VAST_XML, message));
        }

        public void incorrectFieldNode(String message) {
            validation.add(PrebidMessage.of(INCORRECT_NODE, message));
        }

        public void incorrectFirstPartyDataType(String message) {
            fpd.add(PrebidMessage.of(INCORRECT_FIRST_PARTY_DATA, message));
        }

        public void merge(WarningLog warningLog) {
            if (warningLog != null) {
                bidder.addAll(warningLog.bidder());
                privacy.addAll(warningLog.privacy());
            }
        }

        public List<PrebidMessage> getAllMessages() {
            return Stream.of(privacy, bidder, fpd, validation, account)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        }

        public List<String> getIncorrectTypeMessages() {
            final Stream<PrebidMessage> incorrectNodeStream = validation.stream()
                    .filter(e -> e.getCode() == INCORRECT_NODE);

            return Stream.concat(incorrectNodeStream, fpd.stream())
                    .map(PrebidMessage::getMessage)
                    .collect(Collectors.toList());
        }
    }

    @Value(staticConstructor = "empty")
    public static class ErrorLog {

        List<PrebidMessage> privacy = new ArrayList<>();

        List<PrebidMessage> bidder = new ArrayList<>();

        public List<PrebidMessage> getAllMessages() {
            return Stream.of(privacy, bidder)
                    .flatMap(List::stream)
                    .collect(Collectors.toList());
        }

        public void tcf(String message) {
            privacy.add(PrebidMessage.of(TCF, message));
        }

        public void ccpa(String message) {
            privacy.add(PrebidMessage.of(CCPA, message));
        }

        public void coppa(String message) {
            privacy.add(PrebidMessage.of(COPPA, message));
        }

        public void privacy(String message) {
            privacy.add(PrebidMessage.of(GENERIC_PRIVACY, message));
        }

        public void categoryMapping(String message) {
            bidder.add(PrebidMessage.of(CATEGORY_MAPPING, message));
        }

        public void merge(ErrorLog errorLog) {
            if (errorLog != null) {
                bidder.addAll(errorLog.bidder());
                privacy.addAll(errorLog.privacy());
            }
        }
    }

}
