package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;

public interface ModelProviderClient {

    boolean supports(ModelProviderConfig provider);

    ProviderCallResult call(ModelProviderConfig provider, ProviderCallRequest request);
}
