package com.myroutine.search.infrastructure;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.ElasticsearchException;
import co.elastic.clients.elasticsearch._types.VersionType;
import com.myroutine.product.api.event.ProductUpsertedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.StringReader;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductIndexer {

    private final ElasticsearchClient client;
    private final ProductIndexDefinition productIndexDefinition;
    private final Clock clock;

    private volatile boolean ready;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            ensureIndex();
        } catch (RuntimeException e) {
            log.warn("Failed to ensure product index on startup.", e);
        }
    }

    public synchronized void ensureIndex() {
        if (ready) return;

        try {
            boolean exists = client.indices().existsAlias(b -> b.name("products")).value();
            if (exists) {
                ready = true;
                return;
            }

            String json = productIndexDefinition.json();
            String indexName = "products-" + LocalDateTime.now(clock).format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
            client.indices().create(b -> b
                    .index(indexName)
                    .withJson(new StringReader(json))
                    .aliases("products", a -> a));
            ready = true;
        } catch (IOException e) {
            throw new RuntimeException("Failed to ensure product index", e);
        }
    }

    public void upsert(ProductUpsertedEvent event, String indexOrAlias) {
        ensureIndex();

        Map<String, Object> document = Map.of(
                "productId", event.productId().toString(),
                "shopId", event.shopId().toString(),
                "name", event.name(),
                "description", event.description(),
                "category", event.category(),
                "price", event.price(),
                "status", event.status());

        try {
            client.index(b -> b
                    .index(indexOrAlias)
                    .id(event.productId().toString())
                    .version(event.version())
                    .versionType(VersionType.External)
                    .document(document));
        } catch (ElasticsearchException ex) {
            if (ex.status() != 409) {
                throw ex;
            }
            // 409는 "이미 같거나 더 최신"
            log.debug("Ignored stale product event. productId={} version={}", event.productId(), event.version());
        } catch (IOException ex) {
            throw new RuntimeException("Failed to index product. productId=" + event.productId(), ex);
        }
    }
}
