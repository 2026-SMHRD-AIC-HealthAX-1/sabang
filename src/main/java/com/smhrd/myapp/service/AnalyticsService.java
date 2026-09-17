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
    public List<Map<String, Object>> getOutboundLog() {

        return outboundRepository.findAll().stream()
                .map(this::toMap)
                .collect(Collectors.toList());
    }

    private Map<String, Object> toMap(Outbound outbound) {

        Map<String, Object> row = new HashMap<>();

        row.put("wardName", outbound.getWard().getWardName());
        row.put("medicineName", outbound.getMedicine().getMedicineName());
        row.put("qty", outbound.getOutboundQty());
        row.put("time", outbound.getOutboundTime());

        return row;
    }
}
