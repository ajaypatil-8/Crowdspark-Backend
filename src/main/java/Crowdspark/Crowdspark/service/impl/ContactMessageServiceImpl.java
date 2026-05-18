package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.ContactMessageReplyRequest;
import Crowdspark.Crowdspark.dto.ContactMessageRequest;
import Crowdspark.Crowdspark.dto.ContactMessageResponse;
import Crowdspark.Crowdspark.entity.ContactMessage;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.ContactMessageStatus;
import Crowdspark.Crowdspark.repository.ContactMessageRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.ContactMessageService;
import Crowdspark.Crowdspark.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ContactMessageServiceImpl implements ContactMessageService {

    private final ContactMessageRepository contactMessageRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @Override
    @Transactional
    public ContactMessageResponse create(ContactMessageRequest request) {
        ContactMessage message = ContactMessage.builder()
                .name(request.getName().trim())
                .email(request.getEmail().trim().toLowerCase())
                .topic(request.getTopic().trim())
                .message(request.getMessage().trim())
                .status(ContactMessageStatus.NEW)
                .build();

        return toResponse(contactMessageRepository.save(message));
    }

    @Override
    @Transactional(readOnly = true)
    public List<ContactMessageResponse> getAll() {
        return contactMessageRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public ContactMessageResponse markRead(Long id) {
        ContactMessage message = getMessage(id);
        if (message.getStatus() == ContactMessageStatus.NEW) {
            message.setStatus(ContactMessageStatus.READ);
            message.setReadAt(LocalDateTime.now());
        }
        return toResponse(contactMessageRepository.save(message));
    }

    @Override
    @Transactional
    public ContactMessageResponse reply(Long id, ContactMessageReplyRequest request, Long adminId) {
        ContactMessage message = getMessage(id);
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found: " + adminId));

        message.setStatus(ContactMessageStatus.REPLIED);
        if (message.getReadAt() == null) message.setReadAt(LocalDateTime.now());
        message.setRepliedAt(LocalDateTime.now());
        message.setReplySubject(request.getSubject().trim());
        message.setReplyMessage(request.getMessage().trim());
        message.setRepliedBy(admin);

        ContactMessage saved = contactMessageRepository.save(message);
        emailService.sendSimpleEmail(saved.getEmail(), saved.getReplySubject(), buildReplyBody(saved));
        return toResponse(saved);
    }

    private ContactMessage getMessage(Long id) {
        return contactMessageRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Contact message not found: " + id));
    }

    private String buildReplyBody(ContactMessage message) {
        return "Hi " + message.getName() + ",\n\n"
                + message.getReplyMessage()
                + "\n\n---\nYour original message:\n"
                + message.getMessage()
                + "\n\nTeam CrowdSpark";
    }

    private ContactMessageResponse toResponse(ContactMessage message) {
        User repliedBy = message.getRepliedBy();
        return ContactMessageResponse.builder()
                .id(message.getId())
                .name(message.getName())
                .email(message.getEmail())
                .topic(message.getTopic())
                .message(message.getMessage())
                .status(message.getStatus())
                .createdAt(message.getCreatedAt())
                .readAt(message.getReadAt())
                .repliedAt(message.getRepliedAt())
                .replySubject(message.getReplySubject())
                .replyMessage(message.getReplyMessage())
                .repliedByName(repliedBy != null ? repliedBy.getName() : null)
                .build();
    }
}