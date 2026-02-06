package Crowdspark.Crowdspark.entity;

import Crowdspark.Crowdspark.entity.type.MediaType;
import Crowdspark.Crowdspark.entity.type.MediaUsage;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "project_media")
public class ProjectMedia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Cloudinary URL
    @Column(nullable = false)
    private String mediaUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaType mediaType;   // IMAGE / VIDEO

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MediaUsage usage;      // THUMBNAIL, CARD_VIDEO, STORY_IMAGE...

    // order in UI (carousel / story rendering)
    private Integer displayOrder;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;
}
