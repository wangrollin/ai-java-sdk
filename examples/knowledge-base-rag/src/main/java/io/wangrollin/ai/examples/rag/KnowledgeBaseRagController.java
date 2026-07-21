package io.wangrollin.ai.examples.rag;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Thin HTTP boundary around the example RAG service. */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeBaseRagController {
    private final KnowledgeBaseRagService service;

    public KnowledgeBaseRagController(KnowledgeBaseRagService service) {
        this.service = service;
    }

    @GetMapping("/answer")
    public RagAnswer answer(@RequestParam String question) {
        return service.answer(question);
    }
}
