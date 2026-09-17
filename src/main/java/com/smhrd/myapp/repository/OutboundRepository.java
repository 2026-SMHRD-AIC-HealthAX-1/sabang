package com.smhrd.myapp.repository;

import com.smhrd.myapp.entity.Outbound;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundRepository extends JpaRepository<Outbound, Long> {
}
