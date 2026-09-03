package com.roktolink.donor;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Maps {@link BloodGroup} to the symbol stored in {@code varchar(3)} columns, so
 * the database holds {@code B+} rather than {@code B_POSITIVE}.
 */
@Converter(autoApply = true)
public class BloodGroupConverter implements AttributeConverter<BloodGroup, String> {

    @Override
    public String convertToDatabaseColumn(BloodGroup attribute) {
        return attribute == null ? null : attribute.getSymbol();
    }

    @Override
    public BloodGroup convertToEntityAttribute(String dbData) {
        return dbData == null ? null : BloodGroup.fromSymbol(dbData);
    }
}
