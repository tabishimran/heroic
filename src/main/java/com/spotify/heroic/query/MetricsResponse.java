package com.spotify.heroic.query;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import lombok.Getter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.spotify.heroic.backend.RowStatistics;
import com.spotify.heroic.backend.kairosdb.DataPoint;

public class MetricsResponse {
    public static class ResultSerializer extends JsonSerializer<Object> {
        @SuppressWarnings("unchecked")
        @Override
        public void serialize(Object value, JsonGenerator jgen,
                SerializerProvider provider) throws IOException,
                JsonProcessingException {

            final Map<Map<String, String>, List<DataPoint>> result = (Map<Map<String, String>, List<DataPoint>>) value;

            jgen.writeStartArray();

            for (Map.Entry<Map<String, String>, List<DataPoint>> entry : result
                    .entrySet()) {
                final List<DataPoint> datapoints = entry.getValue();

                jgen.writeStartObject();
                jgen.writeFieldName("tags");
                jgen.writeObject(entry.getKey());

                jgen.writeFieldName("values");
                jgen.writeStartArray();

                for (final DataPoint d : datapoints) {
                    jgen.writeStartArray();
                    jgen.writeNumber(d.getTimestamp());
                    jgen.writeNumber(d.getValue());
                    jgen.writeEndArray();
                }

                jgen.writeEndArray();
                jgen.writeEndObject();
            }

            jgen.writeEndArray();
        }
    }

    @Getter
    @JsonSerialize(using = ResultSerializer.class)
    private final Map<Map<String, String>, List<DataPoint>> result;

    @Getter
    private final long sampleSize;

    @Getter
    private final long outOfBounds;

    @Getter
    private final RowStatistics rowStatistics;

    public MetricsResponse(
            final Map<Map<String, String>, List<DataPoint>> result,
            final long sampleSize, final long outOfBounds,
            final RowStatistics rowStatistics) {
        this.result = result;
        this.sampleSize = sampleSize;
        this.outOfBounds = outOfBounds;
        this.rowStatistics = rowStatistics;
    }
}