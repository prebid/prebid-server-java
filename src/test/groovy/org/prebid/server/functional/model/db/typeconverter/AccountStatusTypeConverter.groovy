package org.prebid.server.functional.model.db.typeconverter

import javax.persistence.AttributeConverter
import org.prebid.server.functional.model.AccountStatus

class AccountStatusTypeConverter implements AttributeConverter<AccountStatus, String> {

    @Override
    String convertToDatabaseColumn(AccountStatus accountStatus) {
        accountStatus
    }

    @Override
    AccountStatus convertToEntityAttribute(String value) {
        value ? AccountStatus.forValue(value) : null
    }
}
