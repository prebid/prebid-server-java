package org.prebid.server.hooks.modules.greenbids.real.time.data.model.data;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.iab.openrtb.request.BidRequest;
import com.iab.openrtb.request.Device;
import com.iab.openrtb.request.Imp;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CountryResponse;
import com.maxmind.geoip2.record.Country;
import org.prebid.server.exception.PreBidException;
import org.prebid.server.json.JacksonMapper;
import org.prebid.server.proto.openrtb.ext.request.ExtImpPrebid;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

public class GreenbidsInferenceData {

    JacksonMapper jacksonMapper;

    File database;

    public List<ThrottlingMessage> throttlingMessages;

    public String[][] throttlingInferenceRows;

    public GreenbidsInferenceData(
            JacksonMapper jacksonMapper,
            File database) {
        this.jacksonMapper = jacksonMapper;
        this.database = database;
    }

    public void prepareData(BidRequest bidRequest) {
        final String userAgent = Optional.ofNullable(bidRequest.getDevice())
                .map(Device::getUa)
                .orElse(null);
        final GreenbidsUserAgent greenbidsUserAgent = new GreenbidsUserAgent(userAgent);

        throttlingMessages = extractThrottlingMessages(
                bidRequest,
                greenbidsUserAgent);

        throttlingInferenceRows = convertToArray(throttlingMessages);

        if (isAnyFeatureNull(throttlingInferenceRows)) {
            throw new PreBidException("Null features still exist in inference row after fillna");
        }
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
                .flatMap(imp -> {
                    final String impId = imp.getId();
                    final ObjectNode impExt = imp.getExt();
                    final JsonNode bidderNode = extImpPrebid(impExt.get("prebid")).getBidder();

                    final String ipv4 = Optional.ofNullable(bidRequest.getDevice())
                            .map(Device::getIp)
                            .orElse(null);
                    final String countryFromIp;
                    try {
                        countryFromIp = getCountry(ipv4);
                    } catch (IOException | GeoIp2Exception e) {
                        throw new PreBidException("Failed to get country for IP", e);
                    }

                    final List<ThrottlingMessage> throttlingImpMessages = new ArrayList<>();
                    if (bidderNode.isObject()) {
                        final ObjectNode bidders = (ObjectNode) bidderNode;
                        final Iterator<String> fieldNames = bidders.fieldNames();
                        while (fieldNames.hasNext()) {
                            final String bidderName = fieldNames.next();
                            throttlingImpMessages.add(
                                    ThrottlingMessage.builder()
                                            .browser(greenbidsUserAgent.getBrowser())
                                            .bidder(bidderName)
                                            .adUnitCode(impId)
                                            .country(countryFromIp)
                                            .hostname(hostname)
                                            .device(greenbidsUserAgent.getDevice())
                                            .hourBucket(hourBucket.toString())
                                            .minuteQuadrant(minuteQuadrant.toString())
                                            .build());
                        }
                    }
                    return throttlingImpMessages.stream();
                })
                .collect(Collectors.toList());
    }

    private ExtImpPrebid extImpPrebid(JsonNode extImpPrebid) {
        try {
            return jacksonMapper.mapper().treeToValue(extImpPrebid, ExtImpPrebid.class);
        } catch (JsonProcessingException e) {
            throw new PreBidException("Error decoding imp.ext.prebid: " + e.getMessage(), e);
        }
    }

    private String getCountry(String ip) throws IOException, GeoIp2Exception {
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

    private String[][] convertToArray(List<ThrottlingMessage> messages) {
        return messages.stream()
                .map(message -> new String[]{
                        Optional.ofNullable(message.getBrowser()).orElse(""),
                        Optional.ofNullable(message.getBidder()).orElse(""),
                        Optional.ofNullable(message.getAdUnitCode()).orElse(""),
                        Optional.ofNullable(message.getCountry()).orElse(""),
                        Optional.ofNullable(message.getHostname()).orElse(""),
                        Optional.ofNullable(message.getDevice()).orElse(""),
                        Optional.ofNullable(message.getHourBucket()).orElse(""),
                        Optional.ofNullable(message.getMinuteQuadrant()).orElse("")})
                .toArray(String[][]::new);
    }

    private Boolean isAnyFeatureNull(String[][] throttlingInferenceRow) {
        return Arrays.stream(throttlingInferenceRow)
                .flatMap(Arrays::stream)
                .anyMatch(Objects::isNull);
    }
}
