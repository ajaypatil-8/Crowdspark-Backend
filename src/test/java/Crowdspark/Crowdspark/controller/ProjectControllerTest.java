// src/test/java/Crowdspark/Crowdspark/controller/ProjectControllerTest.java
package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.dto.ProjectFullDetailsResponse;
import Crowdspark.Crowdspark.security.JwtAuthenticationFilter;
import Crowdspark.Crowdspark.service.CloudinaryService;
import Crowdspark.Crowdspark.service.ProjectService;
import Crowdspark.Crowdspark.service.UserService;
import Crowdspark.Crowdspark.util.TestDataFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
    controllers = ProjectController.class,
    excludeAutoConfiguration = SecurityAutoConfiguration.class
)
@DisplayName("ProjectController Tests")
class ProjectControllerTest {

    @Autowired MockMvc      mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockBean ProjectService          projectService;
    @MockBean UserService             userService;
    @MockBean CloudinaryService       cloudinaryService;
    @MockBean JwtAuthenticationFilter jwtFilter;

    private ProjectFeedResponse sampleFeedItem() {
        return ProjectFeedResponse.builder()
                .id(10L)
                .title("Test Campaign")
                .shortDescription("A test campaign")
                .goalAmount(100_000.0)
                .currentAmount(25_000.0)
                .fundedPercentage(25)
                .daysLeft(30)
                .backersCount(5L)
                .creator(ProjectFeedResponse.CreatorDto.builder()
                        .id(2L).username("testcreator").build())
                .build();
    }

    // ─── GET /api/projects/feed ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/projects/feed → 200 with list of projects")
    void getFeed_returns200_withProjects() throws Exception {
        given(projectService.getProjectFeed()).willReturn(List.of(sampleFeedItem()));

        mockMvc.perform(get("/api/projects/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data[0].id").value(10))
                .andExpect(jsonPath("$.data[0].title").value("Test Campaign"))
                .andExpect(jsonPath("$.data[0].fundedPercentage").value(25));
    }

    @Test
    @DisplayName("GET /api/projects/feed → 200 with empty list")
    void getFeed_returns200_withEmptyList() throws Exception {
        given(projectService.getProjectFeed()).willReturn(List.of());

        mockMvc.perform(get("/api/projects/feed"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());
    }

    // ─── GET /api/projects/{id} ───────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/projects/{id} → 200 with full project details")
    void getProjectDetails_returns200() throws Exception {
        ProjectFullDetailsResponse details = ProjectFullDetailsResponse.builder()
                .id(10L)
                .title("Test Campaign")
                .shortDescription("A test campaign")
                .goalAmount(100_000.0)
                .currentAmount(25_000.0)
                .fundedPercentage(25)
                .daysLeft(30L)
                .backersCount(5L)
                .deadline(LocalDateTime.now().plusDays(30))
                .rewards(List.of())
                .previewVideos(List.of())
                .galleryImages(List.of())
                .storyImages(List.of())
                .creator(ProjectFullDetailsResponse.CreatorDto.builder()
                        .id(2L).username("testcreator").build())
                .build();

        given(projectService.getProjectDetails(10L)).willReturn(details);

        mockMvc.perform(get("/api/projects/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(10))
                .andExpect(jsonPath("$.data.title").value("Test Campaign"))
                .andExpect(jsonPath("$.data.fundedPercentage").value(25))
                .andExpect(jsonPath("$.data.backersCount").value(5));
    }

    // ─── GET /api/projects/explore ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/projects/explore → 200 with paginated results")
    void explore_returns200_withPaginatedResults() throws Exception {
        var page = new PageImpl<>(List.of(sampleFeedItem()), PageRequest.of(0, 12), 1);
        given(projectService.exploreProjects(any())).willReturn(page);

        mockMvc.perform(get("/api/projects/explore")
                        .param("page", "0")
                        .param("size", "12")
                        .param("sort", "LATEST"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content").isArray())
                .andExpect(jsonPath("$.data.content[0].id").value(10))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }
}
