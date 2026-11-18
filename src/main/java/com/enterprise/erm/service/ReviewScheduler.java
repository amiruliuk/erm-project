package com.enterprise.erm.service;

import com.enterprise.erm.domain.CrossTrainingPlan;
import com.enterprise.erm.domain.Notification;
import com.enterprise.erm.domain.ReviewSchedule;
import com.enterprise.erm.repository.CrossTrainingPlanRepository;
import com.enterprise.erm.repository.NotificationRepository;
import com.enterprise.erm.repository.ReviewScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.EnumSet;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReviewScheduler {

    private final ReviewScheduleRepository reviews;
    private final CrossTrainingPlanRepository plans;
    private final NotificationRepository notifications;

    /** Every day at 06:00 — remind owners whose review is due within 14 days (once per review/date). */
    @Scheduled(cron = "0 0 6 * * *")
    @Transactional
    public void remindOwners() {
        LocalDate horizon = LocalDate.now().plusDays(14);
        for (ReviewSchedule r : reviews.findByCompletedOnIsNullAndDueOnBefore(horizon)) {
            String msg = "Review " + r.getScope() + " is due on " + r.getDueOn() + ".";
            if (notifications.existsByRecipientIdAndCategoryAndMessage(r.getOwner().getId(), "REVIEW_DUE", msg)) continue;
            Notification n = new Notification();
            n.setRecipient(r.getOwner());
            n.setMessage(msg);
            n.setCategory("REVIEW_DUE");
            notifications.save(n);
        }
    }

    /** Every day at 06:05 — an open cross-training plan past its target date becomes OVERDUE and its owner is told. */
    @Scheduled(cron = "0 5 6 * * *")
    @Transactional
    public void flagOverduePlans() {
        var open = EnumSet.of(CrossTrainingPlan.PlanStatus.PLANNED, CrossTrainingPlan.PlanStatus.IN_PROGRESS);
        for (CrossTrainingPlan p : plans.findByStatusInAndTargetDateBefore(open, LocalDate.now())) {
            p.setStatus(CrossTrainingPlan.PlanStatus.OVERDUE);
            String msg = "Cross-training plan for " + p.getDependency().getName()
                    + " passed its target date " + p.getTargetDate() + ".";
            if (!notifications.existsByRecipientIdAndCategoryAndMessage(p.getOwner().getId(), "PLAN_OVERDUE", msg)) {
                Notification n = new Notification();
                n.setRecipient(p.getOwner());
                n.setMessage(msg);
                n.setCategory("PLAN_OVERDUE");
                notifications.save(n);
            }
        }
    }
}
