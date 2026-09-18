package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Medicine;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface MedicineRepository extends JpaRepository<Medicine, Long> {

    // MEDICINE_ID가 시퀀스/트리거로 자동 채워지지 않아 저장 전 직접 계산해야 함
    @Query("SELECT COALESCE(MAX(m.medicineId), 0) FROM Medicine m")
    Long findMaxId();

    List<Medicine> findByCamera_Admin_MemberId(String adminId);

    long countByCamera_CameraId(Long cameraId);
}
