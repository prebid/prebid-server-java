package org.prebid.server.auction.gpp;

import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Regs;
import com.iab.openrtb.request.User;
import io.vertx.core.Future;
import org.prebid.server.auction.gpp.model.GppContext;
import org.prebid.server.auction.gpp.model.GppContextCreator;
import org.prebid.server.auction.gpp.model.GppContextWrapper;
import org.prebid.server.auction.gpp.model.privacy.TcfEuV2Privacy;
import org.prebid.server.auction.gpp.model.privacy.UspV1Privacy;
import org.prebid.server.auction.model.AuctionContext;
import org.prebid.server.model.UpdateResult;
import org.prebid.server.proto.openrtb.ext.request.ExtRegs;
import org.prebid.server.proto.openrtb.ext.request.ExtUser;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class AuctionGppService {

    private final GppService gppService;

    public AuctionGppService(GppService gppService) {
        this.gppService = Objects.requireNonNull(gppService);
    }

    public Future<GppContext> contextFrom(AuctionContext auctionContext) {
        return Future.succeededFuture(auctionContext)
                .map(AuctionContext::getBidRequest)
                .map(AuctionGppService::contextFrom)
                .map(gppService::processContext)
                .onSuccess(gppContextWrapper -> enrichWithErrors(auctionContext, gppContextWrapper.getErrors()))
                .map(GppContextWrapper::getGppContext);
    }

    private static GppContextWrapper contextFrom(BidRequest bidRequest) {
        final Regs regs = bidRequest.getRegs();

        final String gpp = regs != null ? regs.getGpp() : null;
        final List<Integer> gppSid = regs != null ? regs.getGppSid() : null;

        final Integer gdpr = gdpr(regs);
        final String consent = consent(bidRequest.getUser());

        final String usPrivacy = usPrivacy(regs);

        return GppContextCreator.from(gpp, gppSid)
                .with(TcfEuV2Privacy.of(gdpr, consent))
                .with(UspV1Privacy.of(usPrivacy))
                .build();
    }

    private static Integer gdpr(Regs regs) {
        final Optional<Regs> regsOptional = Optional.ofNullable(regs);

        return regsOptional
                .map(Regs::getGdpr)
                .or(() -> regsOptional
                        .map(Regs::getExt)
                        .map(ExtRegs::getGdpr))
                .orElse(null);
    }

    private static String consent(User user) {
        final Optional<User> userOptional = Optional.ofNullable(user);

        return userOptional
                .map(User::getConsent)
                .or(() -> userOptional
                        .map(User::getExt)
                        .map(ExtUser::getConsent))
                .orElse(null);
    }

    private static String usPrivacy(Regs regs) {
        final Optional<Regs> regsOptional = Optional.ofNullable(regs);

        return regsOptional
                .map(Regs::getUsPrivacy)
                .or(() -> regsOptional
                        .map(Regs::getExt)
                        .map(ExtRegs::getUsPrivacy))
                .orElse(null);
    }

    private static void enrichWithErrors(AuctionContext auctionContext, List<String> errors) {
        if (auctionContext.getDebugContext().isDebugEnabled()) {
            auctionContext.getDebugWarnings().addAll(errors);
        }
    }

    public BidRequest updateBidRequest(BidRequest bidRequest, AuctionContext auctionContext) {
        final GppContext gppContext = auctionContext.getGppContext();

        final UpdateResult<User> updatedUser = updateUser(bidRequest.getUser(), gppContext);
        final UpdateResult<Regs> updatedRegs = updateRegs(bidRequest.getRegs(), gppContext);

        return updatedUser.isUpdated() || updatedRegs.isUpdated()
                ? bidRequest.toBuilder()
                .user(updatedUser.getValue())
                .regs(updatedRegs.getValue())
                .build()
                : bidRequest;
    }

    private static UpdateResult<User> updateUser(User user, GppContext gppContext) {
        final UpdateResult<String> updatedConsent = updateConsent(user, gppContext);

        return updatedConsent.isUpdated()

                ? UpdateResult.updated(
                Optional.ofNullable(user)
                        .map(User::toBuilder)
                        .orElseGet(User::builder)
                        .consent(updatedConsent.getValue())
                        .build())

                : UpdateResult.unaltered(user);
    }

    private static UpdateResult<String> updateConsent(User user, GppContext gppContext) {
        final TcfEuV2Privacy tcfEuV2Privacy = gppContext.regions().getTcfEuV2Privacy();
        return updateResult(
                user != null ? user.getConsent() : null,
                tcfEuV2Privacy != null ? tcfEuV2Privacy.getConsent() : null);
    }

    private static <T> UpdateResult<T> updateResult(T original, T gpp) {
        return original == null && gpp != null
                ? UpdateResult.updated(gpp)
                : UpdateResult.unaltered(original);
    }

    private static UpdateResult<Regs> updateRegs(Regs regs, GppContext gppContext) {
        final UpdateResult<Integer> updatedGdpr = updateGdpr(regs, gppContext);
        final UpdateResult<String> updatedUsPrivacy = updateUsPrivacy(regs, gppContext);

        return updatedGdpr.isUpdated() || updatedUsPrivacy.isUpdated()

                ? UpdateResult.updated(
                Optional.ofNullable(regs)
                        .map(Regs::toBuilder)
                        .orElseGet(Regs::builder)
                        .gdpr(updatedGdpr.getValue())
                        .usPrivacy(updatedUsPrivacy.getValue())
                        .build())

                : UpdateResult.unaltered(regs);
    }

    private static UpdateResult<Integer> updateGdpr(Regs regs, GppContext gppContext) {
        final TcfEuV2Privacy tcfEuV2Privacy = gppContext.regions().getTcfEuV2Privacy();
        return updateResult(
                regs != null ? regs.getGdpr() : null,
                tcfEuV2Privacy != null ? tcfEuV2Privacy.getGdpr() : null);
    }

    private static UpdateResult<String> updateUsPrivacy(Regs regs, GppContext gppContext) {
        final UspV1Privacy uspV1Privacy = gppContext.regions().getUspV1Privacy();
        return updateResult(
                regs != null ? regs.getUsPrivacy() : null,
                uspV1Privacy != null ? uspV1Privacy.getUsPrivacy() : null);
    }
}
