package Crowdspark.Crowdspark.entity.type;

public enum NotificationType {
    PROJECT_BACKED,         // creator: someone backed your project
    PROJECT_APPROVED,       // creator: admin approved your project
    PROJECT_REJECTED,       // creator: admin rejected your project
    PROJECT_GOAL_REACHED,   // creator: project hit funding goal
    PROJECT_DEADLINE_NEAR,  // creator: 3 days left
    KYC_APPROVED,           // user: KYC approved
    KYC_REJECTED,           // user: KYC rejected
    DONATION_CONFIRMED,     // backer: your donation was confirmed
    GENERAL                 // misc admin message
}
