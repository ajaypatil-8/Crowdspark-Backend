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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Page<UserFollow> follows = followRepository
                .findByFollower_IdOrderByFollowedAtDesc(userId, PageRequest.of(page, size));
        return mapFollowPage(follows, UserFollow::getFollowing);
    }

    // ── getFollowers ──────────────────────────────────────────────────────────

    @Override
    public Page<FollowResponse> getFollowers(Long userId, int page, int size) {
        Page<UserFollow> follows = followRepository
                .findByFollowing_IdOrderByFollowedAtDesc(userId, PageRequest.of(page, size));
        return mapFollowPage(follows, UserFollow::getFollower);
    }

    // AUDIT FIX (Feature #18): getFollowing/getFollowers used to call
    // toFollowResponse() once per row, and that method fired two separate
    // COUNT queries of its own (countByFollowing_Id, countByCreator) — up to
    // ~2 * page-size extra round trips per single page render. This gathers
    // the "other side" user IDs for the whole page ONCE, batch-fetches both
    // counts in two queries total (regardless of page size), and maps each
    // row from those pre-fetched lookups instead.
    private Page<FollowResponse> mapFollowPage(
            Page<UserFollow> follows,
            java.util.function.Function<UserFollow, User> userExtractor
    ) {
        List<User> users = follows.getContent().stream().map(userExtractor).toList();
        if (users.isEmpty()) {
            return follows.map(f -> toFollowResponse(userExtractor.apply(f), f.getFollowedAt(), 0L, 0L));
        }

        List<Long> ids = users.stream().map(User::getId).toList();
        Map<Long, Long> followerCounts = toCountMap(followRepository.countFollowersForUsers(ids));
        Map<Long, Long> projectCounts  = toCountMap(projectRepository.countByCreatorIds(ids));

        return follows.map(f -> {
            User user = userExtractor.apply(f);
            return toFollowResponse(
                    user,
                    f.getFollowedAt(),
                    followerCounts.getOrDefault(user.getId(), 0L),
                    projectCounts.getOrDefault(user.getId(), 0L)
            );
        });
    }

    // ── getFollowedFeed ───────────────────────────────────────────────────────

    @Override
    public List<ProjectFeedResponse> getFollowedFeed(Long userId) {
        List<Long> followingIds = followRepository.findFollowingIds(userId);
        if (followingIds.isEmpty()) return List.of();

        // AUDIT FIX (Feature #18): this used to fetch every APPROVED project
        // platform-wide and filter down to followed creators in Java — see
        // ProjectRepository.findTop20ByCreator_IdInAndStatusOrderByCreatedAtDesc
        // for the full explanation. Now the DB does the filtering and the
        // limiting, so only rows we're actually going to return are ever loaded.
        List<Project> projects = projectRepository
                .findTop20ByCreator_IdInAndStatusOrderByCreatedAtDesc(followingIds, ProjectStatus.APPROVED);

        if (projects.isEmpty()) return List.of();

        // Batch-fetch "total backers" per creator instead of hardcoding it —
        // see DonationRepository.countDistinctBackersByCreatorIds.
        List<Long> creatorIds = projects.stream().map(p -> p.getCreator().getId()).distinct().toList();
        Map<Long, Long> backersByCreator = toCountMap(
                donationRepository.countDistinctBackersByCreatorIds(creatorIds));

        return projects.stream()
                .map(p -> toFeedResponse(p, backersByCreator.getOrDefault(p.getCreator().getId(), 0L)))
                .toList();
    }

    /** Converts a `SELECT id, COUNT(...) GROUP BY id` result into a lookup map. */
    private Map<Long, Long> toCountMap(List<Object[]> rows) {
        Map<Long, Long> map = new HashMap<>();
        for (Object[] row : rows) {
            map.put((Long) row[0], (Long) row[1]);
        }
        return map;
    }

    // ── Mappers ───────────────────────────────────────────────────────────────

    private FollowResponse toFollowResponse(User user, LocalDateTime followedAt,
                                             long followerCount, long totalProjects) {
        boolean isCreator = user.getRoles() != null &&
                user.getRoles().contains(Role.CREATOR);

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

    private ProjectFeedResponse toFeedResponse(Project p, long totalBackersForCreator) {
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
        User creator = p.getCreator();

        return ProjectFeedResponse.builder()
                .id(p.getId()).title(p.getTitle())
                .shortDescription(p.getShortDescription())
                .category(cat).thumbnailUrl(thumbnail).previewVideoUrl(previewVideo)
                .goalAmount(p.getGoalAmount()).currentAmount(p.getCurrentAmount())
                .fundedPercentage(pct).daysLeft((int) daysLeft).backersCount(backers)
                .creator(ProjectFeedResponse.CreatorDto.builder()
                        .id(creator.getId())
                        .username(creator.getUsername())
                        .profileImage(creator.getProfileImageUrl())
                        .about(creator.getBio())
                        // AUDIT FIX (Feature #18): these three were hardcoded to
                        // null/0/0 regardless of reality. joinedAt and
                        // totalProjects come straight off the already-loaded
                        // creator entity (no extra query); totalBackers uses the
                        // batch-fetched map built in getFollowedFeed() above.
                        .joinedAt(creator.getCreatedAt() != null ? creator.getCreatedAt().toString() : null)
                        .totalProjects(creator.getTotalProjectsCreated() != null
                                ? creator.getTotalProjectsCreated().longValue() : 0L)
                        .totalBackers(totalBackersForCreator)
                        .build())
                .build();
    }
}
