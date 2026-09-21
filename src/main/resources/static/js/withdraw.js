/*
===========================================
    회원탈퇴

    확인 후 서버에 탈퇴 요청을 보내고,
    성공하면 index.html로 이동한다.
===========================================
*/

document.getElementById("withdrawBtn").addEventListener("click", async function() {

    // 관리자 여부(framVision.isAdmin)로 문구를 분기하지 않는다 - 구독 만료로 이 화면에 온 경우
    // common.js가 그 값을 이미 지워버려서 신뢰할 수 없고, 서버는 구독 상태와 무관하게 소유한 자원이
    // 있으면 항상 전부 정리하기 때문에(회원탈퇴 카스케이드) 문구도 항상 같은 내용으로 안내한다.
    const confirmMessage =
        "정말 탈퇴하시겠습니까?\n계정과 구독·권한 정보가 모두 삭제되며, 관리자 계정이라면 하위 직원 계정과 등록된 카메라·의약품·알림 등 관련 데이터도 모두 함께 삭제됩니다.\n되돌릴 수 없습니다.";

    const confirmed = confirm(confirmMessage);

    if (!confirmed) {
        return;
    }

    try {

        const response = await fetch("/api/auth/me", { method: "DELETE" });

        if (!response.ok) {
            alert("탈퇴에 실패했습니다. 다시 시도해 주세요.");
            return;
        }

        alert("탈퇴가 완료되었습니다.");

        window.location.href = "index.html";

    } catch (error) {

        alert("탈퇴에 실패했습니다. 다시 시도해 주세요.");
    }
});
