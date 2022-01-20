package org.prebid.server.auction.model;

import lombok.Value;
import lombok.experimental.Accessors;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Accessors(fluent = true)
@Value(staticConstructor = "of")
public class PrebidLog {

    private static final int ACCOUNT_LEVEL_DEBUG_DISABLED = 10002;
    private static final int BIDDER_LEVEL_DEBUG_DISABLED = 10003;
    private static final int INCORRECT_FIRST_PARTY_DATA = 10015;
    private static final int INCORRECT_NODE = 10016;

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

    DebugLog debug;

    WarningLog warning;

    ErrorLog error;

    public static PrebidLog empty() {
        return PrebidLog.of(DebugLog.empty(), WarningLog.empty(), ErrorLog.empty());
    }

    public void merge(PrebidLog prebidLog) {
        if (prebidLog != null) {
            this.debug().merge(prebidLog.debug());
            this.warning().merge(prebidLog.warning());
            this.error().merge(prebidLog.error());
        }
    }

    @Value(staticConstructor = "of")
    public static class DebugLog {

        List<PrebidMessage> warning = new ArrayList<>();

        List<PrebidMessage> error = new ArrayList<>();

        static DebugLog empty() {
            return DebugLog.of();
        }

        public void accountLevelDebugDisabled(String message) {
            warning.add(PrebidMessage.of(ACCOUNT_LEVEL_DEBUG_DISABLED, message));
        }

        public void bidderLevelDebugDisabled(String message) {
            warning.add(PrebidMessage.of(BIDDER_LEVEL_DEBUG_DISABLED, message));
        }

        public void incorrectFieldNode(String message) {
            warning.add(PrebidMessage.of(INCORRECT_NODE, message));
        }

        public void incorrectFirstPartyDataType(String message) {
            warning.add(PrebidMessage.of(INCORRECT_FIRST_PARTY_DATA, message));
        }

        public List<String> getIncorrectTypeMessages() {
            return warning.stream()
                    .filter(message ->
                            message.getCode() == INCORRECT_NODE
                                    || message.getCode() == INCORRECT_FIRST_PARTY_DATA)
                    .map(PrebidMessage::getMessage)
                    .collect(Collectors.toList());
        }

        public void merge(DebugLog debugLog) {
            if (debugLog != null) {
                warning.addAll(debugLog.warning());
                error.addAll(debugLog.error());
            }
        }

        public List<PrebidMessage> getAllMessages() {
            final ArrayList<PrebidMessage> messages = new ArrayList<>();
            messages.addAll(warning);
            messages.addAll(error);

            return messages;
        }

        public List<PrebidMessage> getDebugDisabledMessages() {
            return warning.stream()
                    .filter(message ->
                            message.getCode() == ACCOUNT_LEVEL_DEBUG_DISABLED
                                    || message.getCode() == BIDDER_LEVEL_DEBUG_DISABLED)
                    .collect(Collectors.toList());
        }
    }

    @Value(staticConstructor = "of")
    public static class WarningLog {

        List<PrebidMessage> bidder = new ArrayList<>();

        List<PrebidMessage> privacy = new ArrayList<>();

        static WarningLog empty() {
            return WarningLog.of();
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
            bidder.add(PrebidMessage.of(BIDREQUEST_CONTAINS_APP_AND_SITE, message));
        }

        public void validation(String message) {
            bidder.add(PrebidMessage.of(VALIDATION, message));
        }

        public void merge(WarningLog warningLog) {
            if (warningLog != null) {
                bidder.addAll(warningLog.bidder());
                privacy.addAll(warningLog.privacy());
            }
        }

        public List<PrebidMessage> getAllMessages() {
            final ArrayList<PrebidMessage> messages = new ArrayList<>();
            messages.addAll(privacy);
            messages.addAll(bidder);

            return messages;
        }
    }

    @Value(staticConstructor = "of")
    public static class ErrorLog {

        List<PrebidMessage> privacy = new ArrayList<>();

        List<PrebidMessage> bidder = new ArrayList<>();

        public static ErrorLog empty() {
            return ErrorLog.of();
        }

        public List<PrebidMessage> getAllMessages() {
            final ArrayList<PrebidMessage> messages = new ArrayList<>();
            messages.addAll(privacy);
            messages.addAll(bidder);

            return messages;
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

        public void generic(String message) {
            privacy.add(PrebidMessage.of(GENERIC_PRIVACY, message));
        }

        public void invalidTrackingUrl(String message) {
            bidder.add(PrebidMessage.of(VAST_XML, message));
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
