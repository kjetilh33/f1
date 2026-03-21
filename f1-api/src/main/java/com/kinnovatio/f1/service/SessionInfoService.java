package com.kinnovatio.f1.service;

import com.kinnovatio.f1.model.SessionInfoRaw;
import com.kinnovatio.f1.repository.SessionInfoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.Optional;

@ApplicationScoped
public class SessionInfoService {
    private static final Logger LOG = Logger.getLogger(SessionInfoService.class);

    @Inject
    SessionInfoRepository sessionInfoRepository;

    public Optional<SessionInfoRaw> getSessionInfoLive() {
        return sessionInfoRepository.getSessionInfoLive();
    }
}
