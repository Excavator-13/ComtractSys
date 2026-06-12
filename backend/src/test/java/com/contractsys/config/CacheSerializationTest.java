package com.contractsys.config;

import com.contractsys.contract.dto.ContractTemplateView;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CacheSerializationTest {
    private final GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();

    @Test
    void statisticsCacheValueRoundTripsWithRedisJsonSerializer() {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("total", 1L);
        stats.put("draft", 1L);
        stats.put("assigned", 0L);
        stats.put("signed", 0L);
        stats.put("rejected", 0L);
        stats.put("pendingTasks", 2L);

        Object restored = roundTrip(stats);

        assertThat(restored).isInstanceOf(Map.class);
        assertThat(((Number) ((Map<?, ?>) restored).get("total")).longValue()).isEqualTo(1L);
    }

    @Test
    void monthlyStatisticsCacheValueUsesStableCollectionTypes() {
        List<Map<String, Object>> monthly = new ArrayList<>();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("month", "2026-06");
        entry.put("count", 3L);
        monthly.add(entry);

        byte[] bytes = serializer.serialize(monthly);
        String json = new String(bytes, StandardCharsets.UTF_8);
        Object restored = serializer.deserialize(bytes);

        assertThat(json).doesNotContain("ImmutableCollections");
        assertThat(restored).isInstanceOf(List.class);
        assertThat((List<?>) restored).hasSize(1);
    }

    @Test
    void templateCacheValueUsesStableCollectionTypes() {
        List<ContractTemplateView> templates = new ArrayList<>();
        templates.add(new ContractTemplateView(1L, "模板", "说明", "正文", "模板.pdf", 128L, "application/pdf", "ROLE_OPERATOR", true));

        byte[] bytes = serializer.serialize(templates);
        String json = new String(bytes, StandardCharsets.UTF_8);
        Object restored = serializer.deserialize(bytes);

        assertThat(json).doesNotContain("ImmutableCollections");
        assertThat(restored).isInstanceOf(List.class);
        assertThat((List<?>) restored).hasSize(1);
    }

    private Object roundTrip(Object value) {
        byte[] bytes = serializer.serialize(value);
        String json = new String(bytes, StandardCharsets.UTF_8);
        assertThat(json).doesNotContain("ImmutableCollections");
        return serializer.deserialize(bytes);
    }
}
