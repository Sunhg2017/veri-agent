package com.songhg.veri.agent.modelaccess.application;

import com.songhg.veri.agent.modelaccess.api.InvokeModelRequest;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;

public interface PlatformContextClient {

    PlatformInvocationPolicy verifyInvocationContext(InvokeModelRequest request, ServicePrincipal principal);

    void writeInvocationAudit(InvocationRecord record);
}
