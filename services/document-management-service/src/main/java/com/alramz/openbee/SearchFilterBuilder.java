package com.alramz.openbee;

import com.alramz.service.CatalogMetadata;
import com.alramz.service.ValidatedMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the Advanced Search body (brief 8.4): always FILING_CATEGORY EQUALS and TYPE IN (documents only), then
 * one METADATA filter per entry. EQUALS is not applicable to metadata, so text/list/sublist use IN, numbers use
 * COMPARISON "=", dates a one-day DATE_RANGE ({@code d/m/Y}, end exclusive), booleans BOOLEAN.
 */
@Component
@RequiredArgsConstructor
public class SearchFilterBuilder {

    static final List<Object> DOCUMENT_TYPES = List.of("PDF", "OFFICE", "IMAGE", "AUDIO", "OTHERS");
    static final DateTimeFormatter SEARCH_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final MetadataEncoder metadataEncoder;

    public OpenBeeSearchRequest build(long filingCategoryId, List<ValidatedMetadata> metadata, int page, int pageSize) {
        List<OpenBeeSearchRequest.Filter> filters = new ArrayList<>();
        filters.add(new OpenBeeSearchRequest.Filter("FILING_CATEGORY", null,
                OpenBeeSearchRequest.Clause.equalsTo(filingCategoryId)));
        filters.add(new OpenBeeSearchRequest.Filter("TYPE", null, OpenBeeSearchRequest.Clause.in(DOCUMENT_TYPES)));
        for (ValidatedMetadata entry : metadata) {
            filters.add(new OpenBeeSearchRequest.Filter("METADATA", entry.definition().id(), clause(entry)));
        }
        return new OpenBeeSearchRequest(filters,
                new OpenBeeSearchRequest.PaginationSpec(pageSize, page),
                List.of(new OpenBeeSearchRequest.Sort("CREATION_DATE", "DESC")));
    }

    private OpenBeeSearchRequest.Clause clause(ValidatedMetadata entry) {
        Object first = entry.normalisedValues().get(0);
        return switch (entry.definition().group()) {
            case INTEGER, DECIMAL -> OpenBeeSearchRequest.Clause.comparison("=", first);
            case DATE -> {
                LocalDate date = (LocalDate) first;
                yield OpenBeeSearchRequest.Clause.dateRange(SEARCH_DATE.format(date), SEARCH_DATE.format(date.plusDays(1)));
            }
            case BOOLEAN -> OpenBeeSearchRequest.Clause.bool((Boolean) first);
            default -> OpenBeeSearchRequest.Clause.in(entry.normalisedValues().stream()
                    .map(v -> v instanceof CatalogMetadata.ListValue lv ? (Object) metadataEncoder.listValue(lv) : v)
                    .toList());
        };
    }
}
