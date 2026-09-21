/*
===========================================
    병동 관리 화면

    병동 목록(GET /api/wards)을 불러와서 표시하고,
    추가(POST /api/wards)/수정(PUT /api/wards/{wardSeqId})/삭제(DELETE /api/wards/{wardSeqId})로 관리한다.

    병동번호(wardCode)는 화면 표시/입력용이고, 실제 API는 내부 대체키(wardSeqId)로 식별한다
    (병동번호는 병원마다 겹칠 수 있어서 전역 식별자로 쓸 수 없음).
===========================================
*/

const wardTableBody = document.getElementById("wardTableBody");
const wardAddForm = document.getElementById("wardAddForm");
const wardIdInput = document.getElementById("wardIdInput");
const wardNameInput = document.getElementById("wardNameInput");
const wardLocationInput = document.getElementById("wardLocationInput");
const wardFormMessage = document.getElementById("wardFormMessage");

let wards = [];
let editingWardSeqId = null;

async function loadWards() {

    const response = await fetch("/api/wards");

    if (!response.ok) {
        return;
    }

    wards = await response.json();
    editingWardSeqId = null;

    renderWardTable();
}

function renderWardTable() {

    if (wards.length === 0) {

        wardTableBody.innerHTML = `
            <tr>
                <td class="table-loading-row" colspan="4">${t("noWardMessage")}</td>
            </tr>
        `;

        return;
    }

    wardTableBody.innerHTML = wards.map(ward => {

        if (ward.wardSeqId === editingWardSeqId) {

            return `
                <tr data-ward-seq-id="${ward.wardSeqId}">
                    <td>${ward.wardCode}</td>
                    <td><input type="text" class="ward-edit-name-input" value="${ward.wardName}"></td>
                    <td><input type="text" class="ward-edit-location-input" value="${ward.location || ""}"></td>
                    <td>
                        <button type="button" class="ward-save-btn" data-ward-seq-id="${ward.wardSeqId}">${t("saveBtn")}</button>
                        <button type="button" class="ward-cancel-btn">${t("cancelBtn")}</button>
                    </td>
                </tr>
            `;
        }

        return `
            <tr data-ward-seq-id="${ward.wardSeqId}">
                <td>${ward.wardCode}</td>
                <td>${ward.wardName}</td>
                <td>${ward.location || "-"}</td>
                <td>
                    <button type="button" class="ward-edit-btn" data-ward-seq-id="${ward.wardSeqId}">${t("editWardBtn")}</button>
                    <button type="button" class="ward-delete-btn" data-ward-seq-id="${ward.wardSeqId}">${t("deleteWardBtn")}</button>
                </td>
            </tr>
        `;
    }).join("");

    wardTableBody.querySelectorAll(".ward-edit-btn").forEach(button => {
        button.addEventListener("click", () => {
            editingWardSeqId = Number(button.dataset.wardSeqId);
            renderWardTable();
        });
    });

    wardTableBody.querySelectorAll(".ward-cancel-btn").forEach(button => {
        button.addEventListener("click", () => {
            editingWardSeqId = null;
            renderWardTable();
        });
    });

    wardTableBody.querySelectorAll(".ward-save-btn").forEach(button => {
        button.addEventListener("click", () => saveWard(button.dataset.wardSeqId));
    });

    wardTableBody.querySelectorAll(".ward-delete-btn").forEach(button => {
        button.addEventListener("click", () => deleteWard(button.dataset.wardSeqId));
    });
}

/*
===========================================
    병동 추가
===========================================
*/

wardAddForm.addEventListener("submit", async function(event) {

    event.preventDefault();

    wardFormMessage.textContent = "";

    const wardCode = wardIdInput.value.trim();
    const wardName = wardNameInput.value.trim();
    const location = wardLocationInput.value.trim();

    try {

        const response = await fetch("/api/wards", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ wardCode, wardName, location })
        });

        const data = await response.json();

        if (!response.ok) {
            wardFormMessage.textContent = data.message || t("wardAddFailMessage");
            return;
        }

        wardIdInput.value = "";
        wardNameInput.value = "";
        wardLocationInput.value = "";

        await loadWards();

    } catch (error) {

        wardFormMessage.textContent = t("wardAddFailMessage");
    }
});

/*
===========================================
    병동 수정
===========================================
*/

async function saveWard(wardSeqId) {

    const row = wardTableBody.querySelector(`tr[data-ward-seq-id="${wardSeqId}"]`);
    const wardName = row.querySelector(".ward-edit-name-input").value.trim();
    const location = row.querySelector(".ward-edit-location-input").value.trim();

    try {

        const response = await fetch(`/api/wards/${wardSeqId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ wardName, location })
        });

        const data = await response.json();

        if (!response.ok) {
            alert(data.message || t("wardUpdateFailMessage"));
            return;
        }

        await loadWards();

    } catch (error) {

        alert(t("wardUpdateFailMessage"));
    }
}

/*
===========================================
    병동 삭제
===========================================
*/

async function deleteWard(wardSeqId) {

    const confirmed = confirm(t("wardDeleteConfirmMessage"));

    if (!confirmed) {
        return;
    }

    try {

        const response = await fetch(`/api/wards/${wardSeqId}`, { method: "DELETE" });

        if (!response.ok) {
            const data = await response.json().catch(() => ({}));
            alert(data.message || t("wardDeleteFailMessage"));
            return;
        }

        await loadWards();

    } catch (error) {

        alert(t("wardDeleteFailMessage"));
    }
}

loadWards();
