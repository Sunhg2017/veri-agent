package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.ModelProviderClient;
import com.songhg.veri.agent.modelaccess.application.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.ProviderCallResult;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import org.springframework.stereotype.Component;

@Component
public class LocalEchoModelProviderClient implements ModelProviderClient {

    @Override
    public boolean supports(ModelProviderConfig provider) {
        return provider.providerType() == ProviderType.LOCAL_ECHO;
    }

    @Override
    public ProviderCallResult call(ModelProviderConfig provider, ProviderCallRequest request) {
        String content = "local model response: " + firstNonBlank(request.messageText(), request.prompt());
        int inputTokens = estimateTokens(request.prompt()) + estimateTokens(request.messageText());
        int outputTokens = estimateTokens(content);
        return new ProviderCallResult(content, inputTokens, outputTokens);
    }

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first.length() > 500 ? first.substring(0, 500) : first;
        }
        if (second == null) {
            return "";
        }
        return second.length() > 500 ? second.substring(0, 500) : second;
    }

    private int estimateTokens(String content) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        return Math.max(1, (int) Math.ceil(content.length() / 4.0));
    }
}
