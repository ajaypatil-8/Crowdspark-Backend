package Crowdspark.Crowdspark.controller;

import Crowdspark.Crowdspark.dto.ApiResponse;
import Crowdspark.Crowdspark.dto.ContactMessageRequest;
import Crowdspark.Crowdspark.dto.ContactMessageResponse;
import Crowdspark.Crowdspark.service.ContactMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/contact")
@RequiredArgsConstructor
public class ContactMessageController {

    private final ContactMessageService contactMessageService;

    @PostMapping("/messages")
    public ResponseEntity<ApiResponse<ContactMessageResponse>> create(@Valid @RequestBody ContactMessageRequest request) {
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Message received", contactMessageService.create(request)));
    }
}