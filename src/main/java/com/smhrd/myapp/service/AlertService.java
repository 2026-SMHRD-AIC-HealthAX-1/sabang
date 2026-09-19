package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Alert;
import com.smhrd.myapp.repository.AlertRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

// 이상 알림 조회 / 미처리 건수 / 관리자 확인 처리(한 건, 여러 건 일괄)
@Service
public class AlertService {

    private final AlertRepository alertRepository;

    public AlertService(AlertRepository alertRepository) {
        this.alertRepository = alertRepository;
    }

    public List<Alert> listByAdmin(String adminId) {
        return alertRepository.findByMedicine_Admin_MemberIdOrderByAlertTimeDesc(adminId);
    }

    public long countPending(String adminId) {
        return alertRepository.countByMedicine_Admin_MemberIdAndProcessStatus(adminId, Alert.STATUS_PENDING);
    }

    // 확인 처리: 본인 병원의 알림만 처리할 수 있다
    public Alert process(String adminId, Long alertId) {

        Alert alert = alertRepository.findById(alertId).orElse(null);

        if (!isOwnedBy(alert, adminId)) {
            throw new IllegalStateException("존재하지 않거나 권한이 없는 알림입니다.");
        }

        alert.setProcessStatus(Alert.STATUS_DONE);

        return alertRepository.save(alert);
    }

    // 일괄 확인 처리: 하나라도 존재하지 않거나 내 병원 알림이 아니면 전부 처리하지 않는다 (all-or-nothing).
    // 이미 처리된 알림은 건너뛰고, 실제로 새로 처리된 건수를 돌려준다.
    @Transactional
    public int processAll(String adminId, List<Long> alertIds) {

        Set<Long> ids = new HashSet<>(alertIds);

        List<Alert> alerts = alertRepository.findAllById(ids);

        if (ids.isEmpty() || alerts.size() != ids.size()) {
            throw new IllegalStateException("존재하지 않거나 권한이 없는 알림이 포함되어 있습니다.");
        }

        for (Alert alert : alerts) {
            if (!isOwnedBy(alert, adminId)) {
                throw new IllegalStateException("존재하지 않거나 권한이 없는 알림이 포함되어 있습니다.");
            }
        }

        int processed = 0;

        for (Alert alert : alerts) {
            if (!Alert.STATUS_DONE.equals(alert.getProcessStatus())) {
                alert.setProcessStatus(Alert.STATUS_DONE);
                processed++;
            }
        }

        alertRepository.saveAll(alerts);

        return processed;
    }

    // 알림의 의약품을 등록한 관리자가 adminId 인지 확인
    private boolean isOwnedBy(Alert alert, String adminId) {

        return alert != null
                && alert.getMedicine().getAdmin().getMemberId().equals(adminId);
    }
}
