package io.wangrollin.ai.examples.rag;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/** Spring Boot marker for the in-memory RAG workflow example. */
@SpringBootApplication
@EnableConfigurationProperties(RagProperties.class)
public class KnowledgeBaseRagApplication {
}
