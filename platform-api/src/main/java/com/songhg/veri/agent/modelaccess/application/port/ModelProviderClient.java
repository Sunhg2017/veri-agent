package com.songhg.veri.agent.modelaccess.application.port;

import com.songhg.veri.agent.modelaccess.application.command.ProviderCallRequest;
import com.songhg.veri.agent.modelaccess.application.view.ProviderCallResult;
import com.songhg.veri.agent.modelaccess.domain.ModelProviderConfig;



public interface ModelProviderClient {

    boolean supports(ModelProviderConfig provider);

    ProviderCallResult call(ModelProviderConfig provider, ProviderCallRequest request);
}
