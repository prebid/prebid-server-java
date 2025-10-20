package org.prebid.server.hooks.modules.rule.engine.core.request.schema.functions.util;

import com.iab.openrtb.request.App;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Dooh;
import com.iab.openrtb.request.Publisher;
import com.iab.openrtb.request.Site;

import java.util.Optional;

public class DomainUtils {

    private DomainUtils() {
    }

    public static Optional<String> extractDomain(BidRequest bidRequest) {
        return extractPublisherDomain(bidRequest)
                .or(() -> extractPlainDomain(bidRequest));
    }

    private static Optional<String> extractPublisherDomain(BidRequest bidRequest) {
        return extractSitePublisherDomain(bidRequest)
                .or(() -> extractAppPublisherDomain(bidRequest))
                .or(() -> extractDoohPublisherDomain(bidRequest));
    }

    public static Optional<String> extractSitePublisherDomain(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getSite())
                .map(Site::getPublisher)
                .map(Publisher::getDomain);
    }

    public static Optional<String> extractAppPublisherDomain(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getApp())
                .map(App::getPublisher)
                .map(Publisher::getDomain);
    }

    public static Optional<String> extractDoohPublisherDomain(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getDooh())
                .map(Dooh::getPublisher)
                .map(Publisher::getDomain);
    }

    private static Optional<String> extractPlainDomain(BidRequest bidRequest) {
        return extractSiteDomain(bidRequest)
                .or(() -> extractAppDomain(bidRequest))
                .or(() -> extractDoohDomain(bidRequest));
    }

    public static Optional<String> extractSiteDomain(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getSite()).map(Site::getDomain);
    }

    public static Optional<String> extractAppDomain(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getApp()).map(App::getDomain);
    }

    public static Optional<String> extractDoohDomain(BidRequest bidRequest) {
        return Optional.ofNullable(bidRequest.getDooh()).map(Dooh::getDomain);
    }
}
