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

    private static final String ROLE_MONITOR = "MONITOR";
    private static final String ROLE_OCR_SCAN = "OCR_SCAN";

    // 카메라 등록: STREAM_URL의 파이썬 카메라 인덱스는 "이 관리자가 쓰고 있는 인덱스 중
    // 비어있는 가장 작은 번호"로 매긴다 (관리자별로 0부터 시작, 삭제로 생긴 빈 자리도 재사용).
    // CAMERA_ID(PK)는 이 인덱스와 무관하게 시스템 전체에서 계속 유일한 값을 씀.
    // 병원(관리자)마다 자기 서버 + 카메라 세트를 따로 운영하는 배포 구조라 대수 제한은 두지 않는다.
    // (지금 캠퍼스 테스트 DB에서 여러 admin이 물리 웹캠 2대짜리 노트북 하나를 같이 쓰고 있는 건
    // 테스트 환경 특성일 뿐, 실제 병원 배포에서는 병원마다 카메라 대수가 다를 수 있다)
    // cameraRole이 OCR_SCAN이면, 관리자당 1대 제한이라 기존 OCR_SCAN 카메라는 자동으로 MONITOR로 내린다.
    public Camera add(String adminId, String cameraName, String cameraRole) {

        Member admin = memberService.findById(adminId);

        if (admin == null) {
            throw new IllegalStateException("존재하지 않는 회원입니다.");
        }

        String name = cameraName == null ? "" : cameraName.trim();

        if (cameraRepository.countByAdmin_MemberIdAndCameraName(adminId, name) > 0) {
            throw new IllegalStateException("이미 같은 이름의 카메라가 있습니다. 다른 이름을 입력해 주세요.");
        }

        String role = normalizeRole(cameraRole);

        if (ROLE_OCR_SCAN.equals(role)) {
            demoteExistingOcrScan(adminId);
        }

        int streamIndex = nextAvailableStreamIndex(adminId);

        Camera camera = new Camera();
        camera.setAdmin(admin);
        camera.setCameraName(name);
        camera.setConnectionStatus("0");
        camera.setStreamUrl("http://localhost:8000/video/" + streamIndex);
        camera.setCameraRole(role);

        return cameraRepository.save(camera);
    }

    // 카메라 용도 변경 - OCR_SCAN으로 바꾸면 기존 OCR_SCAN 카메라는 자동으로 MONITOR로 내려간다.
    // OCR 스캔용 카메라엔 의약품 구역을 같이 둘 수 없어서, 이미 구역이 설정된 카메라는
    // OCR_SCAN으로 못 바꾸게 막는다 (구역을 다른 카메라로 옮긴 뒤 다시 시도해야 함).
    public Camera setRole(String adminId, Long cameraId, String cameraRole) {

        Camera camera = cameraRepository.findById(cameraId).orElse(null);

        if (camera == null || !camera.getAdmin().getMemberId().equals(adminId)) {
            throw new IllegalStateException("존재하지 않거나 권한이 없는 카메라입니다.");
        }

        String role = normalizeRole(cameraRole);

        if (ROLE_OCR_SCAN.equals(role)) {

            if (medicineRepository.countByCamera_CameraId(cameraId) > 0) {
                throw new IllegalStateException(
                        "이 카메라에는 이미 설정된 의약품 구역이 있어 OCR 스캔용으로 바꿀 수 없습니다. "
                        + "구역 설정에서 다른 카메라로 옮긴 뒤 다시 시도해 주세요."
                );
            }

            demoteExistingOcrScan(adminId);
        }

        camera.setCameraRole(role);

        return cameraRepository.save(camera);
    }

    private String normalizeRole(String cameraRole) {

        if (!ROLE_MONITOR.equals(cameraRole) && !ROLE_OCR_SCAN.equals(cameraRole)) {
            return ROLE_MONITOR;
        }

        return cameraRole;
    }

    private void demoteExistingOcrScan(String adminId) {

        List<Camera> existing = cameraRepository.findByAdmin_MemberIdAndCameraRole(adminId, ROLE_OCR_SCAN);

        for (Camera camera : existing) {
            camera.setCameraRole(ROLE_MONITOR);
            cameraRepository.save(camera);
        }
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
