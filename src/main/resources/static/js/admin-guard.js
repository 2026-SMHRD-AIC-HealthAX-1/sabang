/*
===========================================
    관리자 전용 페이지 접근 제한

    이상 알림 / 사용자 관리 페이지는 관리자(유효 구독 보유)만
    접근 가능하다. 관리자가 아니면 대시보드로 돌려보낸다.
===========================================
*/

(async () => {

    try {

        const response = await fetch("/api/auth/me");

        if (!response.ok) {
            window.location.replace("dashboard.html");
            return;
        }

        const me = await response.json();

        if (!me.hasAccess) {
            // 구독 만료 등으로 대시보드 접근 자체가 막힌 경우 안내 화면으로 바로 보낸다
            window.location.replace("guide.html");
            return;
        }

        if (!me.isAdmin) {
            window.location.replace("dashboard.html");
        }

    } catch (error) {
        window.location.replace("dashboard.html");
    }
})();
