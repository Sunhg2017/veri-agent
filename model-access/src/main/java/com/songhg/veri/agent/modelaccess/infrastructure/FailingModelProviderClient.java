package com.songhg.veri.agent.modelaccess.infrastructure;

import com.songhg.veri.agent.modelaccess.application.ModelProviderClient;
import com.songhg.veri.agent.modelaccess.application.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.ProviderCallResult;
import com.songhg.veri.agent.modelaccess.common.BusinessException;
import com.songhg.veri.agent.modelaccess.common.ErrorCode;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;
import com.songhg.veri.agent.modelaccess.domain.ProviderType;
import org.springframework.stereotype.Component;

@Component
public class FailingModelProviderClient implements ModelProviderClient {

    @Override
    public boolean supports(ModelProviderConfig provider) {
        return provider.providerType() == ProviderType.MOCK_FAILURE;
    }

    @Override
    public ProviderCallResult call(ModelProviderConfig provider, ProviderCallRequest request) {
        throw new BusinessException(ErrorCode.MODEL_PROVIDER_UNAVAILABLE, "模拟模型供应商不可用");
    }
}
