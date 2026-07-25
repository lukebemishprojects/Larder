package dev.lukebemish.larder.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.javalin.openapi.OpenApiIgnore;
import io.javalin.openapi.OpenApiName;
import org.jspecify.annotations.Nullable;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.OptionalLong;

public record IndexEntry(String name, @JsonInclude(JsonInclude.Include.NON_ABSENT) Optional<LocalDateTime> datetime, @JsonInclude(JsonInclude.Include.NON_ABSENT) Optional<Long> size, @JsonProperty("isdirectory") @OpenApiName("isdirectory") boolean isDirectory) {
    @OpenApiIgnore
    @JsonIgnore
    String humanSize() {
        return size.map(IndexEntry::humanSize).orElse("");
    }

    @OpenApiIgnore
    @JsonIgnore
    String humanDatetime() {
        return datetime.map(DATE_TIME_FORMATTER::format).orElse("");
    }

    private static final List<String> HUMAN_UNITS = List.of(
        "K", "M", "G", "T", "P", "E", "Z", "Y"
    );

    private static String humanSize(long byteSize) {
        var threshold = 1000;

        if (Math.abs(byteSize) < threshold) {
            return Long.toString(byteSize);
        }

        var ofUnit = (double) byteSize;
        var u = 0;

        do {
            ofUnit /= threshold;
            u++;
        } while (Math.round(ofUnit * 10) / 10 >= threshold && u != HUMAN_UNITS.size());

        return DECIMAL_FORMATTER.format(ofUnit)+HUMAN_UNITS.get(u - 1);
    }

    private static final DecimalFormat DECIMAL_FORMATTER = new DecimalFormat("0.0", new DecimalFormatSymbols(Locale.ROOT));
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
}
