package io.github.nine710.qodergateway.application;

import io.github.nine710.qodergateway.infra.qoder.BootstrapHttpClient;
import io.github.nine710.qodergateway.infra.qoder.SessionTokenFactory;
import io.github.nine710.qodergateway.support.model.QoderSession;
import org.springframework.stereotype.Component;

@Component
public class SessionFacade {

    private final BootstrapHttpClient bootstrapHttpClient;
    private final SessionTokenFactory sessionTokenFactory;
    private volatile QoderSession cachedSession;

    public SessionFacade(BootstrapHttpClient bootstrapHttpClient, SessionTokenFactory sessionTokenFactory) {
        this.bootstrapHttpClient = bootstrapHttpClient;
        this.sessionTokenFactory = sessionTokenFactory;
    }

    public QoderSession getSession() {
        QoderSession session = cachedSession;
        if (session != null) {
            return session;
        }
        synchronized (this) {
            if (cachedSession == null) {
                cachedSession = sessionTokenFactory.create(bootstrapHttpClient.exchangeSession());
            }
            return cachedSession;
        }
    }
}
