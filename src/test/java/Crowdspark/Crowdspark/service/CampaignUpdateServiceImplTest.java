// src/test/java/Crowdspark/Crowdspark/service/CampaignUpdateServiceImplTest.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CampaignUpdateRequest;
import Crowdspark.Crowdspark.dto.CampaignUpdateResponse;
import Crowdspark.Crowdspark.entity.CampaignUpdate;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.repository.CampaignUpdateRepository;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.impl.CampaignUpdateServiceImpl;
import Crowdspark.Crowdspark.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignUpdateServiceImpl Tests")
class CampaignUpdateServiceImplTest {

    @Mock CampaignUpdateRepository campaignUpdateRepository;
    @Mock ProjectRepository        projectRepository;
    @Mock UserRepository           userRepository;
    @Mock DonationRepository       donationRepository;
    @Mock NotificationService      notificationService;

    @InjectMocks CampaignUpdateServiceImpl updateService;

    private User    creator;
    private User    backer;
    private Project project;

    @BeforeEach
    void setUp() {
        creator = TestDataFactory.creatorUser();
        backer  = TestDataFactory.backerUser();
        project = TestDataFactory.approvedProject(creator);
    }

    @Test
    @DisplayName("createUpdate: saves update and notifies backers")
    void createUpdate_savesAndNotifiesBackers() {
        CampaignUpdateRequest req = new CampaignUpdateRequest();
        req.setTitle("We hit 50%!");
        req.setContent("Thanks to all backers, we're halfway there!");

        CampaignUpdate saved = new CampaignUpdate();
        saved.setId(1L);
        saved.setProject(project);
        saved.setAuthor(creator);
        saved.setTitle(req.getTitle());
        saved.setContent(req.getContent());
        saved.setCreatedAt(LocalDateTime.now());

        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        given(userRepository.findById(creator.getId())).willReturn(Optional.of(creator));
        given(campaignUpdateRepository.save(any())).willReturn(saved);
        given(donationRepository.findByProject_IdAndPaymentStatus(anyLong(), eq(PaymentStatus.SUCCESS)))
                .willReturn(List.of(TestDataFactory.successfulDonation(backer, project)));

        CampaignUpdateResponse response = updateService.createUpdate(project.getId(), req, creator.getId());

        assertThat(response.getTitle()).isEqualTo("We hit 50%!");
        assertThat(response.getAuthorUsername()).isEqualTo("testcreator");
        verify(notificationService).notifyBackerCampaignUpdate(eq(backer), eq(project), anyString());
    }

    @Test
    @DisplayName("createUpdate: throws 403 when user is not project creator")
    void createUpdate_throws403_whenNotCreator() {
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        CampaignUpdateRequest req = new CampaignUpdateRequest();
        req.setTitle("Fake update");
        req.setContent("I am not the creator");

        assertThatThrownBy(() -> updateService.createUpdate(project.getId(), req, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("not the creator");
    }

    @Test
    @DisplayName("createUpdate: throws 400 for non-updatable project status")
    void createUpdate_throws400_forPendingProject() {
        project.setStatus(ProjectStatus.PENDING);
        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));

        CampaignUpdateRequest req = new CampaignUpdateRequest();
        req.setTitle("Update");
        req.setContent("Content");

        assertThatThrownBy(() -> updateService.createUpdate(project.getId(), req, creator.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Updates can only be posted");
    }

    @Test
    @DisplayName("getUpdates: returns list of updates for any project")
    void getUpdates_returnsUpdates() {
        CampaignUpdate update = new CampaignUpdate();
        update.setId(1L);
        update.setProject(project);
        update.setAuthor(creator);
        update.setTitle("First update");
        update.setContent("Content here");
        update.setCreatedAt(LocalDateTime.now());

        given(projectRepository.existsById(project.getId())).willReturn(true);
        given(campaignUpdateRepository.findByProject_IdOrderByCreatedAtDesc(project.getId()))
                .willReturn(List.of(update));

        var result = updateService.getUpdates(project.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("First update");
    }

    @Test
    @DisplayName("deleteUpdate: throws 403 when non-author tries to delete")
    void deleteUpdate_throws403_whenNotAuthor() {
        CampaignUpdate update = new CampaignUpdate();
        update.setId(1L);
        update.setProject(project);
        update.setAuthor(creator);

        given(projectRepository.findById(project.getId())).willReturn(Optional.of(project));
        given(campaignUpdateRepository.findByIdAndProject_Id(1L, project.getId()))
                .willReturn(Optional.of(update));

        assertThatThrownBy(() -> updateService.deleteUpdate(project.getId(), 1L, backer.getId()))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("only edit your own");
    }
}
