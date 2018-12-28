package org.prebid.server.handler;

import io.netty.handler.codec.http.HttpResponseStatus;
import io.vertx.core.Handler;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.cookie.UidsCookie;
import org.prebid.server.cookie.UidsCookieService;
import org.prebid.server.optout.GoogleRecaptchaVerifier;
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
    public void handle(RoutingContext context) {
        final String recaptcha = getRequestParam(context, RECAPTCHA_PARAM);
        if (StringUtils.isBlank(recaptcha)) {
            sendRedirect(context);
            return;
        }

        googleRecaptchaVerifier.verify(recaptcha)
                .setHandler(result -> {
                    if (result.failed()) {
                        sendUnauthorized(context, result.cause());
                    } else {
                        final boolean optout = isOptout(context);
                        sendResponse(context, optCookie(optout, context), optUrl(optout));
                    }
                });
    }

    private void sendRedirect(RoutingContext context) {
        context.response()
                .putHeader(HttpUtil.LOCATION_HEADER, optoutRedirectUrl)
                .setStatusCode(HttpResponseStatus.MOVED_PERMANENTLY.code())
                .end();
    }

    private void sendUnauthorized(RoutingContext context, Throwable cause) {
        logger.warn("Opt Out failed optout", cause);
        context.response()
                .setStatusCode(HttpResponseStatus.UNAUTHORIZED.code())
                .end();
    }

    private void sendResponse(RoutingContext context, Cookie cookie, String url) {
        context.addCookie(cookie)
                .response()
                .putHeader(HttpUtil.LOCATION_HEADER, url)
                .setStatusCode(HttpResponseStatus.MOVED_PERMANENTLY.code())
                .end();
    }

    private static boolean isOptout(RoutingContext context) {
        final String optoutValue = getRequestParam(context, OPTOUT_PARAM);
        return StringUtils.isNotEmpty(optoutValue);
    }

    private Cookie optCookie(boolean optout, RoutingContext context) {
        final UidsCookie uidsCookie = uidsCookieService
                .parseFromRequest(context)
                .updateOptout(optout);
        return uidsCookieService.toCookie(uidsCookie);
    }

    private String optUrl(boolean optout) {
        return optout ? optoutUrl : optinUrl;
    }

    private static String getRequestParam(RoutingContext context, String paramName) {
        final String recaptcha = context.request().getFormAttribute(paramName);
        return StringUtils.isNotEmpty(recaptcha) ? recaptcha : context.request().getParam(paramName);
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
