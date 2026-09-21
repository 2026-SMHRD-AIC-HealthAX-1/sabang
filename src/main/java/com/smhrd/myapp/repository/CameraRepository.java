package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Camera;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CameraRepository extends JpaRepository<Camera, Long> {

    List<Camera> findByAdmin_MemberId(String adminId);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자의 카메라 전체 삭제
    void deleteByAdmin_MemberId(String adminId);
}
