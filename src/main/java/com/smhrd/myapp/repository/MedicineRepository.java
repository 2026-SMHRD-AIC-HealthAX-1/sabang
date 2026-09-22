package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    // 관리자(=병원)가 등록한 의약품 전체 (카메라가 없는 의약품 포함)
    List<Medicine> findByAdmin_MemberId(String adminId);

    long countByCamera_CameraId(Long cameraId);
    
    // 특정 카메라에 연결된 의약품/구역 조회
    List<Medicine> findByCamera_CameraId(Long cameraId);

    // 실시간 카운트 보고(재고부족 판단)용: 이 카메라에서 이 이름의 구역이 어느 의약품인지
    List<Medicine> findByCamera_CameraIdAndMedicineName(Long cameraId, String medicineName);

    // 회원탈퇴(관리자) 시 정리용: 이 관리자가 등록한 의약품 전체 삭제
    void deleteByAdmin_MemberId(String adminId);
}
