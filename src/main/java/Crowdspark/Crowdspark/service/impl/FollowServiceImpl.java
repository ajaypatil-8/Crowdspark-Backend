// src/main/java/Crowdspark/Crowdspark/service/impl/FollowServiceImpl.java
package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.FollowResponse;
import Crowdspark.Crowdspark.dto.FollowStatusResponse;
import Crowdspark.Crowdspark.dto.ProjectFeedResponse;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.ProjectMedia;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.UserFollow;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.repository.*;
import Crowdspark.Crowdspark.service.FollowService;
import Crowdspark.Crowdspark.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FollowServiceImpl implements FollowService {

    private final UserFollowRepository  followRepository;
    private final UserRepository        userRepository;
    private final ProjectRepository     projectRepository;
    private final DonationRepository    donationRepository;
    private final NotificationService   notificationService;

    // ── toggle ────────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public FollowStatusResponse toggle(Long followerId, Long targetId) {
        if (followerId.equals(targetId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "You cannot follow yourself");
        }

        User follower = userRepository.findById(followerId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User not found"));
        User target = userRepository.findById(targetId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Target user not found"));

        boolean alreadyFollowing = followRepository
                .existsByFollower_IdAndFollowing_Id(followerId, targetId);

        if (alreadyFollowing) {
            followRepository.deleteByFollower_IdAndFollowing_Id(followerId, targetId);
            log.info("User {} unfollowed user {}", followerId, targetId);
        } else {
            UserFollow follow = new UserFollow();
            follow.setFollower(follower);
            follow.setFollowing(target);
            followRepository.save(follow);
            log.info("User {} followed user {}", followerId, targetId);

            // Notify target creator they have a new follower
            notificationService.notifyUserNewFollower(target, follower);
        }

        long updatedCount = followRepository.countByFollowing_Id(targetId);
        return FollowStatusResponse.builder()
                .targetUserId(targetId)
                .following(!alreadyFollowing)
                .followerCount(updatedCount)
                .build();
    }

    // ── checkStatus ───────────────────────────────────────────────────────────

    @Override
    public FollowStatusResponse checkStatus(Long followerId, Long targetId) {
        boolean following = followRepository
                .existsByFollower_IdAndFollowing_Id(followerId, targetId);
        long count = followRepository.countByFollowing_Id(targetId);
        return FollowStatusResponse.builder()
                .targetUserId(targetId)
                .following(following)
                .followerCount(count)
                .build();
    }

    // ── getFollowing ──────────────────────────────────────────────────────────

    @Override
    public Page<FollowResponse> getFollowing(Long userId, int page, int size) {
        return followRepository
                .findByFollower_IdOrderByFollowedAtDesc(userId, PageRequest.of(page, size))
                .map(f -> toFollowResponse(f.getFollowing(), f.getFollowedAt()));
    }

    // ── getFollowers ──────────────────────────────────────────────────────────

    @Override
    public Page<FollowResponse> getFollowers(Long userId, int page, int size) {
        return followRepository
                .findByFollowing_IdOrderByFollowedAtDesc(userId, PageRequest.of(page, size))
                .map(f -> toFollowResponse(f.getFollower(), f.getFollowedAt()));
    }

    // ── getFollowedFeed ───────────────────────────────────────────────────────

    @Override
    public List<ProjectFeedResponse> getFollowedFeed(Long userId) {
        List<Long> followingIds = followRepository.findFollowingIds(userId);
        if (followingIds.isEmpty()) return List.of();

        // Get latest APPROVED projects from followed creators (last 20)
        return projectRepository.findByStatusOrderByCreatedAtDesc(ProjectStatus.APPROVED)
                .stream()
                .filter(p -> followingIds.contains(p.getCreator().getId()))
                .limit(20)
                .map(this::toFeedResponse)
                .toList();
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private FollowResponse toFollowResponse(User user, LocalDateTime followedAt) {
        boolean isCreator = user.getRoles() != null &&
                user.getRoles().contains(Role.ROLE_CREATOR);
        long followerCount = followRepository.countByFollowing_Id(user.getId());
        long totalProjects = projectRepository.countByCreator(user);

        return FollowResponse.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .profileImageUrl(user.getProfileImageUrl())
                .bio(user.getBio())
                .isCreator(isCreator)
                .followerCount(followerCount)
                .totalProjects(totalProjects)
                .followedAt(followedAt)
                .build();
    }

    private ProjectFeedResponse toFeedResponse(Project p) {
        String thumbnail = null, previewVideo = null;
        for (ProjectMedia m : p.getMedia()) {
            if (m.getUsage() == MediaUsage.THUMBNAIL)  thumbnail    = m.getMediaUrl();
            if (m.getUsage() == MediaUsage.CARD_VIDEO) previewVideo = m.getMediaUrl();
        }
        int pct = p.getGoalAmount() > 0
                ? (int) ((p.getCurrentAmount() / p.getGoalAmount()) * 100) : 0;
        long daysLeft = ChronoUnit.DAYS.between(LocalDateTime.now(), p.getDeadline());
        long backers  = donationRepository.countByProject_IdAndPaymentStatus(
                p.getId(), PaymentStatus.SUCCESS);
        String cat = p.getCategories().isEmpty() ? null : p.getCategories().get(0).getName();

        return ProjectFeedResponse.builder()
                .id(p.getId()).title(p.getTitle())
                .shortDescription(p.getShortDescription())
                .category(cat).thumbnailUrl(thumbnail).previewVideoUrl(previewVideo)
                .goalAmount(p.getGoalAmount()).currentAmount(p.getCurrentAmount())
                .fundedPercentage(pct).daysLeft((int) daysLeft).backersCount(backers)
                .creator(ProjectFeedResponse.CreatorDto.builder()
                        .id(p.getCreator().getId())
                        .username(p.getCreator().getUsername())
                        .profileImage(p.getCreator().getProfileImageUrl())
                        .about(p.getCreator().getBio())
                        .joinedAt(null).totalProjects(0L).totalBackers(0L)
                        .build())
                .build();
    }
}
