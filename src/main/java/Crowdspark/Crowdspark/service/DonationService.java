package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.CreateDonationRequest;
import Crowdspark.Crowdspark.dto.DonationResponse;

import java.util.List;

public interface DonationService {

    DonationResponse donate(CreateDonationRequest request, Long backerId);

    List<DonationResponse> getMyDonations(Long backerId);

    List<DonationResponse> getProjectDonations(Long projectId);
}
