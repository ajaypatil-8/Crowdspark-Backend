package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.ContactMessageReplyRequest;
import Crowdspark.Crowdspark.dto.ContactMessageRequest;
import Crowdspark.Crowdspark.dto.ContactMessageResponse;

import java.util.List;

public interface ContactMessageService {

    ContactMessageResponse create(ContactMessageRequest request);

    List<ContactMessageResponse> getAll();

    ContactMessageResponse markRead(Long id);

    ContactMessageResponse reply(Long id, ContactMessageReplyRequest request, Long adminId);
}