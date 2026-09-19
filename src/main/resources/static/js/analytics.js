/*
===========================================
    통계 분석 (엑셀 피벗테이블 방식)

    병동 / 의약품 / 일별 / 주별 / 월별 체크박스를 (분류 기준)
    여러 개 동시에 체크해서 조합하고, 집계 값(수량 / 출고 건수 / 이상 알림)은 하나를 고른다.
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

// 체크박스 data-dimension 값 -> {번역 키, 값 뽑는 함수}
const DIMENSIONS = {
    wardName: { labelKey: "filterWard", keyFn: row => row.wardName },
    medicineName: { labelKey: "filterMedicine", keyFn: row => row.medicineName },
    day: { labelKey: "filterDay", keyFn: row => dayKey(row) },
    week: { labelKey: "filterWeek", keyFn: row => weekKey(row) },
    month: { labelKey: "filterMonth", keyFn: row => monthKey(row) }
};

// 체크박스 순서(=표/조합 라벨 순서) 고정
const DIMENSION_ORDER = ["wardName", "medicineName", "day", "week", "month"];

// 막대 색을 나눌 기준으로 쓸 수 있는 항목 (시간 항목은 색을 나누지 않는다)
const COLOR_DIMENSIONS = ["wardName", "medicineName"];

// 막대 하나에 최소로 확보할 가로 폭(px). 조합이 많으면 차트 영역이 가로 스크롤된다.
const MIN_BAR_SLOT = 76;

let outboundLog = [];
let chart = null;

const chartScroll = document.getElementById("analyticsChartScroll");
const chartBox = document.getElementById("analyticsChartBox");
const chartCanvas = document.getElementById("analyticsChart");
const emptyMessage = document.getElementById("analyticsEmpty");
const emptyMessageText = document.getElementById("analyticsEmptyText");
const dimensionChecks = document.querySelectorAll(".filter-check input[data-dimension]");
const measureRadios = document.querySelectorAll('input[name="measure"]');
const abnormalMeasureLabel = document.querySelector('input[name="measure"][value="abnormal"]').closest("label");
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

    // ISO 8601 주차: 주는 월요일에 시작하고, 그 주의 목요일이 속한 해를 그 주의 연도로 본다.
    // (연초/연말에는 달력상 연도와 ISO 연도가 다를 수 있다)
    const [year, month, day] = row.time.slice(0, 10).split("-").map(Number);
    const date = new Date(Date.UTC(year, month - 1, day));

    // 그 주의 목요일로 이동 (일요일=0을 7로 취급)
    date.setUTCDate(date.getUTCDate() + 4 - (date.getUTCDay() || 7));

    const firstDayOfIsoYear = new Date(Date.UTC(date.getUTCFullYear(), 0, 1));
    const week = Math.ceil(((date - firstDayOfIsoYear) / 86400000 + 1) / 7);

    return `${date.getUTCFullYear()}-W${String(week).padStart(2, "0")}`;
}

function monthKey(row) {
    return row.time ? row.time.slice(0, 7) : "미상";
}

/*
===========================================
    이상 알림 집계 칩: 관리자에게만 보인다
    (서버도 관리자가 아니면 abnormal 값을 내려주지 않는다)
===========================================
*/

async function showAbnormalMeasureForAdmin() {

    try {

        const response = await fetch("/api/auth/me");

        if (response.ok && (await response.json()).isAdmin) {
            abnormalMeasureLabel.hidden = false;
        }

    } catch (error) {
        // 확인하지 못하면 숨긴 채로 둔다
    }
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
    선택한 집계 값(수량 합계 / 출고 건수 / 이상 알림 건수)을 구한다.
===========================================
*/

function getCheckedDimensions() {
    return DIMENSION_ORDER.filter(id => {
        return [...dimensionChecks].find(input => input.dataset.dimension === id).checked;
    });
}

// 집계 값: qty(수량 합계) / count(출고 건수) / abnormal(이상 알림 건수)
function getMeasure() {
    return document.querySelector('input[name="measure"]:checked').value;
}

function aggregate() {

    const dimensionIds = getCheckedDimensions();
    const measure = getMeasure();

    if (dimensionIds.length === 0) {
        return { dimensionIds, measure, groups: [] };
    }

    const groupMap = new Map();

    outboundLog.forEach(row => {

        const values = dimensionIds.map(id => DIMENSIONS[id].keyFn(row));
        const key = values.join(" · ");

        if (!groupMap.has(key)) {
            groupMap.set(key, { values, qty: 0, count: 0, abnormal: 0 });
        }

        const group = groupMap.get(key);

        group.qty += Number(row.qty || 0);
        group.count += 1;
        group.abnormal += row.abnormal ? 1 : 0;
    });

    const groups = [...groupMap.values()].sort((a, b) => {
        return a.values.join(" · ").localeCompare(b.values.join(" · "));
    });

    return { dimensionIds, measure, groups };
}

const MEASURE_LABEL_KEYS = {
    qty: "measureQtyLabel",
    count: "measureCountLabel",
    abnormal: "measureAbnormalLabel"
};

function measureLabelOf(measure) {
    return t(MEASURE_LABEL_KEYS[measure]);
}

function measureValueOf(group, measure) {
    return group[measure];
}

// 병동명/의약품명이 표에 HTML로 들어가므로 특수문자는 이스케이프한다
function escapeHtml(text) {
    return String(text)
        .replace(/&/g, "&amp;")
        .replace(/</g, "&lt;")
        .replace(/>/g, "&gt;")
        .replace(/"/g, "&quot;");
}

/*
===========================================
    표 그리기
===========================================
*/

function renderTable(dimensionIds, measure, groups) {

    const headerCells = dimensionIds.map(id => `<th>${t(DIMENSIONS[id].labelKey)}</th>`);

    headerCells.push(`<th class="num">${measureLabelOf(measure)}</th>`);

    tableHead.innerHTML = headerCells.join("");

    tableBody.innerHTML = groups.map(group => {

        const valueCells = group.values.map(value => `<td>${escapeHtml(value)}</td>`).join("");
        const measureCell = `<td class="num">${measureValueOf(group, measure)}</td>`;

        return `<tr>${valueCells}${measureCell}</tr>`;

    }).join("");
}

/*
===========================================
    그래프 그리기
===========================================
*/

// 라이트/다크 모드와 글자 크기 설정에 맞춘 차트 색/글자 크기
function getChartTheme() {

    const dark = document.body.classList.contains("dark-mode");
    const scale = parseFloat(getComputedStyle(document.body).getPropertyValue("--font-scale")) || 1;

    return {
        text: dark ? "#c4d0e6" : "#536b88",
        grid: dark ? "#232f45" : "#edf1f5",
        valueText: dark ? "#e7eefb" : "#243b5a",
        fontSize: Math.round(12 * scale)
    };
}

// 막대 위에 값을 숫자로 표시하는 플러그인 (chartjs-plugin-datalabels 없이 직접 그림)
const valueLabelPlugin = {

    id: "valueLabels",

    afterDatasetsDraw(chartInstance, args, options) {

        const { ctx } = chartInstance;

        ctx.save();
        ctx.font = `600 ${options.fontSize}px ${Chart.defaults.font.family}`;
        ctx.fillStyle = options.color;
        ctx.textAlign = "center";
        ctx.textBaseline = "bottom";

        chartInstance.data.datasets.forEach((dataset, datasetIndex) => {

            if (!chartInstance.isDatasetVisible(datasetIndex)) {
                return;
            }

            chartInstance.getDatasetMeta(datasetIndex).data.forEach((bar, index) => {

                const value = dataset.data[index];

                if (value === null || value === undefined) {
                    return;
                }

                ctx.fillText(value, bar.x, bar.y - 6);
            });
        });

        ctx.restore();
    }
};

// 막대 색을 나눌 기준(병동/의약품)이 있으면 그 값별로 데이터셋을 나눠 범례로 구분하고,
// 없으면(시간 항목만 선택) 한 가지 색으로 그린다.
function buildDatasets(dimensionIds, measure, groups) {

    const colorIndex = dimensionIds.findIndex(id => COLOR_DIMENSIONS.includes(id));

    const baseStyle = {
        borderRadius: 6,
        maxBarThickness: 56
    };

    if (colorIndex === -1) {

        return [{
            ...baseStyle,
            label: measureLabelOf(measure),
            data: groups.map(group => measureValueOf(group, measure)),
            backgroundColor: PALETTE[0]
        }];
    }

    const colorKeys = [...new Set(groups.map(group => group.values[colorIndex]))];

    return colorKeys.map((colorKey, index) => ({
        ...baseStyle,
        label: colorKey,
        data: groups.map(group => {
            return group.values[colorIndex] === colorKey ? measureValueOf(group, measure) : null;
        }),
        backgroundColor: PALETTE[index % PALETTE.length]
    }));
}

function renderChart(dimensionIds, measure, groups) {

    const theme = getChartTheme();
    const datasets = buildDatasets(dimensionIds, measure, groups);

    Chart.defaults.font.family = getComputedStyle(document.body).fontFamily;

    // 막대가 많으면 차트 폭을 늘려서 가로 스크롤 (라벨이 겹치지 않게)
    chartBox.style.minWidth = `${groups.length * MIN_BAR_SLOT}px`;

    if (chart) {
        chart.destroy();
    }

    chart = new Chart(chartCanvas, {
        type: "bar",
        plugins: [valueLabelPlugin],
        data: {
            // 값이 배열이면 여러 줄 라벨이 되어 "병동 / 의약품 / 날짜"가 줄바꿈되어 표시된다
            labels: groups.map(group => group.values),
            datasets
        },
        options: {
            responsive: true,
            maintainAspectRatio: false,
            layout: { padding: { top: 4 } },
            plugins: {
                legend: {
                    display: datasets.length > 1,
                    labels: {
                        color: theme.text,
                        font: { size: theme.fontSize },
                        usePointStyle: true,
                        boxWidth: 8,
                        padding: 28
                    }
                },
                tooltip: {
                    callbacks: {
                        title: items => groups[items[0].dataIndex].values.join(" · ")
                    }
                },
                valueLabels: {
                    color: theme.valueText,
                    fontSize: theme.fontSize
                }
            },
            scales: {
                x: {
                    stacked: true,
                    grid: { display: false },
                    ticks: {
                        color: theme.text,
                        font: { size: theme.fontSize },
                        autoSkip: false,
                        maxRotation: 0
                    }
                },
                y: {
                    stacked: true,
                    beginAtZero: true,
                    grace: "12%",
                    grid: { color: theme.grid },
                    ticks: {
                        color: theme.text,
                        font: { size: theme.fontSize },
                        precision: 0
                    }
                }
            }
        }
    });
}

/*
===========================================
    전체 렌더링
===========================================
*/

function showEmpty(message) {

    emptyMessageText.textContent = message;
    emptyMessage.hidden = false;
    chartScroll.hidden = true;
    tableHead.innerHTML = "";
    tableBody.innerHTML = "";
}

function render() {

    const { dimensionIds, measure, groups } = aggregate();

    if (dimensionIds.length === 0) {
        showEmpty(t("selectAtLeastOneMessage"));
        return;
    }

    if (outboundLog.length === 0 || groups.length === 0) {
        showEmpty(t("noOutboundDataMessage"));
        return;
    }

    emptyMessage.hidden = true;
    chartScroll.hidden = false;

    renderChart(dimensionIds, measure, groups);
    renderTable(dimensionIds, measure, groups);
}

/*
===========================================
    체크박스 이벤트
===========================================
*/

dimensionChecks.forEach(input => {
    input.addEventListener("change", render);
});

measureRadios.forEach(input => {
    input.addEventListener("change", render);
});

showAbnormalMeasureForAdmin();
loadOutboundLog();
