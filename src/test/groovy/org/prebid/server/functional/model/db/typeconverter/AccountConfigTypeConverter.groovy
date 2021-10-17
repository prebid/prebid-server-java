package org.prebid.server.functional.model.db.typeconverter

import javax.persistence.AttributeConverter
import org.prebid.server.functional.model.config.AccountConfig
import org.prebid.server.functional.testcontainers.Dependencies

class AccountConfigTypeConverter implements AttributeConverter<AccountConfig, String> {

    @Override
    String convertToDatabaseColumn(AccountConfig accountConfig) {
        accountConfig ? Dependencies.objectMapperWrapper.encode(accountConfig) : null
    }

    @Override
    AccountConfig convertToEntityAttribute(String value) {
        value ? Dependencies.objectMapperWrapper.decode(value, AccountConfig) : null
    }
}
