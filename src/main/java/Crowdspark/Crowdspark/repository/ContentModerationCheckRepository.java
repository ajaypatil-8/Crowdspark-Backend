// src/main/java/Crowdspark/Crowdspark/repository/ContentModerationCheckRepository.java
// Feature #45 — AI Content Moderation

package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.ContentModerationCheck;
import Crowdspark.Crowdspark.entity.type.ContentType;
import Crowdspark.Crowdspark.entity.type.ModerationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ContentModerationCheckRepository extends JpaRepository<ContentModerationCheck, Long> {

    Optional<ContentModerationCheck> findByContentTypeAndContentId(ContentType contentType, Long contentId);

    // Batch lookup for the admin projects queue — avoids N+1, same reasoning
    // as ProjectFraudCheckRepository.findByProject_IdIn.
    List<ContentModerationCheck> findByContentTypeAndContentIdIn(ContentType contentType, List<Long> contentIds);

    // Admin moderation queue — unresolved flags, newest first.
    List<ContentModerationCheck> findByStatusAndResolvedByAdminFalseOrderByCreatedAtDesc(ModerationStatus status);
}
