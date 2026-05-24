package com.songhg.veri.agent.modelaccess.application.port;

import com.songhg.veri.agent.modelaccess.application.command.ModelInvocationCommand;
import com.songhg.veri.agent.modelaccess.domain.InvocationRecord;
import com.songhg.veri.agent.modelaccess.security.ServicePrincipal;


public interface PlatformContextClient {

    PlatformInvocationPolicy verifyInvocationContext(ModelInvocationCommand request, ServicePrincipal principal);

    void writeInvocationAudit(InvocationRecord record);
}
