/*
===========================================
    개인정보 설정 화면

    1. 현재 회원 정보 표시 (/api/auth/me)
    2. 이름 변경 및 저장 (PUT /api/auth/me)

    이 페이지는 common.js가 먼저 로그인 여부를 확인해서
    로그인 안 됐으면 index.html로 돌려보내므로,
    여기 도달했다면 계정 정보는 항상 존재한다고 본다.
===========================================
*/

(() => {
    "use strict";

    // HTML 요소를 ID로 가져오기
    function byId(id) {
        return document.getElementById(id);
    }

    /*
    ===========================================
        회원 정보를 화면에 표시
    ===========================================
    */

    function render(account) {
        byId("profileName").value = account.memberName;
        byId("profileEmail").value = account.email;

        // 상단 프로필 요약 표시
        byId("profileDisplayName").textContent =
            account.memberName;

        byId("profileDisplayEmail").textContent =
            account.email;

        // 프로필 원 안에 이름의 첫 글자 표시
        byId("profileAvatar").textContent =
            account.memberName.slice(0, 1);
    }

    /*
    ===========================================
        페이지를 열면 회원 정보 불러오기
    ===========================================
    */

    async function load() {

        try {

            const response = await fetch("/api/auth/me");

            if (!response.ok) {
                throw new Error("회원 정보를 불러오지 못했습니다.");
            }

            const account = await response.json();

            render(account);

        } catch {
            byId("accountError").hidden = false;

            byId("accountError").textContent =
                "회원 정보를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.";
        }
    }

    load();

    /*
    ===========================================
        변경사항 저장 버튼
    ===========================================
    */

    byId("profileForm").addEventListener(
        "submit",
        async function (event) {
            // 저장 버튼을 눌러도 페이지가 새로고침되지 않도록 처리
            event.preventDefault();

            try {
                const newName = byId("profileName").value;

                const response = await fetch("/api/auth/me", {
                    method: "PUT",
                    headers: { "Content-Type": "application/json" },
                    body: JSON.stringify({ memberName: newName })
                });

                const result = await response.json();

                if (!response.ok) {
                    throw new Error(result.message || "저장하지 못했습니다. 다시 시도해 주세요.");
                }

                render(result);

                // 헤더의 이름도 바로 바꾼다
                sessionStorage.setItem("framVision.memberName", result.memberName);

                if (typeof setHeaderUserName === "function") {
                    setHeaderUserName(result.memberName);
                }

                byId("profileMessage").textContent =
                    "변경사항이 저장되었습니다.";
            } catch (error) {
                byId("profileMessage").textContent =
                    error.message
                    || "저장하지 못했습니다. 다시 시도해 주세요.";
            }
        }
    );
})();
