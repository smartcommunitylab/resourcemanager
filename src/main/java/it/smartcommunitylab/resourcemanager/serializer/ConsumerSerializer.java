package it.smartcommunitylab.resourcemanager.serializer;

import java.io.IOException;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;

import it.smartcommunitylab.resourcemanager.dto.ConsumerDTO;

public class ConsumerSerializer extends StdSerializer<ConsumerDTO> {

    private static final long serialVersionUID = 2765900697671425958L;

    public ConsumerSerializer() {
        this(null);
    }

    public ConsumerSerializer(Class<ConsumerDTO> t) {
        super(t);
    }

    @Override
    public void serialize(
            ConsumerDTO consumer, JsonGenerator jgen, SerializerProvider provider)
            throws IOException, JsonProcessingException {

        jgen.writeStartObject();
        jgen.writeNumberField("id", consumer.getId());

        jgen.writeStringField("type", consumer.getType());
        jgen.writeStringField("consumer", consumer.getConsumer());

        jgen.writeStringField("spaceId", consumer.getSpaceId());
        jgen.writeStringField("userId", consumer.getUserId());

        jgen.writeStringField("url", consumer.getUrl());
        jgen.writeStringField("status", consumer.getStatus());

        // write properties json
        jgen.writeFieldName("properties");
        jgen.writeRawValue(consumer.getProperties());

        // write tags as json array
        jgen.writeFieldName("tags");
        jgen.writeStartArray();
        for (String tag : consumer.tags) {
            jgen.writeString(tag);
        }
        jgen.writeEndArray();

        // close
        jgen.writeEndObject();
    }
}
