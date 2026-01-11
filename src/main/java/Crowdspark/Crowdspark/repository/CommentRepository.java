package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.Comment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    List<Comment> findByProjectIdAndParentCommentIdIsNull(Long projectId);

    List<Comment> findByParentCommentId(Long parentCommentId);
}
