// src/main/java/Crowdspark/Crowdspark/repository/UserFollowRepository.java
package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.UserFollow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserFollowRepository extends JpaRepository<UserFollow, Long> {

    boolean existsByFollower_IdAndFollowing_Id(Long followerId, Long followingId);

    Optional<UserFollow> findByFollower_IdAndFollowing_Id(Long followerId, Long followingId);

    void deleteByFollower_IdAndFollowing_Id(Long followerId, Long followingId);

    /** Who is this user following */
    Page<UserFollow> findByFollower_IdOrderByFollowedAtDesc(Long followerId, Pageable pageable);

    /** Who follows this user */
    Page<UserFollow> findByFollowing_IdOrderByFollowedAtDesc(Long followingId, Pageable pageable);

    long countByFollower_Id(Long followerId);

    long countByFollowing_Id(Long followingId);

    // AUDIT FIX (Feature #18): batched version of countByFollowing_Id, used to
    // avoid firing one COUNT query per row when rendering a page of
    // followers/following (previously up to ~20 extra queries per page — see
    // FollowServiceImpl.toFollowResponse).
    @Query("SELECT f.following.id, COUNT(f) FROM UserFollow f WHERE f.following.id IN :userIds GROUP BY f.following.id")
    List<Object[]> countFollowersForUsers(@Param("userIds") List<Long> userIds);

    /** IDs of creators this user follows — used to build followed feed */
    @Query("SELECT f.following.id FROM UserFollow f WHERE f.follower.id = :userId")
    List<Long> findFollowingIds(@Param("userId") Long userId);

    // AUDIT FIX (Feature #11/#18): needed by GDPR account deletion, which
    // previously never touched this table at all — a "deleted" user's follow
    // graph (who they followed, who followed them) stayed intact forever.
    @org.springframework.data.jpa.repository.Modifying
    @Query("DELETE FROM UserFollow f WHERE f.follower.id = :userId OR f.following.id = :userId")
    void deleteAllInvolvingUser(@Param("userId") Long userId);
}
