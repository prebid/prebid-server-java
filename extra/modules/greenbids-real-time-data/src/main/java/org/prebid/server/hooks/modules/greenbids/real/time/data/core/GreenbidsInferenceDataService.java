package org.prebid.server.hooks.modules.greenbids.real.time.data.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.hooks.modules.greenbids.real.time.data.model.data.ThrottlingMessage;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;

import java.io.IOException;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class GreenbidsInferenceDataService {

    private final DatabaseReader dbReader;

    private final ObjectMapper mapper;

    public GreenbidsInferenceDataService(DatabaseReader dbReader, ObjectMapper mapper) {
        this.dbReader = Objects.requireNonNull(dbReader);
        this.mapper = Objects.requireNonNull(mapper);
    }

    public List<ThrottlingMessage> extractThrottlingMessagesFromBidRequest(BidRequest bidRequest) {
        final GreenbidsUserAgent userAgent = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getUa)
                .map(GreenbidsUserAgent::new)
                .orElse(null);

        return extractThrottlingMessages(bidRequest, userAgent);
    }

    private List<ThrottlingMessage> extractThrottlingMessages(
            BidRequest bidRequest,
            GreenbidsUserAgent greenbidsUserAgent) {

        final ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        final Integer hourBucket = timestamp.getHour();
        final Integer minuteQuadrant = (timestamp.getMinute() / 15) + 1;

        final String hostname = bidRequest.getSite().getDomain();
        final List<Imp> imps = bidRequest.getImp();

        return imps.stream()
                .map(imp -> extractMessagesForImp(
                        imp,
                        bidRequest,
                        greenbidsUserAgent,
                        hostname,
                        hourBucket,
                        minuteQuadrant))
                .flatMap(Collection::stream)
                .collect(Collectors.toList());
    }

    private List<ThrottlingMessage> extractMessagesForImp(
            Imp imp,
            BidRequest bidRequest,
            GreenbidsUserAgent greenbidsUserAgent,
            String hostname,
            Integer hourBucket,
            Integer minuteQuadrant) {

        final String impId = imp.getId();
        final ObjectNode impExt = imp.getExt();
        final JsonNode bidderNode = extImpPrebid(impExt.get("prebid")).getBidder();
        final String ip = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getIp)
                .orElse(null);
        final String countryFromIp = getCountry(ip);
        return createThrottlingMessages(
                bidderNode,
                impId,
                greenbidsUserAgent,
                countryFromIp,
                hostname,
                hourBucket,
                minuteQuadrant);
    }

    private String getCountry(String ip) {
        if (ip == null) {
            return null;
        }

        try {
            final InetAddress inetAddress = InetAddress.getByName(ip);
            final CountryResponse response = dbReader.country(inetAddress);
            final Country country = response.getCountry();
            return country.getName();
        } catch (IOException | GeoIp2Exception e) {
            throw new PreBidException("Failed to fetch country from geoLite DB", e);
        }
    }

    private List<ThrottlingMessage> createThrottlingMessages(
            JsonNode bidderNode,
            String impId,
            GreenbidsUserAgent greenbidsUserAgent,
            String countryFromIp,
            String hostname,
            Integer hourBucket,
            Integer minuteQuadrant) {

        final List<ThrottlingMessage> throttlingImpMessages = new ArrayList<>();

        if (!bidderNode.isObject()) {
            return throttlingImpMessages;
        }

        final ObjectNode bidders = (ObjectNode) bidderNode;
        final Iterator<String> fieldNames = bidders.fieldNames();
        while (fieldNames.hasNext()) {
            final String bidderName = fieldNames.next();
            throttlingImpMessages.add(buildThrottlingMessage(
                    bidderName,
                    impId,
                    greenbidsUserAgent,
                    countryFromIp,
                    hostname,
                    hourBucket,
                    minuteQuadrant));
        }

        return throttlingImpMessages;
    }

    private ThrottlingMessage buildThrottlingMessage(
            String bidderName,
            String impId,
            GreenbidsUserAgent greenbidsUserAgent,
            String countryFromIp,
            String hostname,
            Integer hourBucket,
            Integer minuteQuadrant) {

        final String browser = Optional.ofNullable(greenbidsUserAgent)
                .map(GreenbidsUserAgent::getBrowser)
                .orElse(StringUtils.EMPTY);

        final String device = Optional.ofNullable(greenbidsUserAgent)
                .map(GreenbidsUserAgent::getDevice)
                .orElse(StringUtils.EMPTY);

        return ThrottlingMessage.builder()
                .browser(browser)
                .bidder(StringUtils.defaultString(bidderName))
                .adUnitCode(StringUtils.defaultString(impId))
                .country(StringUtils.defaultString(countryFromIp))
                .hostname(StringUtils.defaultString(hostname))
                .device(device)
                .hourBucket(StringUtils.defaultString(hourBucket.toString()))
                .minuteQuadrant(StringUtils.defaultString(minuteQuadrant.toString()))
                .build();
    }

    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return mapper.treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
    }
}
