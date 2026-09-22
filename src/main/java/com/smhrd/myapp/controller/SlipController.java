package com.smhrd.myapp.controller;

import com.smhrd.myapp.entity.Outbound;
import com.smhrd.myapp.entity.Slip;
import com.smhrd.myapp.entity.SlipItem;
import com.smhrd.myapp.repository.OutboundRepository;
import com.smhrd.myapp.service.HospitalStaffService;
import com.smhrd.myapp.service.SlipService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

// receipts.html 전표 관리 화면용 - 조회는 관리자+직원 모두(세션 필요).
// 등록은 ocr_processor.py(전표 OCR 파이프라인)가 호출하는 자리라, 브라우저 세션이 없는
// 서버-서버 호출이라서 인증 없이 연다 (medicine-zones/camera/{id}, outbound 등과 같은 패턴).
@RestController
@RequestMapping("/api/slips")
public class SlipController {

    private final SlipService slipService;
    private final HospitalStaffService hospitalStaffService;
    private final OutboundRepository outboundRepository;

    public SlipController(
            SlipService slipService,
            HospitalStaffService hospitalStaffService,
            OutboundRepository outboundRepository
    ) {
        this.slipService = slipService;
        this.hospitalStaffService = hospitalStaffService;
        this.outboundRepository = outboundRepository;
    }

    private String resolveOwnerAdminId(HttpSession session) {

        String memberId = (String) session.getAttribute("memberId");

        if (memberId == null) {
            return null;
        }

        return hospitalStaffService.resolveActiveOwnerAdminId(memberId);
    }

    // 전표 목록 (최신순) - 화면 진입 시 목록 + 가장 최신 전표를 같이 보여주기 위해 씀
    @GetMapping
    public ResponseEntity<?> list(HttpSession session) {

        String ownerAdminId = resolveOwnerAdminId(session);

        if (ownerAdminId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        List<Map<String, Object>> result = slipService.listByAdmin(ownerAdminId).stream()
                .map(this::toSummaryMap)
                .collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    // 특정 전표 상세 ("보기" 클릭 시)
    @GetMapping("/{slipId}")
    public ResponseEntity<?> detail(@PathVariable String slipId, HttpSession session) {

        String ownerAdminId = resolveOwnerAdminId(session);

        if (ownerAdminId == null) {
            return ResponseEntity.status(401).body(Map.of("message", "로그인이 필요합니다."));
        }

        try {

            return ResponseEntity.ok(toDetailMap(slipService.getOwned(ownerAdminId, slipId)));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(404).body(Map.of("message", e.getMessage()));
        }
    }

    // 전표 등록 - ocr_processor.py가 호출 (인증 없음, 클래스 상단 주석 참고)
    @PostMapping
    public ResponseEntity<?> create(@RequestBody Map<String, Object> body) {

        try {

            String slipId = String.valueOf(body.get("slipId"));
            Long wardSeqId = Long.valueOf(String.valueOf(body.get("wardSeqId")));
            String requesterName = body.get("requesterName") != null ? String.valueOf(body.get("requesterName")) : null;
            LocalDate slipDate = body.get("slipDate") != null ? LocalDate.parse(String.valueOf(body.get("slipDate"))) : null;
            String imagePath = body.get("imagePath") != null ? String.valueOf(body.get("imagePath")) : null;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> items = (List<Map<String, Object>>) body.get("items");

            Slip saved = slipService.create(slipId, wardSeqId, requesterName, slipDate, imagePath, items);

            return ResponseEntity.ok(toDetailMap(saved));

        } catch (IllegalStateException e) {

            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));

        } catch (NumberFormatException | java.time.format.DateTimeParseException e) {

            return ResponseEntity.status(400).body(Map.of("message", "입력값을 확인해 주세요."));
        }
    }

    private Map<String, Object> toSummaryMap(Slip slip) {

        Map<String, Object> map = new HashMap<>();

        List<SlipItem> items = slipService.listItems(slip.getSlipId());

        map.put("slipId", slip.getSlipId());
        map.put("wardName", slip.getWard().getWardName());
        map.put("requesterName", slip.getRequesterName());
        map.put("slipDate", slip.getSlipDate());
        map.put("itemCount", items.size());
        map.put(
                "totalQty",
                items.stream().mapToLong(item -> item.getRequestQty() == null ? 0 : item.getRequestQty()).sum()
        );

        return map;
    }

    private Map<String, Object> toDetailMap(Slip slip) {

        Map<String, Object> map = new HashMap<>();

        List<Outbound> outbounds = outboundRepository.findBySlip_SlipId(slip.getSlipId());

        map.put("slipId", slip.getSlipId());
        map.put("wardName", slip.getWard().getWardName());
        map.put("requesterName", slip.getRequesterName());
        map.put("slipDate", slip.getSlipDate());
        map.put("imagePath", slip.getImagePath());

        map.put(
                "items",
                slipService.listItems(slip.getSlipId()).stream()
                        .map(item -> {

                            Map<String, Object> itemMap = new HashMap<>();

                            itemMap.put("medicineName", item.getMedicine().getMedicineName());
                            itemMap.put("requestQty", item.getRequestQty());

                            Outbound matched = outbounds.stream()
                                    .filter(o -> o.getMedicine().getMedicineId().equals(item.getMedicine().getMedicineId()))
                                    .findFirst()
                                    .orElse(null);

                            itemMap.put("outboundQty", matched != null ? matched.getOutboundQty() : null);
                            itemMap.put("abnormal", matched != null && "Y".equals(matched.getAbnormalYn()));

                            return itemMap;
                        })
                        .collect(Collectors.toList())
        );

        return map;
    }
}
