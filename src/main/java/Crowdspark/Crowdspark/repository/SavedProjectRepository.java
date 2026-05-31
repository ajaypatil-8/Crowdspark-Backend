// src/main/java/Crowdspark/Crowdspark/repository/SavedProjectRepository.java
package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.SavedProject;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SavedProjectRepository extends JpaRepository<SavedProject, Long> {

    List<SavedProject> findByUser_IdOrderBySavedAtDesc(Long userId);

    Optional<SavedProject> findByUser_IdAndProject_Id(Long userId, Long projectId);

    boolean existsByUser_IdAndProject_Id(Long userId, Long projectId);

    void deleteByUser_IdAndProject_Id(Long userId, Long projectId);

    long countByProject_Id(Long projectId);
}
