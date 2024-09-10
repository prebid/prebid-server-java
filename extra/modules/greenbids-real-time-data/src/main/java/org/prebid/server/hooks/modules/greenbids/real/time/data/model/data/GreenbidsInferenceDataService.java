package org.prebid.server.hooks.modules.greenbids.real.time.data.model.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Data;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import io.netty.util.internal.StringUtil;
import lombok.Value;
import org.apache.commons.lang3.StringUtils;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;
import org.springframework.stereotype.Service;

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
@Service
public class GreenbidsInferenceDataService {

    private final DatabaseReader dbReader;

    private final ObjectMapper mapper;

    public GreenbidsInferenceDataService(DatabaseReader dbReader, ObjectMapper mapper) {
        this.dbReader = dbReader;
        this.mapper = mapper;
    }

    public List<ThrottlingMessage> extractThrottlingMessagesFromBidRequest(BidRequest bidRequest) {
        final GreenbidsUserAgent userAgent = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getUa)
                .map(GreenbidsUserAgent::new)
                .orElse(null);

        return extractThrottlingMessages(
                bidRequest,
                userAgent,
                dbReader,
                mapper);
    }

    private static List<ThrottlingMessage> extractThrottlingMessages(
            BidRequest bidRequest,
            GreenbidsUserAgent greenbidsUserAgent,
            DatabaseReader dbReader,
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
                        dbReader,
                        mapper).stream())
                .collect(Collectors.toList());
    }

    private static List<ThrottlingMessage> extractMessagesForImp(
            Imp imp,
            BidRequest bidRequest,
            GreenbidsUserAgent greenbidsUserAgent,
            String hostname,
            Integer hourBucket,
            Integer minuteQuadrant,
            DatabaseReader dbReader,
            ObjectMapper mapper) {

        final String impId = imp.getId();
        final ObjectNode impExt = imp.getExt();
        final JsonNode bidderNode = extImpPrebid(impExt.get("prebid"), mapper).getBidder();
        final String ip = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getIp)
                .orElse(null);
        final String countryFromIp = getCountry(ip, dbReader);
        return createThrottlingMessages(
                bidderNode,
                impId,
                greenbidsUserAgent,
                countryFromIp,
                hostname,
                hourBucket,
                minuteQuadrant);
    }

    private static String getCountry(String ip, DatabaseReader dbReader) {
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

    private static List<ThrottlingMessage> createThrottlingMessages(
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

    private static ThrottlingMessage buildThrottlingMessage(
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
                .bidder(Optional.ofNullable(bidderName).orElse(StringUtils.EMPTY))
                .adUnitCode(Optional.ofNullable(impId).orElse(StringUtils.EMPTY))
                .country(Optional.ofNullable(countryFromIp).orElse(StringUtils.EMPTY))
                .hostname(Optional.ofNullable(hostname).orElse(StringUtils.EMPTY))
                .device(device)
                .hourBucket(Optional.ofNullable(hourBucket).map(String::valueOf).orElse(StringUtils.EMPTY))
                .minuteQuadrant(Optional.ofNullable(minuteQuadrant).map(String::valueOf).orElse(StringUtils.EMPTY))
                .build();
    }

    private static ExtImpPrebid extImpPrebid(JsonNode extImpPrebid, ObjectMapper mapper) {
        try {
            return mapper.treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
    }
}
