package com.smhrd.myapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

// 회원(관리자) 정보를 관리하는 JPA Entity
@Entity

// Oracle DB의 MEMBER 테이블과 연결
@Table(name = "MEMBER")
public class Member {

    // 아이디
    // 관리자 로그인 ID
    // DB : MEMBER_ID VARCHAR2(50) PRIMARY KEY
    @Id
    @Column(name = "MEMBER_ID", length = 50)
    private String memberId;


    // 비밀번호
    // DB : PASSWORD VARCHAR2(100), NULL 허용 (가입 시 앱에서 항상 채워서 저장함)
    @Column(name = "PASSWORD", length = 100)
    private String password;


    // 이메일
    // DB : EMAIL VARCHAR2(100), NULL 허용 (가입 시 앱에서 항상 채워서 저장함)
    @Column(name = "EMAIL", length = 100)
    private String email;


    // 전화번호
    // DB : PHONE VARCHAR2(20), NULL 허용 (가입 시 앱에서 항상 채워서 저장함)
    @Column(name = "PHONE", length = 20)
    private String phone;


    // 이름
    // DB : MEMBER_NAME VARCHAR2(50), NULL 허용 (가입 시 앱에서 항상 채워서 저장함)
    @Column(name = "MEMBER_NAME", length = 50)
    private String memberName;


    // 글자크기
    // 관리자 화면의 글자 크기 설정
    @Column(name = "FONT_SIZE")
    private Integer fontSize;


    // 화면테마
    // Light / Dark 등의 화면 설정
    @Column(name = "SCREEN_THEME", length = 20)
    private String screenTheme;


    // 알림소리 ID
    // MEMBER.SOUND_ID →
    // SOUND.SOUND_ID를 참조하는 Foreign Key
    @ManyToOne
    @JoinColumn(
        name = "SOUND_ID",
        referencedColumnName = "SOUND_ID"
    )
    private Sound sound;


    // Getter / Setter
    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getMemberName() {
        return memberName;
    }

    public void setMemberName(String memberName) {
        this.memberName = memberName;
    }

    public Integer getFontSize() {
        return fontSize;
    }

    public void setFontSize(Integer fontSize) {
        this.fontSize = fontSize;
    }

    public String getScreenTheme() {
        return screenTheme;
    }

    public void setScreenTheme(String screenTheme) {
        this.screenTheme = screenTheme;
    }

    public Sound getSound() {
        return sound;
    }

    public void setSound(Sound sound) {
        this.sound = sound;
    }
}