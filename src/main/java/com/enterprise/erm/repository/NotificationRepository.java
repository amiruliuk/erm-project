package com.enterprise.erm.repository;
import com.enterprise.erm.domain.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface NotificationRepository extends JpaRepository<Notification,Long> {

 List<Notification> findByRecipientIdOrderByCreatedAtDesc(Long recipientId);

 /** Lets schedulers avoid creating the same reminder every day. */
 boolean existsByRecipientIdAndCategoryAndMessage(Long recipientId, String category, String message);
}
