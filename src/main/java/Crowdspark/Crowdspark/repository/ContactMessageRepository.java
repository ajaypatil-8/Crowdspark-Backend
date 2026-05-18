package Crowdspark.Crowdspark.repository;

import Crowdspark.Crowdspark.entity.ContactMessage;
import Crowdspark.Crowdspark.entity.type.ContactMessageStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ContactMessageRepository extends JpaRepository<ContactMessage, Long> {

    List<ContactMessage> findAllByOrderByCreatedAtDesc();

    long countByStatus(ContactMessageStatus status);
}