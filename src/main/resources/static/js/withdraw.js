/*
===========================================
    회원탈퇴

    확인 후 서버에 탈퇴 요청을 보내고,
    성공하면 index.html로 이동한다.
===========================================
*/

document.getElementById("withdrawBtn").addEventListener("click", async function() {

    const confirmed = confirm(
        "정말 탈퇴하시겠습니까?\n계정과 구독·권한 정보가 모두 삭제되며 되돌릴 수 없습니다."
    );

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
