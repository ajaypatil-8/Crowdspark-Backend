package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.ProjectUpdate;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectUpdateRepository extends JpaRepository<ProjectUpdate, Long> {

    List<ProjectUpdate> findByProjectIdOrderByCreatedAtDesc(Long projectId);
}
