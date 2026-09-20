package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Outbound;
import com.smhrd.myapp.repository.OutboundRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AnalyticsService {

    private final OutboundRepository outboundRepository;

    public AnalyticsService(OutboundRepository outboundRepository) {
        this.outboundRepository = outboundRepository;
    }

    // 통계 화면(피벗 필터)에서 병동/의약품/시간대별로 자유롭게 재집계할 수 있도록
    // 가공하지 않은 출고 기록을 그대로 반환한다. 집계 기준이 자주 바뀔 수 있어
    // 백엔드에 집계 로직을 고정하지 않고 프론트에서 매번 계산한다.
    // 구독 관리자 1명이 병원 1곳이므로, 해당 관리자가 등록한 의약품의 출고 기록만 준다.
    // includeAbnormal: 이상 알림 여부(abnormal)를 포함할지 - 관리자에게만 true
    public List<Map<String, Object>> getOutboundLog(String adminId, boolean includeAbnormal) {

        return outboundRepository.findByMedicine_Admin_MemberId(adminId).stream()
                .map(outbound -> toMap(outbound, includeAbnormal))
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(Outbound outbound, boolean includeAbnormal) {

        Map<String, Object> row = new HashMap<>();

        row.put("wardName", outbound.getWard().getWardName());
        row.put("medicineName", outbound.getMedicine().getMedicineName());
        row.put("qty", outbound.getOutboundQty());
        row.put("time", outbound.getOutboundTime());

        if (includeAbnormal) {
            row.put("abnormal", "Y".equals(outbound.getAbnormalYn()));
        }

        return row;
    }
}
