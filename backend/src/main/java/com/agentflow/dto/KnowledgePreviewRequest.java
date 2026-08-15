package com.agentflow.dto;

import lombok.Data;

@Data
public class KnowledgePreviewRequest {

    private Integer chunkSize;

    private Integer chunkOverlap;
}
