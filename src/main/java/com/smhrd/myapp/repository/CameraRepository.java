package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Camera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CameraRepository extends JpaRepository<Camera, Long> {

    List<Camera> findByAdmin_MemberId(String adminId);

    // 이 관리자의 카메라 중 특정 용도(MONITOR/OCR_SCAN)인 것들 - OCR_SCAN 1대 제한 처리에 사용
    List<Camera> findByAdmin_MemberIdAndCameraRole(String adminId, String cameraRole);

    // 카메라 이름 중복확인용 (같은 관리자 안에서만 유일하면 됨)
    long countByAdmin_MemberIdAndCameraName(String adminId, String cameraName);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 카메라 전체 삭제
    void deleteByAdmin_MemberId(String adminId);
}
