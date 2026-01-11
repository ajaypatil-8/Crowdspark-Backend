package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.AddCommentRequest;
import Crowdspark.Crowdspark.dto.CommentResponse;

import java.util.List;

public interface CommentService {

    CommentResponse addComment(Long projectId, AddCommentRequest request);

    CommentResponse replyToComment(Long commentId, AddCommentRequest request);

    List<CommentResponse> getProjectComments(Long projectId);

    List<CommentResponse> getReplies(Long parentCommentId);


}
