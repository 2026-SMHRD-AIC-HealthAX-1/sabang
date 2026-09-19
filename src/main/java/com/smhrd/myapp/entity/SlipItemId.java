package com.smhrd.myapp.entity;

import java.io.Serializable;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

// SLIP_ITEM의 복합 기본키 (전표번호 + 품목번호)
@Embeddable
public class SlipItemId implements Serializable {

    // 전표번호 - SLIP.SLIP_ID
    @Column(name = "SLIP_ID", length = 100)
    private String slipId;

    // 품목번호 - 전표 안에서의 줄 번호 (No.1, 2, 3...)
    @Column(name = "ITEM_NO")
    private Integer itemNo;

    public SlipItemId() {
    }

    public SlipItemId(String slipId, Integer itemNo) {
        this.slipId = slipId;
        this.itemNo = itemNo;
    }

    public String getSlipId() {
        return slipId;
    }

    public void setSlipId(String slipId) {
        this.slipId = slipId;
    }

    public Integer getItemNo() {
        return itemNo;
    }

    public void setItemNo(Integer itemNo) {
        this.itemNo = itemNo;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof SlipItemId other)) {
            return false;
        }
        return Objects.equals(slipId, other.slipId) && Objects.equals(itemNo, other.itemNo);
    }

    @Override
    public int hashCode() {
        return Objects.hash(slipId, itemNo);
    }
}
