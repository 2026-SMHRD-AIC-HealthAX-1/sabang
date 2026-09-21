package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Camera;
import com.smhrd.myapp.entity.Medicine;
import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.repository.AlertRepository;
import com.smhrd.myapp.repository.CameraRepository;
import com.smhrd.myapp.repository.MedicineRepository;
import com.smhrd.myapp.repository.OutboundRepository;
import com.smhrd.myapp.repository.SlipItemRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

// 의약품 = 구역(Zone) 1:1. 약품을 추가하면 카메라 화면 위 구역이 함께 생기고,
// 삭제하면 구역과 의약품 데이터가 함께 사라진다.
@Service
public class MedicineZoneService {

    private final MedicineRepository medicineRepository;
    private final CameraRepository cameraRepository;
    private final MemberService memberService;
    private final AlertRepository alertRepository;
    private final OutboundRepository outboundRepository;
    private final SlipItemRepository slipItemRepository;

    public MedicineZoneService(
            MedicineRepository medicineRepository,
            CameraRepository cameraRepository,
            MemberService memberService,
            AlertRepository alertRepository,
            OutboundRepository outboundRepository,
            SlipItemRepository slipItemRepository
    ) {
        this.medicineRepository = medicineRepository;
        this.cameraRepository = cameraRepository;
        this.memberService = memberService;
        this.alertRepository = alertRepository;
        this.outboundRepository = outboundRepository;
        this.slipItemRepository = slipItemRepository;
    }

    public List<Medicine> listByAdmin(String adminId) {
        return medicineRepository.findByAdmin_MemberId(adminId);
    }
    
    // 특정 카메라에 연결된 의약품/구역 조회
    public List<Medicine> listByCamera(Long cameraId) {
        return medicineRepository.findByCamera_CameraId(cameraId);
    }
    

    private Camera requireOwnedCamera(String adminId, Long cameraId) {

        Camera camera = cameraRepository.findById(cameraId).orElse(null);

        if (camera == null || !camera.getAdmin().getMemberId().equals(adminId)) {
            throw new IllegalStateException("존재하지 않거나 권한이 없는 카메라입니다.");
        }

        return camera;
    }

    private Medicine requireOwnedMedicine(String adminId, Long medicineId) {

        Medicine medicine = medicineRepository.findById(medicineId).orElse(null);

        if (medicine == null || !medicine.getAdmin().getMemberId().equals(adminId)) {
            throw new IllegalStateException("존재하지 않거나 권한이 없는 구역입니다.");
        }

        return medicine;
    }

    // 약품추가: 새 의약품을 만들면서 바로 구역(카메라)에 연결.
    // 위치는 기본값으로 두고 화면에서 드래그해서 잡는다.
    // medicineId는 PK라 관리자 입력 없이 자동 계산.
    public Medicine create(
            String adminId,
            String medicineName,
            String highRiskYn,
            String manufacturer,
            LocalDate registerDate,
            Long cameraId,
            Long minQty
    ) {

        Camera camera = requireOwnedCamera(adminId, cameraId);

        Member admin = memberService.findById(adminId);

        if (admin == null) {
            throw new IllegalStateException("존재하지 않는 회원입니다.");
        }

        Medicine medicine = new Medicine();
        medicine.setAdmin(admin);
        medicine.setMedicineName(medicineName);
        medicine.setHighRiskYn(highRiskYn != null && !highRiskYn.isBlank() ? highRiskYn : "N");
        // Oracle은 VARCHAR2에 빈 문자열("")을 넣으면 NULL로 취급해서 NOT NULL 제약에 걸림
        medicine.setManufacturer(manufacturer != null && !manufacturer.isBlank() ? manufacturer : "미상");
        medicine.setRegisterDate(registerDate != null ? registerDate : LocalDate.now());
        medicine.setCamera(camera);
        medicine.setMinQty(minQty);
        medicine.setRegionX(35.0);
        medicine.setRegionY(35.0);
        medicine.setRegionWidth(20.0);
        medicine.setRegionHeight(20.0);

        return medicineRepository.save(medicine);
    }

    // 전달된 값만 반영 (일부 필드만 와도 됨) - 저장 버튼에서 일괄 호출
    // 최소 수량은 "비우기(NULL)"도 값이라서, 요청에 minQty 항목이 있었는지(minQtyGiven)를 따로 받는다.
    public Medicine update(
            String adminId,
            Long medicineId,
            String medicineName,
            String highRiskYn,
            String manufacturer,
            LocalDate registerDate,
            Long cameraId,
            Double regionX,
            Double regionY,
            Double regionWidth,
            Double regionHeight,
            boolean minQtyGiven,
            Long minQty
    ) {

        Medicine medicine = requireOwnedMedicine(adminId, medicineId);

        if (medicineName != null && !medicineName.isBlank()) {
            medicine.setMedicineName(medicineName);
        }

        if (highRiskYn != null && !highRiskYn.isBlank()) {
            medicine.setHighRiskYn(highRiskYn);
        }

        if (manufacturer != null && !manufacturer.isBlank()) {
            medicine.setManufacturer(manufacturer);
        }

        if (registerDate != null) {
            medicine.setRegisterDate(registerDate);
        }

        if (cameraId != null) {
            medicine.setCamera(requireOwnedCamera(adminId, cameraId));
        }

        if (regionX != null) {
            medicine.setRegionX(regionX);
        }

        if (regionY != null) {
            medicine.setRegionY(regionY);
        }

        if (regionWidth != null) {
            medicine.setRegionWidth(regionWidth);
        }

        if (regionHeight != null) {
            medicine.setRegionHeight(regionHeight);
        }

        if (minQtyGiven) {
            medicine.setMinQty(minQty);
        }

        return medicineRepository.save(medicine);
    }

    // 알림/출고기록/전표품목이 이 의약품을 NOT NULL FK로 참조하고 있어서, 사용 이력이 있으면
    // 삭제 전에 막아야 한다 (안 막으면 FK 제약 위반 예외가 그대로 터짐)
    public void delete(String adminId, Long medicineId) {

        Medicine medicine = requireOwnedMedicine(adminId, medicineId);

        if (alertRepository.countByMedicine_MedicineId(medicineId) > 0
                || outboundRepository.countByMedicine_MedicineId(medicineId) > 0
                || slipItemRepository.countByMedicine_MedicineId(medicineId) > 0) {
            throw new IllegalStateException("이 의약품은 사용 중(알림·출고·전표 기록)이라 삭제할 수 없습니다.");
        }

        medicineRepository.delete(medicine);
    }
}
