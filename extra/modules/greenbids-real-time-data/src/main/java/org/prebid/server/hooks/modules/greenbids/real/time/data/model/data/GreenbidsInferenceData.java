package org.prebid.server.hooks.modules.greenbids.real.time.data.model.data;

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
import lombok.Value;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Value(staticConstructor = "of")
public class GreenbidsInferenceData {

    List<ThrottlingMessage> throttlingMessages;

    public static GreenbidsInferenceData of(
            BidRequest bidRequest,
            File database,
            ObjectMapper mapper) {

        final GreenbidsUserAgent userAgent = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getUa)
                .map(GreenbidsUserAgent::new)
                .orElse(null);

        final List<ThrottlingMessage> throttlingMessages = extractThrottlingMessages(
                bidRequest,
                userAgent,
                database,
                mapper);

        return of(throttlingMessages);
    }

    private static List<ThrottlingMessage> extractThrottlingMessages(
            BidRequest bidRequest,
            GreenbidsUserAgent greenbidsUserAgent,
            File database,
            ObjectMapper mapper) {

        final ZonedDateTime timestamp = ZonedDateTime.now(ZoneId.of("UTC"));
        final Integer hourBucket = timestamp.getHour();
        final Integer minuteQuadrant = (timestamp.getMinute() / 15) + 1;

        final String hostname = bidRequest.getSite().getDomain();
        final List<Imp> imps = bidRequest.getImp();

        return imps.stream()
                .flatMap(imp -> extractMessagesForImp(
                        imp,
                        bidRequest,
                        greenbidsUserAgent,
                        hostname,
                        hourBucket,
                        minuteQuadrant,
                        database,
                        mapper))
                .collect(Collectors.toList());
    }

    private static Stream<ThrottlingMessage> extractMessagesForImp(
            Imp imp,
            BidRequest bidRequest,
            GreenbidsUserAgent greenbidsUserAgent,
            String hostname,
            Integer hourBucket,
            Integer minuteQuadrant,
            File database,
            ObjectMapper mapper) {

        final String impId = imp.getId();
        final ObjectNode impExt = imp.getExt();
        final JsonNode bidderNode = extImpPrebid(impExt.get("prebid"), mapper).getBidder();
        final String ip = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getIp)
                .orElse(null);
        final String countryFromIp = getCountrySafely(ip, database);
        return createThrottlingMessages(
                bidderNode,
                impId,
                greenbidsUserAgent,
                countryFromIp,
                hostname,
                hourBucket,
                minuteQuadrant).stream();
    }

    private static String getCountrySafely(String ip, File database) {
        try {
            return getCountry(ip, database);
        } catch (IOException | GeoIp2Exception e) {
            throw new PreBidException("Failed to get country for IP", e);
        }
    }

    private static List<ThrottlingMessage> createThrottlingMessages(
            JsonNode bidderNode,
            String impId,
            GreenbidsUserAgent greenbidsUserAgent,
            String countryFromIp,
            String hostname,
            Integer hourBucket,
            Integer minuteQuadrant) {

        final List<ThrottlingMessage> throttlingImpMessages = new ArrayList<>();
        if (bidderNode.isObject()) {
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
        }
        return throttlingImpMessages;
    }

    private static ThrottlingMessage buildThrottlingMessage(
            String bidderName,
            String impId,
            GreenbidsUserAgent greenbidsUserAgent,
            String countryFromIp,
            String hostname,
            Integer hourBucket,
            Integer minuteQuadrant) {
        return ThrottlingMessage.builder()
                .browser(Optional.ofNullable(greenbidsUserAgent)
                        .map(GreenbidsUserAgent::getBrowser)
                        .orElse(""))
                .bidder(Optional.ofNullable(bidderName).orElse(""))
                .adUnitCode(Optional.ofNullable(impId).orElse(""))
                .country(Optional.ofNullable(countryFromIp).orElse(""))
                .hostname(Optional.ofNullable(hostname).orElse(""))
                .device(Optional.ofNullable(greenbidsUserAgent)
                        .map(GreenbidsUserAgent::getDevice)
                        .orElse(""))
                .hourBucket(Optional.ofNullable(hourBucket).map(String::valueOf).orElse(""))
                .minuteQuadrant(Optional.ofNullable(minuteQuadrant).map(String::valueOf).orElse(""))
                .build();
    }

    private static ExtImpPrebid extImpPrebid(JsonNode extImpPrebid, ObjectMapper mapper) {
        try {
            return mapper.treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
    }

    private static String getCountry(String ip, File database) throws IOException, GeoIp2Exception {
        return Optional.ofNullable(ip)
                .map(ipAddress -> {
                    try {
                        final DatabaseReader dbReader = new DatabaseReader.Builder(database).build();
                        final InetAddress inetAddress = InetAddress.getByName(ipAddress);
                        final CountryResponse response = dbReader.country(inetAddress);
                        final Country country = response.getCountry();
                        return country.getName();
                    } catch (IOException | GeoIp2Exception e) {
                        throw new PreBidException("Failed to fetch country from geoLite DB", e);
                    }
                }).orElse(null);
    }
}
