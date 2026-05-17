package com.songhg.veri.agent.bootstrap.infrastructure;

import com.songhg.veri.agent.auth.domain.AuthIdentityStore;
import com.songhg.veri.agent.auth.domain.AuthUserRecord;
import com.songhg.veri.agent.bootstrap.domain.BootstrapStateStore;
import com.songhg.veri.agent.bootstrap.domain.BootstrapUserDraft;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Profile("local")
@Repository
public class InMemoryBootstrapStateStore implements BootstrapStateStore, AuthIdentityStore {

    private final AtomicReference<BootstrapUserRecord> superAdmin = new AtomicReference<>();

    @Override
    public boolean hasSuperAdmin() {
        return superAdmin.get() != null;
    }

    @Override
    public String createSuperAdmin(BootstrapUserDraft draft) {
        BootstrapUserRecord record = new BootstrapUserRecord(UUID.randomUUID(), draft, 1);
        if (!superAdmin.compareAndSet(null, record)) {
            throw new IllegalStateException("super admin already initialized");
        }
        return record.userId().toString();
    }

    @Override
    public Optional<AuthUserRecord> findEnabledByUsername(String username) {
        BootstrapUserRecord record = superAdmin.get();
        if (record == null || !record.draft().username().equals(username)) {
            return Optional.empty();
        }
        return Optional.of(toAuthUserRecord(record));
    }

    @Override
    public Optional<AuthUserRecord> findEnabledByUserId(UUID userId) {
        BootstrapUserRecord record = superAdmin.get();
        if (record == null || !record.userId().equals(userId)) {
            return Optional.empty();
        }
        return Optional.of(toAuthUserRecord(record));
    }

    @Override
    public void changePassword(UUID userId, String passwordHash, UUID updatedBy) {
        while (true) {
            BootstrapUserRecord current = superAdmin.get();
            if (current == null || !current.userId().equals(userId)) {
                return;
            }
            BootstrapUserDraft updatedDraft = new BootstrapUserDraft(
                    current.draft().username(),
                    passwordHash,
                    current.draft().displayName(),
                    current.draft().email(),
                    current.draft().roleCode(),
                    false
            );
            BootstrapUserRecord updated = new BootstrapUserRecord(userId, updatedDraft, current.authVersion() + 1);
            if (superAdmin.compareAndSet(current, updated)) {
                return;
            }
        }
    }

    private AuthUserRecord toAuthUserRecord(BootstrapUserRecord record) {
        return new AuthUserRecord(
                record.userId(),
                record.draft().username(),
                record.draft().displayName(),
                record.draft().email(),
                record.draft().passwordHash(),
                record.draft().mustChangePassword(),
                record.authVersion(),
                List.of(record.draft().roleCode())
        );
    }

    record BootstrapUserRecord(UUID userId, BootstrapUserDraft draft, long authVersion) {
    }
}
