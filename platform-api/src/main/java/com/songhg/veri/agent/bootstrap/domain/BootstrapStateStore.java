package com.songhg.veri.agent.bootstrap.domain;

public interface BootstrapStateStore {

    boolean hasSuperAdmin();

    String createSuperAdmin(BootstrapUserDraft draft);
}

