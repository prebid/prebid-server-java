package org.prebid.server.functional.model.db.typeconverter

import org.prebid.server.functional.model.AccountStatus

import javax.persistence.AttributeConverter

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
