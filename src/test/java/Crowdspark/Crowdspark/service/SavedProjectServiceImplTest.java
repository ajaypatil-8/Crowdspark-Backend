// src/test/java/Crowdspark/Crowdspark/service/SavedProjectServiceImplTest.java
package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.SavedProject;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.SavedProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.impl.SavedProjectServiceImpl;
import Crowdspark.Crowdspark.util.TestDataFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("SavedProjectServiceImpl Tests")
class SavedProjectServiceImplTest {

    @Mock SavedProjectRepository savedProjectRepository;
    @Mock ProjectRepository      projectRepository;
    @Mock UserRepository         userRepository;
    @Mock DonationRepository     donationRepository;

    @InjectMocks SavedProjectServiceImpl savedProjectService;

    private User    user;
    private Project project;

    @BeforeEach
    void setUp() {
        user    = TestDataFactory.backerUser();
        project = TestDataFactory.approvedProject(TestDataFactory.creatorUser());
    }

    @Test
    @DisplayName("save: saves project when not already saved")
    void save_savesProject_whenNotAlreadySaved() {
        given(savedProjectRepository.existsByUser_IdAndProject_Id(1L, 10L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(projectRepository.findById(10L)).willReturn(Optional.of(project));
        given(savedProjectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        savedProjectService.save(1L, 10L);

        verify(savedProjectRepository).save(any(SavedProject.class));
    }

    @Test
    @DisplayName("save: idempotent — does nothing when already saved")
    void save_isIdempotent_whenAlreadySaved() {
        given(savedProjectRepository.existsByUser_IdAndProject_Id(1L, 10L)).willReturn(true);

        savedProjectService.save(1L, 10L);

        verify(savedProjectRepository, never()).save(any());
        verifyNoInteractions(userRepository, projectRepository);
    }

    @Test
    @DisplayName("unsave: removes saved project")
    void unsave_removesSavedProject() {
        savedProjectService.unsave(1L, 10L);

        verify(savedProjectRepository).deleteByUser_IdAndProject_Id(1L, 10L);
    }

    @Test
    @DisplayName("toggle: returns true when project is newly saved")
    void toggle_returnsTrue_whenNewlySaved() {
        given(savedProjectRepository.existsByUser_IdAndProject_Id(1L, 10L)).willReturn(false);
        given(userRepository.findById(1L)).willReturn(Optional.of(user));
        given(projectRepository.findById(10L)).willReturn(Optional.of(project));
        given(savedProjectRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        boolean result = savedProjectService.toggle(1L, 10L);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("toggle: returns false when project is unsaved")
    void toggle_returnsFalse_whenUnsaved() {
        given(savedProjectRepository.existsByUser_IdAndProject_Id(1L, 10L)).willReturn(true);

        boolean result = savedProjectService.toggle(1L, 10L);

        assertThat(result).isFalse();
        verify(savedProjectRepository).deleteByUser_IdAndProject_Id(1L, 10L);
    }

    @Test
    @DisplayName("isSaved: returns true when project is saved")
    void isSaved_returnsTrue_whenSaved() {
        given(savedProjectRepository.existsByUser_IdAndProject_Id(1L, 10L)).willReturn(true);

        assertThat(savedProjectService.isSaved(1L, 10L)).isTrue();
    }

    @Test
    @DisplayName("isSaved: returns false when project is not saved")
    void isSaved_returnsFalse_whenNotSaved() {
        given(savedProjectRepository.existsByUser_IdAndProject_Id(1L, 10L)).willReturn(false);

        assertThat(savedProjectService.isSaved(1L, 10L)).isFalse();
    }

    @Test
    @DisplayName("getSaved: returns all saved projects for user")
    void getSaved_returnsSavedProjects() {
        SavedProject sp = new SavedProject();
        sp.setUser(user);
        sp.setProject(project);
        given(savedProjectRepository.findByUser_IdOrderBySavedAtDesc(1L))
                .willReturn(List.of(sp));
        given(donationRepository.countByProject_IdAndPaymentStatus(anyLong(), eq(PaymentStatus.SUCCESS)))
                .willReturn(2L);

        var result = savedProjectService.getSaved(1L);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getTitle()).isEqualTo("Test Campaign");
        assertThat(result.get(0).getBackersCount()).isEqualTo(2L);
    }

    @Test
    @DisplayName("getSaved: returns empty list when nothing saved")
    void getSaved_returnsEmpty_whenNothingSaved() {
        given(savedProjectRepository.findByUser_IdOrderBySavedAtDesc(1L))
                .willReturn(List.of());

        var result = savedProjectService.getSaved(1L);

        assertThat(result).isEmpty();
    }
}
