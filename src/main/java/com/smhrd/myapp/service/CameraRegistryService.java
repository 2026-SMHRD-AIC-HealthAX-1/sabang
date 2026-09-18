package com.smhrd.myapp.service;

import com.smhrd.myapp.entity.Camera;
import com.smhrd.myapp.entity.Member;
import com.smhrd.myapp.repository.CameraRepository;
import com.smhrd.myapp.repository.MedicineRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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

    // 카메라 등록: STREAM_URL의 파이썬 카메라 인덱스는 "이 관리자가 쓰고 있는 인덱스 중
    // 비어있는 가장 작은 번호"로 매긴다 (관리자별로 0부터 시작, 삭제로 생긴 빈 자리도 재사용).
    // CAMERA_ID(PK)는 이 인덱스와 무관하게 시스템 전체에서 계속 유일한 값을 씀.
    // (지금은 노트북 한계로 카메라가 2대뿐이라 인덱스 2 이상은 화면이 안 나옴)
    public Camera add(String adminId, String cameraName) {

        Member admin = memberService.findById(adminId);

        if (admin == null) {
            throw new IllegalStateException("존재하지 않는 회원입니다.");
        }

        long nextId = cameraRepository.findMaxId() + 1;
        int streamIndex = nextAvailableStreamIndex(adminId);

        Camera camera = new Camera();
        camera.setCameraId(nextId);
        camera.setAdmin(admin);
        camera.setCameraName(cameraName);
        camera.setConnectionStatus("0");
        camera.setStreamUrl("http://localhost:8000/video/" + streamIndex);

        return cameraRepository.save(camera);
    }

    // 이 관리자의 카메라들이 쓰고 있는 인덱스를 모아서, 비어있는 가장 작은 0 이상 정수를 찾는다
    private int nextAvailableStreamIndex(String adminId) {

        Set<Integer> used = cameraRepository.findByAdmin_MemberId(adminId).stream()
                .map(Camera::getStreamUrl)
                .filter(url -> url != null && url.contains("/"))
                .map(url -> {
                    try {
                        return Integer.parseInt(url.substring(url.lastIndexOf('/') + 1));
                    } catch (NumberFormatException e) {
                        return -1;
                    }
                })
                .collect(Collectors.toSet());

        int index = 0;

        while (used.contains(index)) {
            index++;
        }

        return index;
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
