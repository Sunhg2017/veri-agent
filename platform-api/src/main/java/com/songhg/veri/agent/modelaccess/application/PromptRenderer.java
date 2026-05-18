package com.songhg.veri.agent.modelaccess.application;

import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class PromptRenderer {

    public String render(String content, Map<String, String> variables) {
        String rendered = content;
        if (variables == null || variables.isEmpty()) {
            return rendered;
        }
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }
}
