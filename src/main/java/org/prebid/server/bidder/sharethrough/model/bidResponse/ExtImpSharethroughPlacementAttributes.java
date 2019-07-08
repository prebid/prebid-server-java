package org.prebid.server.bidder.sharethrough.model.bidResponse;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Builder
@Value
class ExtImpSharethroughPlacementAttributes {

    String adServerKey;

    String adServerPath;

    Boolean allowDynamicCropping;

    List<String> appThirdPartyPartners;

    String customCardCss;

    String dfpPath;

    String directSellPromotedByText;

    String domain;

    Boolean enableLinkRedirection;

    JsonNode featuredContent;

    Integer maxHeadlineLength;

    Boolean multiAdPlacement;

    String promotedByText;

    String publisherKey;

    Integer renderingPixelOffset;

    List<Integer> safeFrameSize;

    String siteKey;

    String strOptOutUrl;

    String template;

    List<ExtImpSharethroughPlacementThirdPartyPartner> thirdPartyPartners;
}

