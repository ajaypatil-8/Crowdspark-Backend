package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByStatus(ProjectStatus status);

    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    List<Project> findByCreatorId(Long creatorId);


    Optional<Project> findByIdAndCreatorId(Long id, Long creatorId);


}
