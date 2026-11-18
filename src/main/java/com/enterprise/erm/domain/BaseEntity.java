package com.enterprise.erm.domain;
import jakarta.persistence.*;
import lombok.Getter; import lombok.Setter;
import java.time.LocalDateTime;
@MappedSuperclass @Getter @Setter
public abstract class BaseEntity {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(nullable=false, updatable=false) private LocalDateTime createdAt;
 private LocalDateTime updatedAt;
 @PrePersist void onCreate(){ createdAt=LocalDateTime.now(); updatedAt=createdAt; }
 @PreUpdate void onUpdate(){ updatedAt=LocalDateTime.now(); }
}
