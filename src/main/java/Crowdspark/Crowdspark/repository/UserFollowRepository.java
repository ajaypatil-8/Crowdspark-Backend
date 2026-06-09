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

    /** IDs of creators this user follows — used to build followed feed */
    @Query("SELECT f.following.id FROM UserFollow f WHERE f.follower.id = :userId")
    List<Long> findFollowingIds(@Param("userId") Long userId);
}
