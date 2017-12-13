package org.rtb.vexing.handler;

import io.vertx.core.http.HttpHeaders;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Cookie;
import io.vertx.ext.web.RoutingContext;
import org.apache.commons.lang3.StringUtils;
import org.rtb.vexing.config.ApplicationConfig;
import org.rtb.vexing.cookie.UidsCookie;
import org.rtb.vexing.cookie.UidsCookieService;
import org.rtb.vexing.optout.GoogleRecaptchaVerifier;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

public class OptoutHandler {

    private static final Logger logger = LoggerFactory.getLogger(OptoutHandler.class);

    private static final String RECAPTCHA_PARAM = "g-recaptcha-response";
    private static final String OPTOUT_PARAM = "optout";

    private final UidsCookieService uidsCookieService;
    private final GoogleRecaptchaVerifier googleRecaptchaVerifier;
    private final String optoutRedirectUrl;
    private final String optoutUrl;
    private final String optinUrl;

    private OptoutHandler(GoogleRecaptchaVerifier googleRecaptchaVerifier, UidsCookieService uidsCookieService,
                          String optoutRedirectUrl, String optoutUrl, String optinUrl) {
        this.googleRecaptchaVerifier = googleRecaptchaVerifier;
        this.uidsCookieService = uidsCookieService;
        this.optoutRedirectUrl = optoutRedirectUrl;
        this.optoutUrl = optoutUrl;
        this.optinUrl = optinUrl;
    }

    public static OptoutHandler create(ApplicationConfig config, GoogleRecaptchaVerifier googleRecaptchaVerifier,
                                       UidsCookieService uidsCookieService) {
        Objects.requireNonNull(config);
        Objects.requireNonNull(googleRecaptchaVerifier);
        Objects.requireNonNull(uidsCookieService);

        return new OptoutHandler(googleRecaptchaVerifier, uidsCookieService, getOptoutRedirectUrl(config),
                validateUrl(config.getString("host_cookie.opt_out_url")),
                validateUrl(config.getString("host_cookie.opt_in_url")));
    }

    public void optout(RoutingContext context) {
        Objects.requireNonNull(context);

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
                .putHeader(HttpHeaders.LOCATION, optoutRedirectUrl)
                .setStatusCode(301)
                .end();
    }

    private void sendUnauthorized(RoutingContext context, Throwable cause) {
        logger.warn("Opt Out failed optout", cause);
        context.response()
                .setStatusCode(401)
                .end();
    }

    private void sendResponse(RoutingContext context, Cookie cookie, String url) {
        context.addCookie(cookie)
                .response()
                .putHeader(HttpHeaders.LOCATION, url)
                .setStatusCode(301)
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

    private static String getOptoutRedirectUrl(ApplicationConfig config) {
        try {
            final URL externalUrl = new URL(config.getString("external_url"));
            return new URL(externalUrl.toExternalForm() + "/static/optout.html").toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Could not get optout redirect url", e);
        }
    }

    private static String validateUrl(String url) {
        try {
            return new URL(url).toString();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException(String.format("Could not get url from string: %s", url), e);
        }
    }
}
