package com.smhrd.myapp.controller;

import com.smhrd.myapp.service.OutboundService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// camera.py가 반출 결과(전표 요청수량과 실제 세그먼트 반출수량 비교)를 보고하는 자리.
// 브라우저 세션이 없는 서버-서버 호출이라 인증 없이 연다 (medicine-zones/camera/{id}와 같은 패턴).
@RestController
@RequestMapping("/api/outbound")
public class OutboundController {

    private final OutboundService outboundService;

    public OutboundController(OutboundService outboundService) {
        this.outboundService = outboundService;
    }

    // body: { slipId, medicineId, outboundQty }
    @PostMapping
    public ResponseEntity<?> report(@RequestBody Map<String, Object> body) {

        try {

            String slipId = String.valueOf(body.get("slipId"));
            Long medicineId = Long.valueOf(String.valueOf(body.get("medicineId")));
            Long outboundQty = Long.valueOf(String.valueOf(body.get("outboundQty")));

            Map<String, Object> result = outboundService.reportDispense(slipId, medicineId, outboundQty);

            return ResponseEntity.ok(result);

        } catch (IllegalStateException e) {

            return ResponseEntity.status(400).body(Map.of("message", e.getMessage()));

        } catch (NumberFormatException e) {

            return ResponseEntity.status(400).body(Map.of("message", "입력값을 확인해 주세요."));
        }
    }
}
