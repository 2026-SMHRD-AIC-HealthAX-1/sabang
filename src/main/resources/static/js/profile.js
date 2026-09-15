/*
===========================================
    개인정보 설정 화면

    1. 현재 회원 정보 표시
    2. 가입 정보가 없으면 안내 표시
    3. 이름 변경 및 저장
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
        // 가입 정보가 있으면 안내를 숨기고 입력 활성화
        byId("profileEmpty").hidden = !!account;
        byId("profileFields").disabled = !account;

        if (!account) {
            return;
        }

        // 입력창에 회원 정보 표시
        byId("profileName").value = account.name;
        byId("profileEmail").value = account.email;

        // 상단 프로필 요약 표시
        byId("profileDisplayName").textContent =
            account.name;

        byId("profileDisplayEmail").textContent =
            account.email;

        // 프로필 원 안에 이름의 첫 글자 표시
        byId("profileAvatar").textContent =
            account.name.slice(0, 1);

        // 가입일 표시
        const joined = new Date(account.joinedAt);

        if (Number.isNaN(joined.getTime())) {
            byId("profileJoined").textContent = "—";
        } else {
            byId("profileJoined").textContent =
                joined.toLocaleDateString("ko-KR");
        }

        // 약관 동의 상태 표시
        byId("profileAgreed").textContent =
            account.agreed ? "동의 완료" : "미확인";
    }

    /*
    ===========================================
        페이지를 열면 회원 정보 불러오기
    ===========================================
    */

    try {
        const account = FramAccount.current();

        render(account);
    } catch {
        render(null);

        byId("accountError").hidden = false;

        byId("accountError").textContent =
            "회원 정보를 읽지 못했습니다. "
            + "브라우저의 사이트 저장소 설정을 확인해 주세요.";
    }

    /*
    ===========================================
        변경사항 저장 버튼
    ===========================================
    */

    byId("profileForm").addEventListener(
        "submit",
        function (event) {
            // 저장 버튼을 눌러도 페이지가 새로고침되지 않도록 처리
            event.preventDefault();

            try {
                const newName = byId("profileName").value;

                // account-store.js의 이름 수정 기능 실행
                const updatedAccount =
                    FramAccount.updateName(newName);

                // 수정한 정보를 화면에도 반영
                render(updatedAccount);

                byId("profileMessage").textContent =
                    "변경사항을 이 브라우저에 저장했습니다.";
            } catch (error) {
                byId("profileMessage").textContent =
                    error.message
                    || "저장하지 못했습니다. 다시 시도해 주세요.";
            }
        }
    );
})();