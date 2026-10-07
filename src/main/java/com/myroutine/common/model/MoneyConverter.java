package com.myroutine.common.model;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class MoneyConverter implements AttributeConverter<Money, Long> {
    @Override
    public Long convertToDatabaseColumn(Money attribute) {
        return attribute == null ? null : attribute.amount();
    }

    @Override
    public Money convertToEntityAttribute(Long dbData) {
        return dbData == null ? null : Money.of(dbData);
    }
}
