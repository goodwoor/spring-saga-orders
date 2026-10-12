package saga.entity;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class TopicConverter implements AttributeConverter<Topic, String> {

    @Override
    public String convertToDatabaseColumn(Topic value) {
        return value == null ? null : value.getCode();
    }

    @Override
    public Topic convertToEntityAttribute(String dbValue) {
        return dbValue == null ? null : Topic.fromCode(dbValue);
    }
}
