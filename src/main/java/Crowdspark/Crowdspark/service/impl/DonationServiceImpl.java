package Crowdspark.Crowdspark.service.impl;

import Crowdspark.Crowdspark.dto.DonateRequest;
import Crowdspark.Crowdspark.dto.DonationResponse;
import Crowdspark.Crowdspark.entity.Donation;
import Crowdspark.Crowdspark.entity.Payment;
import Crowdspark.Crowdspark.entity.Project;
import Crowdspark.Crowdspark.entity.User;
import Crowdspark.Crowdspark.entity.type.PaymentStatus;
import Crowdspark.Crowdspark.entity.type.ProjectStatus;
import Crowdspark.Crowdspark.entity.type.Role;
import Crowdspark.Crowdspark.exception.AuthException;
import Crowdspark.Crowdspark.repository.DonationRepository;
import Crowdspark.Crowdspark.repository.PaymentRepository;
import Crowdspark.Crowdspark.repository.ProjectRepository;
import Crowdspark.Crowdspark.repository.UserRepository;
import Crowdspark.Crowdspark.service.AuditLogService;
import Crowdspark.Crowdspark.service.DonationService;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DonationServiceImpl implements DonationService {

    private final DonationRepository donationRepository;
    private final ProjectRepository projectRepository;
    private final UserRepository userRepository;
    private final PaymentRepository paymentRepository;
    private final AuditLogService auditLogService;
    private final ModelMapper modelMapper;

    /* ================= DONATE ================= */

    @Override
    public DonationResponse donate(Long projectId, DonateRequest request) {

        if (request.getAmount() == null || request.getAmount() <= 0) {
            throw new AuthException("Donation amount must be greater than zero");
        }

        User user = getCurrentUser();

        // ✅ MULTI-ROLE CHECK
        if (!user.getRoles().contains(Role.BACKER)) {
            throw new AuthException("Only BACKER can donate");
        }

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AuthException("Project not found"));

        if (project.getStatus() != ProjectStatus.APPROVED) {
            throw new AuthException("Cannot donate to unapproved project");
        }

        Donation donation = new Donation();
        donation.setUserId(user.getId());
        donation.setProjectId(projectId);
        donation.setAmount(request.getAmount());

        Donation savedDonation = donationRepository.save(donation);

        Payment payment = new Payment();
        payment.setDonationId(savedDonation.getId());
        payment.setAmount(savedDonation.getAmount());
        payment.setProvider("MOCK");
        payment.setStatus(PaymentStatus.PENDING);

        paymentRepository.save(payment);

        auditLogService.log(
                user.getId(),
                "DONATION_CREATED",
                "PROJECT",
                projectId
        );

        return modelMapper.map(savedDonation, DonationResponse.class);
    }

    /* ================= MY DONATIONS ================= */

    @Override
    public List<DonationResponse> myDonations() {

        User user = getCurrentUser();

        return donationRepository.findByUserId(user.getId())
                .stream()
                .map(d -> modelMapper.map(d, DonationResponse.class))
                .toList();
    }

    /* ================= PROJECT DONATIONS ================= */

    @Override
    public List<DonationResponse> projectDonations(Long projectId) {

        User user = getCurrentUser();

        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new AuthException("Project not found"));

        boolean isAdmin = user.getRoles().contains(Role.ADMIN);
        boolean isCreatorOwner =
                user.getRoles().contains(Role.CREATOR)
                        && project.getCreatorId().equals(user.getId());

        if (isAdmin || isCreatorOwner) {
            return donationRepository.findByProjectId(projectId)
                    .stream()
                    .map(d -> modelMapper.map(d, DonationResponse.class))
                    .toList();
        }

        throw new AuthException("Not allowed to view donations");
    }

    /* ================= HELPERS ================= */

    private User getCurrentUser() {
        if (SecurityContextHolder.getContext() == null ||
                SecurityContextHolder.getContext().getAuthentication() == null ||
                !SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            throw new AuthException("Not authenticated");
        }

        String username = SecurityContextHolder.getContext()
                .getAuthentication()
                .getName();

        if (username == null) {
            throw new AuthException("Not authenticated");
        }

        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AuthException("User not found"));
    }
}
