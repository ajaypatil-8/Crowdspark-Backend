package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.type.MediaType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProjectMediaRepository extends JpaRepository<ProjectMedia, Long> {

    List<ProjectMedia> findByProjectId(Long projectId);

    Optional<ProjectMedia> findFirstByProjectIdAndType(
            Long projectId,
            MediaType type
    );
}
