package com.songhg.veri.agent.common.transaction;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Creates transaction templates for services that must also load in no-database contract-test contexts.
 */
public final class OptionalTransactionTemplates {

    private static final PlatformTransactionManager NOOP_TRANSACTION_MANAGER = new NoopTransactionManager();

    private OptionalTransactionTemplates() {
    }

    public static TransactionTemplate create(ObjectProvider<PlatformTransactionManager> transactionManagers) {
        return new TransactionTemplate(transactionManagers.getIfAvailable(() -> NOOP_TRANSACTION_MANAGER));
    }

    private static final class NoopTransactionManager implements PlatformTransactionManager {

        @Override
        public TransactionStatus getTransaction(@Nullable TransactionDefinition definition) throws TransactionException {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) throws TransactionException {
            // No database transaction exists in lightweight contract-test contexts.
        }

        @Override
        public void rollback(TransactionStatus status) throws TransactionException {
            // No database transaction exists in lightweight contract-test contexts.
        }
    }
}
