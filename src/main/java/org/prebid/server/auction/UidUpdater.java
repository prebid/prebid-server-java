package org.prebid.server.auction;

import com.iab.openrtb.request.User;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.bidder.BidderCatalog;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;
import org.prebid.server.proto.openrtb.ext.request.ExtUserPrebid;

import java.util.Objects;
import java.util.Optional;
import java.util.stream.Stream;

public class UidUpdater {

    private final String hostCookieFamily;
    private final BidderCatalog bidderCatalog;
    private final UidsCookieService uidsCookieService;

    public UidUpdater(String hostCookieFamily, BidderCatalog bidderCatalog, UidsCookieService uidsCookieService) {
        this.hostCookieFamily = hostCookieFamily;
        this.bidderCatalog = Objects.requireNonNull(bidderCatalog);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
    }

    public UpdateResult<String> updateUid(String bidder, AuctionContext auctionContext, BidderAliases aliases) {
        final User user = auctionContext.getBidRequest().getUser();

        final String uidFromUser = user != null ? user.getBuyeruid() : null;
        if (StringUtils.isNotBlank(uidFromUser)) {
            return UpdateResult.unaltered(uidFromUser);
        }

        final String resolvedBidder = aliases.resolveBidder(bidder);

        final String uidFromExt = uidFromExtUser(user, resolvedBidder);
        final String uidFromUidsCookie = uidFromUidsCookie(auctionContext.getUidsCookie(), resolvedBidder);
        final String uidFromHostCookie = uidFromHostCookie(auctionContext, resolvedBidder);

        return Stream.of(uidFromExt, uidFromUidsCookie, uidFromHostCookie)
                .filter(StringUtils::isNotBlank)
                .map(UpdateResult::updated)
                .findFirst()
                .orElse(UpdateResult.unaltered(null));
    }

    private static String uidFromExtUser(User user, String bidder) {
        return Optional.ofNullable(user)
                .map(User::getExt)
                .map(ExtUser::getPrebid)
                .map(ExtUserPrebid::getBuyeruids)
                .map(uids -> uids.get(bidder))
                .orElse(null);
    }

    private String uidFromUidsCookie(UidsCookie uidsCookie, String bidder) {
        return bidderCatalog.cookieFamilyName(bidder)
                .map(uidsCookie::uidFrom)
                .orElse(null);
    }

    private String uidFromHostCookie(AuctionContext auctionContext, String bidder) {
        return bidderCatalog.cookieFamilyName(bidder)
                .filter(cookieFamily -> StringUtils.equals(cookieFamily, hostCookieFamily))
                .map(cookieFamily -> uidsCookieService.parseHostCookie(auctionContext.getHttpRequest()))
                .orElse(null);
    }
}
