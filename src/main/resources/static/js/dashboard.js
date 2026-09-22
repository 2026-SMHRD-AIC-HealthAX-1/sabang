/*
===========================================
    대시보드 화면

    다른 화면들(알림/전표/카메라/통계)의 데이터를 한 번씩 불러와서
    "한눈에 보기" 카드 4개로 요약해서 보여준다. 각 카드는 해당 화면으로 링크만 걸어주고,
    실제 처리(확인 처리, 전표 등록 등)는 각자의 화면에서 한다.

    이상(MISMATCH) 알림과 출고 이상건수는 관리자만 볼 수 있다 - 서버가 이미
    직원 세션에는 그 데이터를 아예 안 주지만(권한 분리), 화면에서도 라벨 자체를
    관리자에게만 보여주도록 한 번 더 가린다.
===========================================
*/

const isAdmin = sessionStorage.getItem("framVision.isAdmin") === "true";

document.querySelectorAll(".admin-only-inline").forEach(el => {
    el.hidden = !isAdmin;
});

const TYPE_LOW_STOCK = "LOW_STOCK";
const STATUS_PENDING = "PENDING";

function formatTime(value) {
    return new Date(value).toLocaleString("ko-KR");
}


/*
===========================
    미처리 알림 카드
===========================
*/

async function loadAlertCard() {

    const summary = document.getElementById("dashAlertSummary");
    const list = document.getElementById("dashAlertList");

    let alerts = [];

    try {
        const response = await fetch("/api/alerts");
        alerts = response.ok ? await response.json() : [];
    } catch (error) {
        alerts = [];
    }

    const pending = alerts.filter(alert => alert.processStatus === STATUS_PENDING);
    const lowStockCount = pending.filter(alert => alert.alertType === TYPE_LOW_STOCK).length;
    const mismatchCount = pending.length - lowStockCount;

    summary.innerHTML = `
        ${isAdmin ? `<span><strong>${mismatchCount}</strong>${t("countUnit")} ${t("dashAbnormalLabel")}</span>` : ""}
        <span><strong>${lowStockCount}</strong>${t("countUnit")} ${t("dashLowStockLabel")}</span>
    `;

    if (pending.length === 0) {
        list.innerHTML = `<li class="dash-empty">${t("dashNoAlertMessage")}</li>`;
        return;
    }

    list.innerHTML = pending.slice(0, 5).map(alert => `
        <li>
            <span class="dash-row-top">
                <span>${alert.medicineName}</span>
                <span class="dash-row-badge ${alert.alertType === TYPE_LOW_STOCK ? "low-stock" : ""}">
                    ${alert.alertType === TYPE_LOW_STOCK ? t("dashLowStockLabel") : t("dashAbnormalLabel")}
                </span>
            </span>
            <span class="dash-row-sub">${alert.alertContent} · ${formatTime(alert.alertTime)}</span>
        </li>
    `).join("");
}


/*
===========================
    최근 전표 카드
===========================
*/

async function loadSlipCard() {

    const body = document.getElementById("dashSlipBody");

    let slips = [];

    try {
        const response = await fetch("/api/slips");
        slips = response.ok ? await response.json() : [];
    } catch (error) {
        slips = [];
    }

    if (slips.length === 0) {
        body.innerHTML = `<p class="dash-empty">${t("noSlipMessage")}</p>`;
        return;
    }

    const latest = slips[0];

    body.innerHTML = `
        <div class="dash-slip-id">${latest.slipId}</div>
        <div class="dash-slip-meta">${latest.slipDate || "-"} · ${latest.wardName} · ${t("colStaff")} ${latest.requesterName}</div>
        <div class="dash-slip-items">${latest.itemCount}${t("unitSpecies")} / ${latest.totalQty}${t("unitPieces")}</div>
    `;
}


/*
===========================
    카메라 현황 카드
===========================
*/

async function loadCameraCard() {

    const body = document.getElementById("dashCameraBody");

    let cameras = [];

    try {
        const response = await fetch("/api/cameras");
        cameras = response.ok ? await response.json() : [];
    } catch (error) {
        cameras = [];
    }

    if (cameras.length === 0) {
        body.innerHTML = `<p class="dash-empty">${t("cameraEmptyMessage")}</p>`;
        return;
    }

    const monitorCount = cameras.filter(camera => camera.cameraRole !== "OCR_SCAN").length;
    const ocrCount = cameras.length - monitorCount;

    body.innerHTML = `
        <div class="dash-stat">
            <div class="dash-stat-value">${cameras.length}${t("unitCameras")}</div>
            <div class="dash-stat-label">${t("dashCameraStatusTitle")}</div>
        </div>
        <div class="dash-stat">
            <div class="dash-stat-value">${monitorCount}${t("unitCameras")}</div>
            <div class="dash-stat-label">${t("cameraRoleMonitor")}</div>
        </div>
        <div class="dash-stat">
            <div class="dash-stat-value">${ocrCount}${t("unitCameras")}</div>
            <div class="dash-stat-label">${t("cameraRoleOcrScan")}</div>
        </div>
    `;
}


/*
===========================
    오늘 출고 현황 카드
===========================
*/

async function loadOutboundCard() {

    const body = document.getElementById("dashOutboundBody");

    let rows = [];

    try {
        const response = await fetch("/api/analytics/outbound-log");
        rows = response.ok ? await response.json() : [];
    } catch (error) {
        rows = [];
    }

    const todayKey = new Date().toDateString();
    const todayRows = rows.filter(row => row.time && new Date(row.time).toDateString() === todayKey);

    if (todayRows.length === 0) {
        body.innerHTML = `<p class="dash-empty">${t("dashOutboundNoDataMessage")}</p>`;
        return;
    }

    const totalQty = todayRows.reduce((sum, row) => sum + (row.qty || 0), 0);
    // abnormal 필드는 서버가 관리자 세션에만 내려준다 (analytics/outbound-log 참고)
    const abnormalCount = todayRows.filter(row => row.abnormal === true).length;

    body.innerHTML = `
        <div class="dash-stat">
            <div class="dash-stat-value">${todayRows.length}${t("countUnit")}</div>
            <div class="dash-stat-label">${t("typeOutbound")}</div>
        </div>
        <div class="dash-stat">
            <div class="dash-stat-value">${totalQty}${t("unitPieces")}</div>
            <div class="dash-stat-label">${t("dashOutboundQtyLabel")}</div>
        </div>
        ${isAdmin ? `
        <div class="dash-stat">
            <div class="dash-stat-value">${abnormalCount}${t("countUnit")}</div>
            <div class="dash-stat-label">${t("dashAbnormalLabel")}</div>
        </div>
        ` : ""}
    `;
}


loadAlertCard();
loadSlipCard();
loadCameraCard();
loadOutboundCard();
