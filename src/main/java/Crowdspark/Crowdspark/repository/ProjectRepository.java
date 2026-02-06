package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Feed (only approved/active)
    List<Project> findByStatusOrderByCreatedAtDesc(ProjectStatus status);

    // Creator dashboard
    List<Project> findByCreatorOrderByCreatedAtDesc(User creator);

    // Admin pending review
    List<Project> findByStatus(ProjectStatus status);

    long countByCreator(User creator);

    long countByCreatorAndStatus(User creator, ProjectStatus status);
}
