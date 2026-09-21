package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Camera;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CameraRepository extends JpaRepository<Camera, Long> {

    // CAMERA_ID가 시퀀스/트리거로 자동 채워지지 않아 저장 전 직접 계산해야 함
    @Query("SELECT COALESCE(MAX(c.cameraId), 0) FROM Camera c")
    Long findMaxId();

    List<Camera> findByAdmin_MemberId(String adminId);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 카메라 전체 삭제
    void deleteByAdmin_MemberId(String adminId);
}
