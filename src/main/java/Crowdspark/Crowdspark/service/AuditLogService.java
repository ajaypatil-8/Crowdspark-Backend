package Crowdspark.Crowdspark.service;

public interface AuditLogService {

    void log(Long userId, String action, String entityType, Long entityId);
}
