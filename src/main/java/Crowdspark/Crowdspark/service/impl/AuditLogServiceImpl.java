package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.entity.AuditLog;
import Crowdspark.Crowdspark.repository.AuditLogRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuditLogServiceImpl implements AuditLogService {

    private final AuditLogRepository repository;



    @Override
    public void log(Long userId, String action, String entityType, Long entityId) {

        AuditLog log = new AuditLog();
        log.setUserId(userId);
        log.setAction(action);
        log.setEntityType(entityType);
        log.setEntityId(entityId);

        repository.save(log);
    }


}
