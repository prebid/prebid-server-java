package org.prebid.server.functional.model.response.setuid

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer

import java.time.LocalDate
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class ZonedDateTimeDeserializer extends JsonDeserializer<ZonedDateTime> {

    @Override
    ZonedDateTime deserialize(JsonParser jsonParser, DeserializationContext deserializationContext) throws IOException {
        LocalDate localDate = LocalDate.parse(jsonParser.getText(), DateTimeFormatter.ISO_ZONED_DATE_TIME)

        localDate.atStartOfDay(ZoneOffset.UTC)
    }
}
