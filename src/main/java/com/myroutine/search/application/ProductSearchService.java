package com.myroutine.search.application;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.myroutine.common.error.BusinessException;
import com.myroutine.common.error.CommonErrorCode;
import com.myroutine.common.web.CursorPage;
import com.myroutine.product.api.ProductApi;
import com.myroutine.product.api.ProductSummaryView;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductSearchService {

    private final ElasticsearchClient client;
    private final ObjectMapper objectMapper;
    private final ProductApi productApi;

    public CursorPage<ProductSearchResult> search(String q, String category, String cursor, int size) {
        List<FieldValue> after = decodeCursor(cursor);

        List<Query> filters = new ArrayList<>();
        filters.add(Query.of(f -> f.term(t -> t.field("status").value("ON_SALE"))));
        if (category != null) {
            filters.add(Query.of(f -> f.term(t -> t.field("category").value(category))));
        }

        String nextCursor = null;
        List<UUID> productIds = new ArrayList<>();

        try {
            SearchResponse<Map> response = client.search(b -> {
                b.index("products")
                        .size(size + 1)
                        .query(query -> query.bool(bool -> bool
                                .must(must -> must.multiMatch(mm -> mm.query(q).fields("name^3", "description")))
                                .filter(filters)))
                        .sort(s -> s.score(o -> o.order(SortOrder.Desc)))
                        .sort(s -> s.field(f -> f.field("productId").order(SortOrder.Asc)))
                        .source(src -> src.filter(f -> f.includes("productId")));
                if (after != null) {
                    b.searchAfter(after);
                }
                return b;
            }, Map.class);

            List<Hit<Map>> hits = response.hits().hits();

            if (hits.size() > size) {
                hits = hits.subList(0, size);
                Hit<Map> hit = hits.getLast();
                List<FieldValue> sort = hit.sort(); // [0.22232392, aaaaaaaa-0000-...]
                List<Object> values = List.of(sort.get(0).doubleValue(), sort.get(1).stringValue());
                nextCursor = Base64.getUrlEncoder().withoutPadding().encodeToString(objectMapper.writeValueAsBytes(values));
            }

            hits.stream()
                    .map(hit -> UUID.fromString(String.valueOf(hit.source().get("productId"))))
                    .forEach(productIds::add);

        } catch (IOException | ElasticsearchException e) {
            log.error("Failed to search products", e);
            throw new BusinessException(SearchErrorCode.SEARCH_UNAVAILABLE);
        }

        List<ProductSummaryView> views = productApi.getOnSaleSummaries(productIds);

        Map<UUID, ProductSummaryView> viewById = views.stream()
                .collect(Collectors.toMap(ProductSummaryView::productId, Function.identity()));

        List<ProductSearchResult> items = productIds.stream()
                .map(viewById::get)
                .filter(Objects::nonNull)
                .map(ProductSearchResult::from)
                .toList();

        return new CursorPage<>(items, nextCursor);
    }

    public List<String> autocomplete(String q) {
        try {
            SearchResponse<Map> response = client.search(b -> b
                    .index("products")
                    .size(10)
                    .query(query -> query.bool(bool -> bool
                            .must(must -> must.match(m -> m.field("name.autocomplete").query(q)))
                            .filter(f -> f.term(t -> t.field("status").value("ON_SALE")))))
                    .sort(s -> s.score(o -> o.order(SortOrder.Desc)))
                    .sort(s -> s.field(f -> f.field("name.keyword").order(SortOrder.Asc)))
                    .collapse(c -> c.field("name.keyword"))
                    .source(src -> src.filter(f -> f.includes("name"))), Map.class);

            return response.hits().hits().stream()
                    .map(hit -> (String) hit.source().get("name"))
                    .toList();
        } catch (IOException | ElasticsearchException e) {
            log.error("Failed to autocomplete products", e);
            throw new BusinessException(SearchErrorCode.SEARCH_UNAVAILABLE);
        }
    }

    private List<FieldValue> decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(Base64.getUrlDecoder().decode(cursor));
            if (!node.isArray() || node.size() != 2 || !node.get(0).isNumber() || !node.get(1).isString()) {
                throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
            }
            return List.of(FieldValue.of(node.get(0).doubleValue()), FieldValue.of(node.get(1).stringValue()));
        } catch (IllegalArgumentException | JacksonException e) {
            throw new BusinessException(CommonErrorCode.INVALID_REQUEST);
        }
    }
}
