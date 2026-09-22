/*
===========================================
    전표 관리 화면

    전표 목록(GET /api/slips)을 불러와서 표로 보여주고,
    페이지 진입 시엔 가장 최신 전표를 기본으로 상세(이미지+OCR 결과)에 띄운다.
    목록에서 "보기"를 클릭하면 그 전표로 상세를 바꿔서 보여준다.
===========================================
*/

const receiptTableBody = document.getElementById("receiptTableBody");
const receiptDetails = document.getElementById("receipt-details");
const receiptImage = document.getElementById("receiptImage");
const receiptCaption = document.getElementById("receiptCaption");
const ocrTableBody = document.getElementById("ocrTableBody");

let slips = [];

async function loadSlips() {

    const response = await fetch("/api/slips");

    if (!response.ok) {

        receiptTableBody.innerHTML = `
            <tr><td class="table-loading-row" colspan="7">${t("slipLoadFailMessage")}</td></tr>
        `;

        return;
    }

    slips = await response.json();

    renderTable();

    // 별도로 "보기"를 클릭하지 않으면 가장 최신 전표(목록 맨 위)를 기본으로 보여준다
    if (slips.length > 0) {
        showDetail(slips[0].slipId);
    } else {
        receiptTableBody.innerHTML = `
            <tr><td class="table-loading-row" colspan="7">${t("noSlipMessage")}</td></tr>
        `;
    }
}

function renderTable() {

    if (slips.length === 0) {
        return;
    }

    receiptTableBody.innerHTML = slips.map(slip => `
        <tr>
            <td><strong>${escapeHtml(slip.slipId)}</strong></td>
            <td>${slip.slipDate || "-"}</td>
            <td>${t("typeOutbound")}</td>
            <td>${escapeHtml(slip.wardName)}</td>
            <td>${slip.itemCount}${t("unitSpecies")} / ${slip.totalQty}${t("unitPieces")}</td>
            <td>${escapeHtml(slip.requesterName)}</td>
            <td>
                <button
                    type="button"
                    class="view-receipt-btn"
                    data-slip-id="${escapeHtml(slip.slipId)}"
                    aria-controls="receipt-details"
                    aria-label="${escapeHtml(slip.slipId)} 전표 보기"
                >${t("viewBtn")}</button>
            </td>
        </tr>
    `).join("");

    receiptTableBody.querySelectorAll(".view-receipt-btn").forEach(button => {
        button.addEventListener("click", () => showDetail(button.dataset.slipId, true));
    });
}

async function showDetail(slipId, focusAfterLoad) {

    const response = await fetch(`/api/slips/${encodeURIComponent(slipId)}`);

    if (!response.ok) {
        return;
    }

    const detail = await response.json();

    receiptImage.src = detail.imagePath || "";
    receiptImage.alt = `${detail.slipId} 전표 이미지`;

    receiptCaption.textContent =
        `${detail.slipId} · ${detail.slipDate || "-"} · ${detail.wardName} · ${t("colStaff")} ${detail.requesterName}`;

    ocrTableBody.innerHTML = detail.items.map(item => `
        <tr>
            <td>${escapeHtml(item.medicineName)}</td>
            <td>${item.requestQty}${t("unitPieces")}</td>
            <td>${item.outboundQty != null ? item.outboundQty + t("unitPieces") + (item.abnormal ? ` (${t("mismatchLabel")})` : "") : "-"}</td>
        </tr>
    `).join("");

    if (focusAfterLoad) {
        receiptDetails.focus({ preventScroll: true });
        receiptDetails.scrollIntoView({
            behavior: window.matchMedia("(prefers-reduced-motion: reduce)").matches ? "instant" : "smooth",
            block: "nearest"
        });
    }
}

loadSlips();
