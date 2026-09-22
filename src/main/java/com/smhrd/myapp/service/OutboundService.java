package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Alert;
import com.smhrd.myapp.entity.Medicine;
import com.smhrd.myapp.entity.Outbound;
import com.smhrd.myapp.entity.Slip;
import com.smhrd.myapp.entity.SlipItem;
import com.smhrd.myapp.repository.AlertRepository;
import com.smhrd.myapp.repository.MedicineRepository;
import com.smhrd.myapp.repository.OutboundRepository;
import com.smhrd.myapp.repository.SlipItemRepository;
import com.smhrd.myapp.repository.SlipRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// camera.py가 "이 전표의 이 의약품이 실제로 몇 개 반출됐다"를 보고하는 자리.
// 전표의 요청수량(SLIP_ITEM)과 비교해서 OUTBOUND를 남기고, 다르면 이상 알림(ALERT)까지 같이 만든다.
// (전표 인식 -> 세그먼트로 반출 확인 -> 수량 비교까지가 OCR 파이프라인의 마지막 단계)
@Service
public class OutboundService {

    private final SlipRepository slipRepository;
    private final SlipItemRepository slipItemRepository;
    private final MedicineRepository medicineRepository;
    private final OutboundRepository outboundRepository;
    private final AlertRepository alertRepository;

    public OutboundService(
            SlipRepository slipRepository,
            SlipItemRepository slipItemRepository,
            MedicineRepository medicineRepository,
            OutboundRepository outboundRepository,
            AlertRepository alertRepository
    ) {
        this.slipRepository = slipRepository;
        this.slipItemRepository = slipItemRepository;
        this.medicineRepository = medicineRepository;
        this.outboundRepository = outboundRepository;
        this.alertRepository = alertRepository;
    }

    @Transactional
    public Map<String, Object> reportDispense(String slipId, Long medicineId, Long outboundQty) {

        if (outboundQty == null || outboundQty < 0) {
            throw new IllegalStateException("반출 수량이 올바르지 않습니다.");
        }

        Slip slip = slipRepository.findById(slipId).orElse(null);

        if (slip == null) {
            throw new IllegalStateException("존재하지 않는 전표입니다: " + slipId);
        }

        Medicine medicine = medicineRepository.findById(medicineId).orElse(null);

        if (medicine == null) {
            throw new IllegalStateException("존재하지 않는 의약품입니다: " + medicineId);
        }

        // 같은 전표·의약품을 두 번 보고하지 않게 막는다 (감지 로직이 재시작되는 등으로 중복 호출될 수 있음)
        if (outboundRepository.countBySlip_SlipIdAndMedicine_MedicineId(slipId, medicineId) > 0) {
            throw new IllegalStateException("이미 반출 처리된 전표·의약품입니다.");
        }

        SlipItem slipItem = slipItemRepository.findById_SlipIdAndMedicine_MedicineId(slipId, medicineId).orElse(null);

        if (slipItem == null) {
            throw new IllegalStateException("이 전표에 해당 의약품 품목이 없습니다.");
        }

        Long requestQty = slipItem.getRequestQty();
        boolean abnormal = !outboundQty.equals(requestQty);

        Outbound outbound = new Outbound();
        outbound.setSlip(slip);
        outbound.setMedicine(medicine);
        outbound.setWard(slip.getWard());
        outbound.setOutboundQty(outboundQty);
        outbound.setOutboundTime(LocalDateTime.now());
        outbound.setAbnormalYn(abnormal ? "Y" : "N");

        outbound = outboundRepository.save(outbound);

        if (abnormal) {

            Alert alert = new Alert();
            alert.setOutbound(outbound);
            alert.setMedicine(medicine);
            alert.setAlertType(Alert.TYPE_MISMATCH);
            alert.setAlertContent(String.format(
                    "전표 %s: %s 요청 %d개 · 실제 반출 %d개",
                    slipId, medicine.getMedicineName(), requestQty, outboundQty
            ));
            alert.setAlertTime(LocalDateTime.now());
            alert.setProcessStatus(Alert.STATUS_PENDING);

            alertRepository.save(alert);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("outboundId", outbound.getOutboundId());
        result.put("requestQty", requestQty);
        result.put("outboundQty", outboundQty);
        result.put("abnormal", abnormal);

        return result;
    }
}
