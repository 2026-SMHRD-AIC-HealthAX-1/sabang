/*
===========================================
    회원 정보 저장소 — 화면 데모용

    저장하는 정보:
    이름, 이메일, 가입일, 약관 동의 여부

    비밀번호와 카드번호는 저장하지 않습니다.
    실제 로그인 인증은 Spring에서 연결해야 합니다.
===========================================
*/

(() => {
    "use strict";

    // 가입 정보 저장에 사용하는 이름
    const accountKey = "framVision.demoAccounts.v1";

    // 현재 선택한 데모 계정 저장에 사용하는 이름
    const currentKey = "framVision.demoCurrentEmail.v1";

    /*
    ===========================================
        이메일 정리
        앞뒤 공백 제거 + 소문자로 통일
    ===========================================
    */

    function normalize(email) {
        return String(email || "")
            .trim()
            .toLowerCase();
    }

    /*
    ===========================================
        저장된 회원 목록 불러오기
    ===========================================
    */

    function readAccounts() {
        const saved = localStorage.getItem(accountKey);

        const accounts = JSON.parse(saved || "[]");

        if (!Array.isArray(accounts)) {
            return [];
        }

        return accounts.filter(account => {
            return account
                && typeof account.email === "string";
        });
    }

    /*
    ===========================================
        회원가입 정보 저장
    ===========================================
    */

    function register({ name, email, agreed }) {
        const accounts = readAccounts();

        const address = normalize(email);

        if (!name.trim() || !address || !agreed) {
            throw new Error(
                "가입 정보를 확인해 주세요."
            );
        }

        // 같은 이메일로 중복 가입했는지 확인
        const alreadyRegistered = accounts.some(account => {
            return account.email === address;
        });

        if (alreadyRegistered) {
            throw new Error(
                "이 브라우저에 이미 등록된 이메일입니다. "
                + "로그인 화면을 이용해 주세요."
            );
        }

        const account = {
            name: name.trim(),
            email: address,
            joinedAt: new Date().toISOString(),
            agreed: true
        };

        accounts.push(account);

        localStorage.setItem(
            accountKey,
            JSON.stringify(accounts)
        );
    }

    /*
    ===========================================
        데모 로그인 계정 선택

        실제 비밀번호 인증이 아닙니다.
        입력한 이메일과 일치하는 가입 정보를
        선택하는 기능입니다.
    ===========================================
    */

    function selectDemoAccount(email) {
        // 이전 계정 선택 해제
        sessionStorage.removeItem(currentKey);

        const address = normalize(email);

        const accounts = readAccounts();

        const found = accounts.find(account => {
            return account.email === address;
        });

        if (found) {
            sessionStorage.setItem(
                currentKey,
                address
            );
        }
    }

    /*
    ===========================================
        현재 선택한 회원 정보 가져오기
    ===========================================
    */

    function current() {
        const email = sessionStorage.getItem(currentKey);

        const accounts = readAccounts();

        const account = accounts.find(item => {
            return item.email === email;
        });

        return account || null;
    }

    /*
    ===========================================
        개인정보 화면에서 이름 변경
    ===========================================
    */

    function updateName(name) {
        const value = name.trim();

        if (!value || value.length > 50) {
            throw new Error(
                "이름을 1~50자로 입력해 주세요."
            );
        }

        const email = sessionStorage.getItem(currentKey);

        const accounts = readAccounts();

        const account = accounts.find(item => {
            return item.email === email;
        });

        if (!account) {
            throw new Error(
                "회원가입 후 같은 이메일로 로그인해 주세요."
            );
        }

        account.name = value;

        localStorage.setItem(
            accountKey,
            JSON.stringify(accounts)
        );

        return account;
    }

    /*
    ===========================================
        다른 JavaScript 파일에서 사용하도록 공개
    ===========================================
    */

    window.FramAccount = Object.freeze({
        register,
        selectDemoAccount,
        current,
        updateName
    });
})();