/*
===========================================
    대시보드 화면

    다른 화면들(알림/전표/카메라/통계)의 데이터를 한 번씩 불러와서
    "한눈에 보기" 카드 4개로 요약해서 보여준다. 각 카드는 해당 화면으로 링크만 걸어주고,
    실제 처리(확인 처리, 전표 등록 등)는 각자의 화면에서 한다.

    이상(MISMATCH) 알림 상세(의약품명 등)와 출고 이상건수는 관리자만 볼 수 있다.
    직원의 "미처리 알림" 카드는 상세 목록 대신, 가장 최근 미처리 알림의 시각·종류만 담은
    주의 문구 한 줄만 보여준다 (/api/alerts/latest-notice - 서버가 내용은 안 주고
    시각·종류만 내려줌). 대시보드 상단 배너(anomaly-ui.js)도 상세를 보여주는 UI라
    관리자에게만 뜨게 되어 있다.

    아직 OCR 파이프라인이 없어서 "OCR 인식될 때마다 갱신"을 서버 푸시로는 못 하고,
    대신 /api/slips 목록의 가장 최신 전표번호가 바뀌었는지를 주기적으로 확인해서
    바뀌었을 때만 관련 카드를 다시 그린다 (나중에 파이프라인이 붙으면 그때 실제
    이벤트 기반(WebSocket 등)으로 바꾸면 됨 - 지금은 그 자리를 폴링으로 대신함).
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

    // 직원 화면은 상세 목록 대신 "언제·무슨 종류" 정도의 주의 문구 한 줄만 보여준다
    // (회의 결과: 직원에게 의약품명 등 상세 내용까지 줄 필요는 없음)
    if (!isAdmin) {
        await loadAlertNoticeForStaff(summary, list);
        return;
    }

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
        <span><strong>${mismatchCount}</strong>${t("countUnit")} ${t("dashAbnormalLabel")}</span>
        <span><strong>${lowStockCount}</strong>${t("countUnit")} ${t("dashLowStockLabel")}</span>
    `;

    if (pending.length === 0) {
        list.innerHTML = `<li class="dash-empty">${t("dashNoAlertMessage")}</li>`;
        return;
    }

    list.innerHTML = pending.slice(0, 5).map(alert => `
        <li>
            <span class="dash-row-top">
                <span>${escapeHtml(alert.medicineName)}</span>
                <span class="dash-row-badge ${alert.alertType === TYPE_LOW_STOCK ? "low-stock" : ""}">
                    ${alert.alertType === TYPE_LOW_STOCK ? t("dashLowStockLabel") : t("dashAbnormalLabel")}
                </span>
            </span>
            <span class="dash-row-sub">${escapeHtml(alert.alertContent)} · ${formatTime(alert.alertTime)}</span>
        </li>
    `).join("");
}

function formatHourMinute(value) {

    const date = new Date(value);

    return `${date.getHours()}${t("hourUnit")}${String(date.getMinutes()).padStart(2, "0")}${t("minuteUnit")}`;
}

async function loadAlertNoticeForStaff(summary, list) {

    summary.innerHTML = "";

    let notices = [];

    try {
        const response = await fetch("/api/alerts/notices");
        notices = response.ok ? await response.json() : [];
    } catch (error) {
        notices = [];
    }

    if (notices.length === 0) {
        list.innerHTML = `<li class="dash-empty">${t("dashNoAlertMessage")}</li>`;
        return;
    }

    list.innerHTML = notices.slice(0, 5).map(notice => {

        const timeLabel = formatHourMinute(notice.alertTime);
        const typeLabel = notice.alertType === TYPE_LOW_STOCK ? t("dashLowStockLabel") : t("dashMismatchNoticeType");

        return `
            <li class="dash-alert-notice">
                <i class="fa-solid fa-triangle-exclamation" aria-hidden="true"></i>
                <span>${t("dashAlertNoticeText").replace("{time}", timeLabel).replace("{type}", typeLabel)}</span>
            </li>
        `;
    }).join("");
}


/*
===========================
    최근 전표 카드 - 가장 최근 전표의 실제 이미지를 보여준다
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

    let detail = null;

    try {
        const response = await fetch(`/api/slips/${encodeURIComponent(latest.slipId)}`);
        detail = response.ok ? await response.json() : null;
    } catch (error) {
        detail = null;
    }

    body.innerHTML = `
        ${detail && detail.imagePath
            ? `<img class="dash-slip-image" src="${escapeHtml(detail.imagePath)}" alt="${escapeHtml(latest.slipId)}">`
            : `<p class="dash-empty">${t("dashNoSlipImageMessage")}</p>`}
        <div class="dash-slip-meta">
            <span class="dash-slip-id">${escapeHtml(latest.slipId)}</span>
            <span class="dash-slip-sub">${latest.slipDate || "-"} · ${escapeHtml(latest.wardName)} · ${t("colStaff")} ${escapeHtml(latest.requesterName)}</span>
        </div>
        <table class="dash-slip-ocr-table">
            <thead>
                <tr><th>${t("colMedicineName")}</th><th>${t("ocrColRequestQty")}</th><th>${t("ocrColOutboundQty")}</th></tr>
            </thead>
            <tbody>
                ${detail && detail.items && detail.items.length > 0
                    ? detail.items.map(item => `
                        <tr>
                            <td>${escapeHtml(item.medicineName)}</td>
                            <td>${item.requestQty}${t("unitPieces")}</td>
                            <td>${item.outboundQty != null ? item.outboundQty + t("unitPieces") + (item.abnormal ? ` (${t("mismatchLabel")})` : "") : "-"}</td>
                        </tr>
                    `).join("")
                    : `<tr><td class="dash-empty" colspan="3">${t("noSlipMessage")}</td></tr>`}
            </tbody>
        </table>
    `;
}


/*
===========================
    실시간 카메라 카드 - 모니터링용/OCR인식용 카메라를 각각 한 대씩 미리보기로 보여준다
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

    const monitorCameras = cameras.filter(camera => camera.cameraRole !== "OCR_SCAN");
    const ocrCamera = cameras.find(camera => camera.cameraRole === "OCR_SCAN");

    body.innerHTML = `
        <div class="dash-camera-preview">
            ${monitorCameras.length > 0
                ? `<select class="dash-camera-select" id="dashMonitorCameraSelect" aria-label="${t("dashMonitorPreviewLabel")}">
                    ${monitorCameras.map(camera => `<option value="${camera.cameraId}">${escapeHtml(camera.cameraName)}</option>`).join("")}
                   </select>`
                : `<span class="dash-camera-preview-label">${t("dashMonitorPreviewLabel")}</span>`}
            ${monitorCameras.length > 0
                ? `<img id="dashMonitorCameraImg" src="${escapeHtml(monitorCameras[0].streamUrl)}" alt="${escapeHtml(monitorCameras[0].cameraName)}">`
                : `<p class="dash-empty">${t("cameraEmptyMessage")}</p>`}
        </div>
        <div class="dash-camera-preview">
            <span class="dash-camera-preview-label">${t("dashOcrPreviewLabel")}</span>
            ${ocrCamera
                ? `<img src="${escapeHtml(ocrCamera.streamUrl)}" alt="${escapeHtml(ocrCamera.cameraName)}">`
                : `<p class="dash-empty">${t("dashNoOcrCameraMessage")}</p>`}
        </div>
    `;

    // 구역설정 화면의 "카메라 이동" 드롭다운처럼, 모니터링 미리보기도 다른 카메라로 바꿔볼 수 있게
    const select = document.getElementById("dashMonitorCameraSelect");
    const img = document.getElementById("dashMonitorCameraImg");

    if (select && img) {
        select.addEventListener("change", () => {
            const chosen = monitorCameras.find(camera => String(camera.cameraId) === select.value);
            if (chosen) {
                img.src = chosen.streamUrl;
                img.alt = chosen.cameraName;
            }
        });
    }
}


/*
===========================
    오늘 출고 현황 카드 - 통계 분석 화면의 기본 조합(병동+의약품, 수량 합계)을
    오늘 데이터로만 그대로 그려서 보여준다
===========================
*/

let outboundChart = null;

async function loadOutboundCard() {

    const summary = document.getElementById("dashOutboundSummary");
    const body = document.getElementById("dashOutboundBody");
    const canvas = document.getElementById("dashOutboundChart");

    let rows = [];

    try {
        const response = await fetch("/api/analytics/outbound-log");
        rows = response.ok ? await response.json() : [];
    } catch (error) {
        rows = [];
    }

    const todayKey = new Date().toDateString();
    const todayRows = rows.filter(row => row.time && new Date(row.time).toDateString() === todayKey);

    const totalQty = todayRows.reduce((sum, row) => sum + (row.qty || 0), 0);
    // abnormal 필드는 서버가 관리자 세션에만 내려준다 (analytics/outbound-log 참고)
    const abnormalCount = todayRows.filter(row => row.abnormal === true).length;

    summary.innerHTML = `
        <span><strong>${todayRows.length}</strong>${t("countUnit")} ${t("typeOutbound")}</span>
        <span><strong>${totalQty}</strong>${t("unitPieces")} ${t("dashOutboundQtyLabel")}</span>
        ${isAdmin ? `<span><strong>${abnormalCount}</strong>${t("countUnit")} ${t("dashAbnormalLabel")}</span>` : ""}
    `;

    const existingEmpty = body.querySelector(".dash-empty");
    if (existingEmpty) {
        existingEmpty.remove();
    }

    if (todayRows.length === 0) {

        canvas.hidden = true;
        body.insertAdjacentHTML("beforeend", `<p class="dash-empty">${t("dashOutboundNoDataMessage")}</p>`);

        if (outboundChart) {
            outboundChart.destroy();
            outboundChart = null;
        }

        return;
    }

    canvas.hidden = false;

    // 통계 분석 화면의 기본 선택값(병동+의약품 조합, 수량 합계)과 같은 방식으로 오늘 데이터만 집계
    const groupMap = new Map();

    todayRows.forEach(row => {
        const key = `${row.wardName} · ${row.medicineName}`;
        groupMap.set(key, (groupMap.get(key) || 0) + Number(row.qty || 0));
    });

    const labels = [...groupMap.keys()];
    const values = [...groupMap.values()];

    if (outboundChart) {
        outboundChart.destroy();
    }

    outboundChart = new Chart(canvas, {
        type: "bar",
        data: {
            labels,
            datasets: [{
                label: t("measureQtyLabel"),
                data: values,
                backgroundColor: "#1474ed",
                borderRadius: 6,
                maxBarThickness: 40
            }]
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            plugins: { legend: { display: false } },
            scales: {
                x: { grid: { display: false }, ticks: { autoSkip: false, maxRotation: 0 } },
                y: { beginAtZero: true, ticks: { precision: 0 } }
            }
        }
    });
}


/*
===========================
    OCR 인식(=새 전표 등록) 감지용 폴링

    아직 실시간 푸시가 없어서, 최신 전표번호가 바뀌었는지를 주기적으로 확인하는 걸로
    대신한다. 바뀌었을 때만 알림/전표/출고 카드를 다시 그린다 (카메라 미리보기는
    스트림이라 항상 실시간이라 다시 그릴 필요 없음).
===========================
*/

let lastSeenSlipId;

async function pollForNewSlip() {

    try {

        const response = await fetch("/api/slips");
        const slips = response.ok ? await response.json() : [];
        const currentLatestId = slips.length > 0 ? slips[0].slipId : null;

        if (lastSeenSlipId !== undefined && currentLatestId !== lastSeenSlipId) {
            loadAlertCard();
            loadSlipCard();
            loadOutboundCard();
        }

        lastSeenSlipId = currentLatestId;

    } catch (error) {
        // 폴링 실패는 조용히 넘어가고 다음 주기에 다시 시도
    }
}

loadAlertCard();
loadSlipCard();
loadCameraCard();
loadOutboundCard();

setInterval(pollForNewSlip, 8000);
