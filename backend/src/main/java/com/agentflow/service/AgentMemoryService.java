package com.agentflow.service;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class AgentMemoryService {

    private final List<MemoryRecord> memories = new CopyOnWriteArrayList<>();

    public Map<String, Object> write(String content, String memoryType, String scope,
                                     List<String> tags, String source, List<Double> embedding,
                                     String embeddingModel) {
        MemoryRecord record = new MemoryRecord(
                UUID.randomUUID().toString(),
                StringUtils.hasText(scope) ? scope : "workflow",
                StringUtils.hasText(memoryType) ? memoryType : "fact",
                content,
                tags == null ? List.of() : tags,
                source,
                embedding == null ? List.of() : embedding,
                embeddingModel,
                LocalDateTime.now()
        );
        memories.add(record);

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("memoryId", record.id());
        output.put("scope", record.scope());
        output.put("stored", true);
        return output;
    }

    public Map<String, Object> retrieve(String query, String scope, List<String> tags,
                                        List<Double> queryEmbedding, int topK) {
        List<Map<String, Object>> matches = memories.stream()
                .filter(memory -> !StringUtils.hasText(scope) || scope.equals(memory.scope()))
                .filter(memory -> tags == null || tags.isEmpty() || memory.tags().containsAll(tags))
                .map(memory -> toMatch(memory, query, queryEmbedding))
                .filter(match -> (double) match.get("score") > 0)
                .sorted(Comparator.comparingDouble((Map<String, Object> match) -> (double) match.get("score")).reversed())
                .limit(Math.max(1, topK))
                .toList();

        StringBuilder context = new StringBuilder();
        for (Map<String, Object> match : matches) {
            context.append("- ").append(match.get("content")).append("\n");
        }

        Map<String, Object> output = new LinkedHashMap<>();
        output.put("memories", matches);
        output.put("context", context.toString().trim());
        output.put("citations", matches.stream().map(match -> match.get("memoryId")).toList());
        return output;
    }

    private Map<String, Object> toMatch(MemoryRecord memory, String query, List<Double> queryEmbedding) {
        double score = cosine(queryEmbedding, memory.embedding());
        if (score == 0 && StringUtils.hasText(query) && memory.content().contains(query)) {
            score = 0.5;
        }
        Map<String, Object> match = new LinkedHashMap<>();
        match.put("memoryId", memory.id());
        match.put("scope", memory.scope());
        match.put("memoryType", memory.memoryType());
        match.put("content", memory.content());
        match.put("tags", memory.tags());
        match.put("source", memory.source());
        match.put("score", score);
        return match;
    }

    private double cosine(List<Double> left, List<Double> right) {
        if (left == null || right == null || left.isEmpty() || right.isEmpty()) {
            return 0;
        }
        int size = Math.min(left.size(), right.size());
        double dot = 0;
        double leftNorm = 0;
        double rightNorm = 0;
        for (int i = 0; i < size; i++) {
            double l = left.get(i);
            double r = right.get(i);
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm == 0 || rightNorm == 0) {
            return 0;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    public List<String> splitTags(String tagText) {
        if (!StringUtils.hasText(tagText)) {
            return new ArrayList<>();
        }
        String[] parts = tagText.split("[,，\\s]+");
        List<String> tags = new ArrayList<>();
        for (String part : parts) {
            if (StringUtils.hasText(part)) {
                tags.add(part.trim());
            }
        }
        return tags;
    }

    private record MemoryRecord(
            String id,
            String scope,
            String memoryType,
            String content,
            List<String> tags,
            String source,
            List<Double> embedding,
            String embeddingModel,
            LocalDateTime createdAt
    ) {
    }
}
