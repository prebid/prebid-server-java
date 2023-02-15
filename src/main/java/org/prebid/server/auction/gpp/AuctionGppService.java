package org.prebid.server.auction.gpp;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import org.apache.commons.lang3.ObjectUtils;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.util.ObjectUtil;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AuctionGppService {

    private final GppService gppService;

    public AuctionGppService(GppService gppService) {
        this.gppService = Objects.requireNonNull(gppService);
    }

    public BidRequest apply(BidRequest bidRequest, AuctionContext auctionContext) {
        final GppContext gppContext = gppService.processContext(contextFrom(bidRequest));

        updateAuctionContext(auctionContext, gppContext);
        return updateBidRequest(bidRequest, gppContext);
    }

    private static GppContext contextFrom(BidRequest bidRequest) {
        final Regs regs = bidRequest.getRegs();

        final String gpp = regs != null ? regs.getGpp() : null;
        final List<Integer> gppSid = regs != null ? regs.getGppSid() : null;

        final Integer gdpr = regs != null ? regs.getGdpr() : null;
        final String consent = ObjectUtil.getIfNotNull(bidRequest.getUser(), User::getConsent);

        final String usPrivacy = regs != null ? regs.getUsPrivacy() : null;

        return GppContextCreator.from(gpp, gppSid)
                .withTcfEuV2(gdpr, consent)
                .withUspV1(usPrivacy)
                .build();
    }

    private static void updateAuctionContext(AuctionContext auctionContext, GppContext gppContext) {
        auctionContext.getDebugWarnings().addAll(gppContext.getErrors());
    }

    private static BidRequest updateBidRequest(BidRequest bidRequest, GppContext gppContext) {
        final User user = bidRequest.getUser();
        final User updatedUser = updateUser(user, gppContext);

        final Regs regs = bidRequest.getRegs();
        final Regs updatedRegs = updateRegs(regs, gppContext);

        return ObjectUtils.anyNotNull(updatedUser, updatedRegs)
                ? bidRequest.toBuilder()
                .user(ObjectUtils.defaultIfNull(updatedUser, user))
                .regs(ObjectUtils.defaultIfNull(updatedRegs, regs))
                .build()
                : bidRequest;
    }

    private static User updateUser(User user, GppContext gppContext) {
        final String consent = user != null ? user.getConsent() : null;
        final String gppConsent = gppContext.getRegions().getTcfEuV2Privacy().getConsent();

        return needUpdates(consent, gppConsent)

                ? Optional.ofNullable(user)
                .map(User::toBuilder)
                .orElseGet(User::builder)
                .consent(gppConsent)
                .build()

                : null;
    }

    private static <T> boolean needUpdates(T original, T gpp) {
        return original == null && gpp != null;
    }

    private static Regs updateRegs(Regs regs, GppContext gppContext) {
        final GppContext.Regions regions = gppContext.getRegions();

        final Integer gdpr = regs != null ? regs.getGdpr() : null;
        final Integer gppGdpr = regions.getTcfEuV2Privacy().getGdpr();

        final String usPrivacy = regs != null ? regs.getUsPrivacy() : null;
        final String gppUsPrivacy = regions.getUspV1Privacy().getUsPrivacy();

        return needUpdates(gdpr, gppGdpr) || needUpdates(usPrivacy, gppUsPrivacy)

                ? Optional.ofNullable(regs)
                .map(Regs::toBuilder)
                .orElseGet(Regs::builder)
                .gdpr(ObjectUtils.defaultIfNull(gdpr, gppGdpr))
                .usPrivacy(ObjectUtils.defaultIfNull(usPrivacy, gppUsPrivacy))
                .build()

                : null;
    }
}
