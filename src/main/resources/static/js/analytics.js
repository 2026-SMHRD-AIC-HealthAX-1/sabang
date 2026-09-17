/*
===========================================
    통계 분석 (엑셀 피벗테이블 방식)

    병동 / 의약품 / 일별 / 주별 / 월별 / 수량 체크박스를
    여러 개 동시에 체크해서 조합할 수 있다.
    체크된 항목들의 값을 이어붙인 조합별로 그룹핑해서
    그래프 + 표로 보여준다.

    회의 내용에 따라 항목이 계속 바뀔 수 있어서, 백엔드는
    가공 없는 출고 기록만 내려주고 여기서 매번 다시 집계한다.

    새 항목을 추가하려면 DIMENSIONS에 한 줄만 추가하면 된다.
===========================================
*/

const PALETTE = [
    "#1474ed", "#16a34a", "#f59e0b", "#e11d48",
    "#7c3aed", "#0891b2", "#ea580c", "#65a30d"
];

// 체크박스 data-dimension 값 -> {라벨, 값 뽑는 함수}
const DIMENSIONS = {
    wardName: { label: "병동", keyFn: row => row.wardName },
    medicineName: { label: "의약품", keyFn: row => row.medicineName },
    day: { label: "일별", keyFn: row => dayKey(row) },
    week: { label: "주별", keyFn: row => weekKey(row) },
    month: { label: "월별", keyFn: row => monthKey(row) }
};

// 체크박스 순서(=표/조합 라벨 순서) 고정
const DIMENSION_ORDER = ["wardName", "medicineName", "day", "week", "month"];

let outboundLog = [];
let chart = null;

const chartCanvas = document.getElementById("analyticsChart");
const emptyMessage = document.getElementById("analyticsEmpty");
const emptyMessageText = document.getElementById("analyticsEmptyText");
const dimensionChecks = document.querySelectorAll(".filter-check input[data-dimension]");
const qtyMeasureCheck = document.getElementById("qtyMeasureCheck");
const tableHead = document.getElementById("analyticsTableHead");
const tableBody = document.getElementById("analyticsTableBody");

/*
===========================================
    시간 항목 키 뽑기
===========================================
*/

function dayKey(row) {
    return row.time ? row.time.slice(0, 10) : "미상";
}

function weekKey(row) {

    if (!row.time) {
        return "미상";
    }

    const date = new Date(row.time);
    const firstDayOfYear = new Date(date.getFullYear(), 0, 1);
    const pastDays = Math.floor((date - firstDayOfYear) / 86400000);
    const week = Math.ceil((pastDays + firstDayOfYear.getDay() + 1) / 7);

    return `${date.getFullYear()}-W${String(week).padStart(2, "0")}`;
}

function monthKey(row) {
    return row.time ? row.time.slice(0, 7) : "미상";
}

/*
===========================================
    데이터 불러오기
===========================================
*/

async function loadOutboundLog() {

    try {

        const response = await fetch("/api/analytics/outbound-log");

        outboundLog = response.ok ? await response.json() : [];

    } catch (error) {
        outboundLog = [];
    }

    render();
}

/*
===========================================
    체크된 항목 조합대로 집계

    선택된 차원(들)의 값을 이어붙인 것을 그룹 키로 삼고,
    수량 체크 여부에 따라 합계(수량) 또는 건수를 구한다.
===========================================
*/

function getCheckedDimensions() {
    return DIMENSION_ORDER.filter(id => {
        return [...dimensionChecks].find(input => input.dataset.dimension === id).checked;
    });
}

function aggregate() {

    const dimensionIds = getCheckedDimensions();
    const useQty = qtyMeasureCheck.checked;

    if (dimensionIds.length === 0) {
        return { dimensionIds, useQty, groups: [] };
    }

    const groupMap = new Map();

    outboundLog.forEach(row => {

        const values = dimensionIds.map(id => DIMENSIONS[id].keyFn(row));
        const key = values.join(" · ");

        if (!groupMap.has(key)) {
            groupMap.set(key, { values, qty: 0, count: 0 });
        }

        const group = groupMap.get(key);

        group.qty += Number(row.qty || 0);
        group.count += 1;
    });

    const groups = [...groupMap.values()].sort((a, b) => {
        return a.values.join(" · ").localeCompare(b.values.join(" · "));
    });

    return { dimensionIds, useQty, groups };
}

/*
===========================================
    표 그리기
===========================================
*/

function renderTable(dimensionIds, useQty, groups) {

    const headerLabels = dimensionIds.map(id => DIMENSIONS[id].label);
    const measureLabel = useQty ? "수량 합계" : "건수";

    tableHead.innerHTML = [...headerLabels, measureLabel]
        .map(label => `<th>${label}</th>`)
        .join("");

    tableBody.innerHTML = groups.map(group => {

        const valueCells = group.values.map(value => `<td>${value}</td>`).join("");
        const measureCell = `<td>${useQty ? group.qty : group.count}</td>`;

        return `<tr>${valueCells}${measureCell}</tr>`;

    }).join("");
}

/*
===========================================
    그래프 그리기
===========================================
*/

function renderChart(useQty, groups) {

    const labels = groups.map(group => group.values.join(" · "));
    const data = groups.map(group => useQty ? group.qty : group.count);

    if (chart) {
        chart.destroy();
    }

    chart = new Chart(chartCanvas, {
        type: "bar",
        data: {
            labels,
            datasets: [{
                label: useQty ? "수량 합계" : "건수",
                data,
                backgroundColor: PALETTE[0]
            }]
        },
        options: {
            responsive: true,
            scales: {
                y: { beginAtZero: true }
            }
        }
    });
}

/*
===========================================
    전체 렌더링
===========================================
*/

function render() {

    const { dimensionIds, useQty, groups } = aggregate();

    if (dimensionIds.length === 0) {
        emptyMessageText.textContent = "표시할 항목을 하나 이상 선택하세요.";
        emptyMessage.hidden = false;
        chartCanvas.hidden = true;
        tableHead.innerHTML = "";
        tableBody.innerHTML = "";
        return;
    }

    if (outboundLog.length === 0 || groups.length === 0) {
        emptyMessageText.textContent = "표시할 출고 데이터가 없습니다.";
        emptyMessage.hidden = false;
        chartCanvas.hidden = true;
        tableHead.innerHTML = "";
        tableBody.innerHTML = "";
        return;
    }

    emptyMessage.hidden = true;
    chartCanvas.hidden = false;

    renderChart(useQty, groups);
    renderTable(dimensionIds, useQty, groups);
}

/*
===========================================
    체크박스 이벤트
===========================================
*/

dimensionChecks.forEach(input => {
    input.addEventListener("change", render);
});

qtyMeasureCheck.addEventListener("change", render);

loadOutboundLog();
