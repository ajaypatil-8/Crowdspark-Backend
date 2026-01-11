package Crowdspark.Crowdspark.service;

import Crowdspark.Crowdspark.dto.DonateRequest;
import Crowdspark.Crowdspark.dto.DonationResponse;

import java.util.List;

public interface DonationService {

    DonationResponse donate(Long projectId, DonateRequest request);

    List<DonationResponse> myDonations();

    List<DonationResponse> projectDonations(Long projectId);
}
