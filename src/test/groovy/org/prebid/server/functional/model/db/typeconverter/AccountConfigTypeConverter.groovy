package org.prebid.server.functional.model.db.typeconverter

import jakarta.persistence.AttributeConverter
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.util.ObjectMapperWrapper

class AccountConfigTypeConverter implements AttributeConverter<AccountConfig, String>, ObjectMapperWrapper {

    @Override
    String convertToDatabaseColumn(AccountConfig accountConfig) {
        accountConfig ? encode(accountConfig) : null
    }

    @Override
    AccountConfig convertToEntityAttribute(String value) {
        value ? decode(value, AccountConfig) : null
    }
}
