package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Camera;
import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.repository.CameraRepository;
import com.smhrd.myapp.repository.MedicineRepository;
import org.springframework.stereotype.Service;

import java.util.List;

// CameraService(파이썬 FastAPI 프로세스 실행)와는 별개로,
// CAMERA 테이블 CRUD(등록된 카메라 목록 관리)만 담당
@Service
public class CameraRegistryService {

    private final CameraRepository cameraRepository;
    private final MemberService memberService;
    private final MedicineRepository medicineRepository;

    public CameraRegistryService(
            CameraRepository cameraRepository,
            MemberService memberService,
            MedicineRepository medicineRepository
    ) {
        this.cameraRepository = cameraRepository;
        this.memberService = memberService;
        this.medicineRepository = medicineRepository;
    }

    public List<Camera> listByAdmin(String adminId) {
        return cameraRepository.findByAdmin_MemberId(adminId);
    }

    // 카메라 등록: STREAM_URL은 CAMERA_ID - 1을 파이썬 카메라 인덱스로 사용해서 자동 생성
    // (지금은 노트북 한계로 카메라가 2대뿐이지만, 카메라가 늘어나면 이 규칙 그대로 이어짐)
    public Camera add(String adminId, String cameraName) {

        Member admin = memberService.findById(adminId);

        if (admin == null) {
            throw new IllegalStateException("존재하지 않는 회원입니다.");
        }

        long nextId = cameraRepository.findMaxId() + 1;

        Camera camera = new Camera();
        camera.setCameraId(nextId);
        camera.setAdmin(admin);
        camera.setCameraName(cameraName);
        camera.setConnectionStatus("0");
        camera.setStreamUrl("http://localhost:8000/video/" + (nextId - 1));

        return cameraRepository.save(camera);
    }

    public void delete(String adminId, Long cameraId) {

        Camera camera = cameraRepository.findById(cameraId).orElse(null);

        if (camera == null || !camera.getAdmin().getMemberId().equals(adminId)) {
            throw new IllegalStateException("존재하지 않거나 권한이 없는 카메라입니다.");
        }

        if (medicineRepository.countByCamera_CameraId(cameraId) > 0) {
            throw new IllegalStateException("이 카메라를 사용 중인 의약품 구역이 있어 삭제할 수 없습니다. 먼저 다른 카메라로 이동해 주세요.");
        }

        cameraRepository.delete(camera);
    }
}
