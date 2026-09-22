package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Medicine;
import com.smhrd.myapp.entity.Slip;
import com.smhrd.myapp.entity.SlipItem;
import com.smhrd.myapp.entity.SlipItemId;
import com.smhrd.myapp.entity.Ward;
import com.smhrd.myapp.repository.MedicineRepository;
import com.smhrd.myapp.repository.SlipItemRepository;
import com.smhrd.myapp.repository.SlipRepository;
import com.smhrd.myapp.repository.WardRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

// receipts.html 전표 관리 화면용. 지금은 OCR이 아직 없어서 더미데이터/수동입력 기준으로 동작하고,
// 나중에 OCR 파이프라인이 붙으면 create()를 그 파이프라인이 호출하게 된다.
@Service
public class SlipService {

    private final SlipRepository slipRepository;
    private final SlipItemRepository slipItemRepository;
    private final MedicineRepository medicineRepository;
    private final WardRepository wardRepository;

    public SlipService(
            SlipRepository slipRepository,
            SlipItemRepository slipItemRepository,
            MedicineRepository medicineRepository,
            WardRepository wardRepository
    ) {
        this.slipRepository = slipRepository;
        this.slipItemRepository = slipItemRepository;
        this.medicineRepository = medicineRepository;
        this.wardRepository = wardRepository;
    }

    public List<Slip> listByAdmin(String ownerAdminId) {
        return slipRepository.findByWard_Admin_MemberIdOrderByCreatedAtDesc(ownerAdminId);
    }

    // 목록의 가장 첫 번째(= 가장 최신) 전표를 돌려주는 메서드. 목록이 비어있으면 null.
    // (사용 안 함) receipts.html에서 "가장 최신 전표 기본 표시"를 프론트(receipts.js)가
    // listByAdmin()으로 받은 목록의 0번째로 처리해서, 백엔드에서 따로 골라줄 필요가 없어졌음.
    // 아무도 호출하지 않는 죽은 코드라 주석 처리함 - 나중에 서버 쪽에서 "최신 전표만" 조회가
    // 필요해지면(예: 별도 API) 다시 살리면 됨.
    // public Slip getLatest(String ownerAdminId) {
    //
    //     List<Slip> slips = listByAdmin(ownerAdminId);
    //
    //     return slips.isEmpty() ? null : slips.get(0);
    // }

    public Slip getOwned(String ownerAdminId, String slipId) {

        Slip slip = slipRepository.findById(slipId).orElse(null);

        if (slip == null || !slip.getWard().getAdmin().getMemberId().equals(ownerAdminId)) {
            throw new IllegalStateException("존재하지 않거나 권한이 없는 전표입니다.");
        }

        return slip;
    }

    public List<SlipItem> listItems(String slipId) {
        return slipItemRepository.findById_SlipIdOrderById_ItemNoAsc(slipId);
    }

    // 전표 생성: OCR 파이프라인 또는 관리자 수동입력에서 호출.
    // items: [{ "medicineId": 1, "requestQty": 3 }, ...]
    @Transactional
    public Slip create(
            String slipId,
            Long wardSeqId,
            String requesterName,
            LocalDate slipDate,
            String imagePath,
            List<Map<String, Object>> items
    ) {

        if (slipId == null || slipId.isBlank()) {
            throw new IllegalStateException("전표번호를 입력해 주세요.");
        }

        if (slipRepository.existsById(slipId)) {
            throw new IllegalStateException("이미 등록된 전표번호입니다.");
        }

        Ward ward = wardRepository.findById(wardSeqId).orElse(null);

        if (ward == null) {
            throw new IllegalStateException("존재하지 않는 병동입니다.");
        }

        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("전표 품목을 1개 이상 입력해 주세요.");
        }

        Slip slip = new Slip();
        slip.setSlipId(slipId.trim());
        slip.setWard(ward);
        slip.setRequesterName(requesterName != null && !requesterName.isBlank() ? requesterName.trim() : "미상");
        slip.setSlipDate(slipDate != null ? slipDate : LocalDate.now());
        slip.setImagePath(imagePath);
        slip.setCreatedAt(LocalDateTime.now());

        slipRepository.save(slip);

        int itemNo = 1;

        for (Map<String, Object> item : items) {

            Long medicineId = Long.valueOf(String.valueOf(item.get("medicineId")));
            Long requestQty = Long.valueOf(String.valueOf(item.get("requestQty")));

            Medicine medicine = medicineRepository.findById(medicineId).orElse(null);

            if (medicine == null) {
                throw new IllegalStateException("존재하지 않는 의약품입니다: " + medicineId);
            }

            SlipItem slipItem = new SlipItem();
            slipItem.setId(new SlipItemId(slip.getSlipId(), itemNo));
            slipItem.setSlip(slip);
            slipItem.setMedicine(medicine);
            slipItem.setRequestQty(requestQty);

            slipItemRepository.save(slipItem);

            itemNo++;
        }

        return slip;
    }
}
