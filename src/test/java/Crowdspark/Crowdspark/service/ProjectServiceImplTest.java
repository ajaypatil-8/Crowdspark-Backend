// src/test/java/Crowdspark/Crowdspark/service/ProjectServiceImplTest.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.ExploreRequest;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.CategoryRepository;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.RewardTierRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.impl.ProjectServiceImpl;
import Crowdspark.Crowdspark.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProjectServiceImpl Tests")
class ProjectServiceImplTest {

    @Mock ProjectRepository    projectRepository;
    @Mock UserRepository       userRepository;
    @Mock CategoryRepository   categoryRepository;
    @Mock RewardTierRepository rewardTierRepository;
    @Mock DonationRepository   donationRepository;

    @InjectMocks ProjectServiceImpl projectService;

    private User    creator;
    private Project project;

    @BeforeEach
    void setUp() {
        creator = TestDataFactory.creatorUser();
        project = TestDataFactory.approvedProject(creator);
    }

    // ─── getProjectFeed ───────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectFeed returns list of approved projects")
    void getProjectFeed_returnsApprovedProjects() {
        given(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.APPROVED))
                .willReturn(List.of(project));
        given(donationRepository.countByProject_IdAndPaymentStatus(anyLong(), eq(PaymentStatus.SUCCESS)))
                .willReturn(3L);

        List<ProjectFeedResponse> feed = projectService.getProjectFeed();

        assertThat(feed).hasSize(1);
        assertThat(feed.get(0).getTitle()).isEqualTo("Test Campaign");
        assertThat(feed.get(0).getBackersCount()).isEqualTo(3L);
        assertThat(feed.get(0).getFundedPercentage()).isEqualTo(25); // 25k/100k
    }

    @Test
    @DisplayName("getProjectFeed returns empty list when no approved projects")
    void getProjectFeed_empty_whenNoApprovedProjects() {
        given(projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.APPROVED))
                .willReturn(List.of());

        List<ProjectFeedResponse> feed = projectService.getProjectFeed();

        assertThat(feed).isEmpty();
    }

    // ─── getProjectDetails ────────────────────────────────────────────────────

    @Test
    @DisplayName("getProjectDetails returns full details for approved project")
    void getProjectDetails_returnsFullDetails_forApprovedProject() {
        given(projectRepository.findById(10L)).willReturn(Optional.of(project));
        given(rewardTierRepository.findByProject_Id(10L)).willReturn(List.of());
        given(donationRepository.countByProject_IdAndPaymentStatus(anyLong(), any()))
                .willReturn(5L);

        ProjectFullDetailsResponse details = projectService.getProjectDetails(10L);

        assertThat(details.getId()).isEqualTo(10L);
        assertThat(details.getTitle()).isEqualTo("Test Campaign");
        assertThat(details.getGoalAmount()).isEqualTo(100_000.0);
        assertThat(details.getCurrentAmount()).isEqualTo(25_000.0);
        assertThat(details.getFundedPercentage()).isEqualTo(25);
        assertThat(details.getBackersCount()).isEqualTo(5L);
        assertThat(details.getCreator().getUsername()).isEqualTo("testcreator");
    }

    @Test
    @DisplayName("getProjectDetails throws 404 when project not found")
    void getProjectDetails_throws404_whenProjectNotFound() {
        given(projectRepository.findById(999L)).willReturn(Optional.empty());

        assertThatThrownBy(() -> projectService.getProjectDetails(999L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Project not found");
    }

    @Test
    @DisplayName("getProjectDetails throws 404 for PENDING project")
    void getProjectDetails_throws404_forPendingProject() {
        project.setStatus(ProjectStatus.PENDING);
        given(projectRepository.findById(10L)).willReturn(Optional.of(project));

        assertThatThrownBy(() -> projectService.getProjectDetails(10L))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not available");
    }

    @Test
    @DisplayName("getProjectDetails allows FUNDED projects to be viewed")
    void getProjectDetails_allowsFundedProjects() {
        project.setStatus(ProjectStatus.FUNDED);
        given(projectRepository.findById(10L)).willReturn(Optional.of(project));
        given(rewardTierRepository.findByProject_Id(anyLong())).willReturn(List.of());
        given(donationRepository.countByProject_IdAndPaymentStatus(anyLong(), any()))
                .willReturn(10L);

        ProjectFullDetailsResponse details = projectService.getProjectDetails(10L);

        assertThat(details.getId()).isEqualTo(10L);
    }

    // ─── fundedPercentage calculation ─────────────────────────────────────────

    @Test
    @DisplayName("fundedPercentage is correctly calculated")
    void fundedPercentage_calculatedCorrectly() {
        project.setCurrentAmount(75_000.0);
        project.setGoalAmount(100_000.0);
        given(projectRepository.findById(10L)).willReturn(Optional.of(project));
        given(rewardTierRepository.findByProject_Id(anyLong())).willReturn(List.of());
        given(donationRepository.countByProject_IdAndPaymentStatus(anyLong(), any()))
                .willReturn(0L);

        ProjectFullDetailsResponse details = projectService.getProjectDetails(10L);

        assertThat(details.getFundedPercentage()).isEqualTo(75);
    }

    @Test
    @DisplayName("fundedPercentage is 0 when goalAmount is 0")
    void fundedPercentage_isZero_whenGoalIsZero() {
        project.setGoalAmount(0.0);
        project.setCurrentAmount(0.0);
        given(projectRepository.findById(10L)).willReturn(Optional.of(project));
        given(rewardTierRepository.findByProject_Id(anyLong())).willReturn(List.of());
        given(donationRepository.countByProject_IdAndPaymentStatus(anyLong(), any()))
                .willReturn(0L);

        ProjectFullDetailsResponse details = projectService.getProjectDetails(10L);

        assertThat(details.getFundedPercentage()).isEqualTo(0);
    }
}
