package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.AsyncResult;
import io.vertx.core.Handler;
import io.vertx.core.http.Cookie;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.model.Endpoint;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
import org.prebid.server.optout.model.RecaptchaResponse;
import org.prebid.server.util.HttpUtil;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

public class OptoutHandler implements Handler<RoutingContext> {

    private static final Logger logger = LoggerFactory.getLogger(OptoutHandler.class);

    private static final String RECAPTCHA_PARAM = "g-recaptcha-response";
    private static final String OPTOUT_PARAM = "optout";

    private final UidsCookieService uidsCookieService;
    private final GoogleRecaptchaVerifier googleRecaptchaVerifier;
    private final String optoutRedirectUrl;
    private final String optoutUrl;
    private final String optinUrl;

    public OptoutHandler(GoogleRecaptchaVerifier googleRecaptchaVerifier, UidsCookieService uidsCookieService,
                         String optoutRedirectUrl, String optoutUrl, String optinUrl) {
        this.googleRecaptchaVerifier = Objects.requireNonNull(googleRecaptchaVerifier);
        this.uidsCookieService = Objects.requireNonNull(uidsCookieService);
        this.optoutRedirectUrl = Objects.requireNonNull(optoutRedirectUrl);
        this.optoutUrl = Objects.requireNonNull(optoutUrl);
        this.optinUrl = Objects.requireNonNull(optinUrl);
    }

    @Override
    public void handle(RoutingContext routingContext) {
        final String recaptcha = getRequestParam(routingContext, RECAPTCHA_PARAM);
        if (StringUtils.isBlank(recaptcha)) {
            respondWithRedirect(routingContext);
            return;
        }

        googleRecaptchaVerifier.verify(recaptcha)
                .onComplete(result -> handleVerification(routingContext, result));
    }

    private void handleVerification(RoutingContext routingContext, AsyncResult<RecaptchaResponse> result) {
        if (result.failed()) {
            respondWithUnauthorized(routingContext, result.cause());
        } else {
            final boolean optout = isOptout(routingContext);
            respondWithRedirectAndCookie(routingContext, optCookie(optout, routingContext), optUrl(optout));
        }
    }

    private void respondWithRedirect(RoutingContext routingContext) {
        HttpUtil.executeSafely(routingContext, Endpoint.optout,
                response -> response
                        .setStatusCode(HttpResponseStatus.MOVED_PERMANENTLY.code())
                        .putHeader(HttpUtil.LOCATION_HEADER, optoutRedirectUrl)
                        .end());
    }

    private void respondWithUnauthorized(RoutingContext routingContext, Throwable exception) {
        logger.warn("Opt Out failed optout", exception);
        HttpUtil.executeSafely(routingContext, Endpoint.optout,
                response -> response
                        .setStatusCode(HttpResponseStatus.UNAUTHORIZED.code())
                        .end());
    }

    private void respondWithRedirectAndCookie(RoutingContext routingContext, Cookie cookie, String url) {
        HttpUtil.executeSafely(routingContext, Endpoint.optout,
                response -> response
                        .setStatusCode(HttpResponseStatus.MOVED_PERMANENTLY.code())
                        .putHeader(HttpUtil.LOCATION_HEADER, url)
                        .putHeader(HttpUtil.SET_COOKIE_HEADER, HttpUtil.toSetCookieHeaderValue(cookie))
                        .end());
    }

    private static boolean isOptout(RoutingContext routingContext) {
        final String optoutValue = getRequestParam(routingContext, OPTOUT_PARAM);
        return StringUtils.isNotEmpty(optoutValue);
    }

    private Cookie optCookie(boolean optout, RoutingContext routingContext) {
        final UidsCookie uidsCookie = uidsCookieService
                .parseFromRequest(routingContext)
                .updateOptout(optout);
        return uidsCookieService.toCookie(uidsCookie);
    }

    private String optUrl(boolean optout) {
        return optout ? optoutUrl : optinUrl;
    }

    private static String getRequestParam(RoutingContext routingContext, String paramName) {
        final String recaptcha = routingContext.request().getFormAttribute(paramName);
        return StringUtils.isNotEmpty(recaptcha) ? recaptcha : routingContext.request().getParam(paramName);
    }

    public static String getOptoutRedirectUrl(String externalUrl) {
        try {
            final URL url = new URL(externalUrl);
            return new URL(url.toExternalForm() + "/static/optout.html").toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not get optout redirect url", e);
        }
    }
}
