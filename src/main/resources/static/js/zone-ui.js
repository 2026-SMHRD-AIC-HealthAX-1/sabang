/*
===========================================
    구역(Zone) 설정 화면

    zone-store.js의 상태를 받아서
    왼쪽 카메라 위 구역 박스(드래그/리사이즈) +
    오른쪽 의약품 목록을 그린다.
===========================================
*/

const PALETTE = [
    "#1474ed", "#16a34a", "#f59e0b", "#e11d48",
    "#7c3aed", "#0891b2", "#ea580c", "#65a30d"
];

function colorFor(medicineId) {
    return PALETTE[medicineId % PALETTE.length];
}

function clamp(value, min, max) {
    return Math.min(Math.max(value, min), max);
}

const zoneCameraName = document.getElementById("zoneCameraName");
const zoneCameraVideo = document.getElementById("zoneCameraVideo");
const zoneCameraEmpty = document.getElementById("zoneCameraEmpty");
const zoneCameraLoading = document.getElementById("zoneCameraLoading");
const zoneCameraContainer = document.getElementById("zoneCameraContainer");
const zoneList = document.getElementById("zoneList");
const zoneMessage = document.getElementById("zoneMessage");

const medicineFormModal = document.getElementById("medicineFormModal");
const medicineModalBackdrop = document.getElementById("medicineModalBackdrop");
const medicineModalTitle = document.getElementById("medicineModalTitle");
const medicineModalCancelBtn = document.getElementById("medicineModalCancelBtn");
const medicineForm = document.getElementById("medicineForm");
const medicineNameInput = document.getElementById("medicineNameInput");
const medicineHighRiskInput = document.getElementById("medicineHighRiskInput");
const medicineManufacturerInput = document.getElementById("medicineManufacturerInput");
const medicineRegisterDateInput = document.getElementById("medicineRegisterDateInput");
const medicineMinQtyInput = document.getElementById("medicineMinQtyInput");
const medicineFormMessage = document.getElementById("medicineFormMessage");

// null이면 추가 모드, 값이 있으면 그 medicineId를 수정하는 중
let editingMedicineId = null;

/*
===========================================
    약품 추가/수정 모달

    추가는 누르는 즉시 서버에 생성되고,
    수정도 추가와 같이 창의 "저장"을 누르면 그 약품만 바로 서버에 반영된다.
    (구역 위치/카메라 변경은 오른쪽 아래 "저장" 버튼으로 따로 저장)
===========================================
*/

function openMedicineModal(zone) {

    editingMedicineId = zone ? zone.medicineId : null;

    medicineModalTitle.textContent = zone ? "약품 수정" : "약품 추가";
    medicineFormMessage.textContent = "";

    medicineNameInput.value = zone ? zone.medicineName : "";
    medicineHighRiskInput.value = zone ? zone.highRiskYn : "N";
    medicineManufacturerInput.value = zone ? zone.manufacturer : "";
    medicineRegisterDateInput.value = zone ? zone.registerDate : new Date().toISOString().slice(0, 10);
    medicineMinQtyInput.value = zone && zone.minQty !== null && zone.minQty !== undefined ? zone.minQty : "";

    medicineFormModal.hidden = false;
}

function closeMedicineModal() {
    medicineFormModal.hidden = true;
}

medicineModalCancelBtn.addEventListener("click", closeMedicineModal);
medicineModalBackdrop.addEventListener("click", closeMedicineModal);

medicineForm.addEventListener("submit", async function(event) {

    event.preventDefault();

    const details = {
        medicineName: medicineNameInput.value.trim(),
        highRiskYn: medicineHighRiskInput.value,
        manufacturer: medicineManufacturerInput.value.trim(),
        registerDate: medicineRegisterDateInput.value,
        // 비워두면 null (재고 부족 알림 없음). 입력 필수 여부는 팀 결정 후 아래 검사에서 정한다.
        minQty: medicineMinQtyInput.value.trim() === "" ? null : Number(medicineMinQtyInput.value)
    };

    if (!details.medicineName || !details.manufacturer || !details.registerDate) {
        medicineFormMessage.textContent = "모든 항목을 입력해 주세요.";
        return;
    }

    if (details.minQty !== null && (!Number.isInteger(details.minQty) || details.minQty < 0)) {
        medicineFormMessage.textContent = t("minQtyInvalid");
        return;
    }

    try {

        if (editingMedicineId === null) {
            await FramZones.addZone(details);
        } else {
            await FramZones.updateZoneDetails(editingMedicineId, details);
        }

        closeMedicineModal();

    } catch (error) {
        medicineFormMessage.textContent = error.message;
    }
});

/*
===========================================
    왼쪽 카메라 + 구역 박스
===========================================
*/

function renderCameraView() {

    const cameras = FramZones.getCameras();
    const currentCameraId = FramZones.getCurrentCameraId();
    const camera = cameras.find(item => item.cameraId === currentCameraId);

    zoneCameraContainer.querySelectorAll(".zone-box").forEach(box => box.remove());

    zoneCameraLoading.hidden = true;
    zoneCameraName.hidden = false;

    if (!camera) {
        zoneCameraName.textContent = t("noCameraLabel");
        zoneCameraVideo.hidden = true;
        zoneCameraEmpty.hidden = false;
        return;
    }

    zoneCameraName.textContent = camera.cameraName;
    zoneCameraVideo.src = camera.streamUrl;
    zoneCameraVideo.hidden = false;
    zoneCameraEmpty.hidden = true;

    FramZones.getZonesForCurrentCamera().forEach(zone => {
        zoneCameraContainer.appendChild(createZoneBox(zone));
    });
}

function createZoneBox(zone) {

    const color = colorFor(zone.medicineId);

    const box = document.createElement("div");
    box.className = "zone-box";
    box.style.left = zone.regionX + "%";
    box.style.top = zone.regionY + "%";
    box.style.width = zone.regionWidth + "%";
    box.style.height = zone.regionHeight + "%";
    box.style.borderColor = color;

    const label = document.createElement("span");
    label.className = "zone-box-label";
    label.textContent = zone.medicineName;
    label.style.background = color;
    box.appendChild(label);

    const handle = document.createElement("div");
    handle.className = "zone-box-handle";
    handle.style.background = color;
    box.appendChild(handle);

    enableDrag(box, zone);
    enableResize(handle, box, zone);

    return box;
}

// 드래그 중에는 zone 데이터 객체(메모리)만 바로 바꾸고, 화면 전체를 다시 그리지 않는다.
// (다시 그리면 드래그 중인 요소 자체가 사라져서 끊김)
function enableDrag(box, zone) {

    box.addEventListener("mousedown", function(event) {

        if (event.target === box.querySelector(".zone-box-handle")) {
            return;
        }

        event.preventDefault();

        const rect = zoneCameraContainer.getBoundingClientRect();
        const startX = event.clientX;
        const startY = event.clientY;
        const startLeft = zone.regionX;
        const startTop = zone.regionY;

        function onMove(moveEvent) {

            const deltaX = ((moveEvent.clientX - startX) / rect.width) * 100;
            const deltaY = ((moveEvent.clientY - startY) / rect.height) * 100;

            zone.regionX = clamp(startLeft + deltaX, 0, 100 - zone.regionWidth);
            zone.regionY = clamp(startTop + deltaY, 0, 100 - zone.regionHeight);

            box.style.left = zone.regionX + "%";
            box.style.top = zone.regionY + "%";
        }

        function onUp() {
            document.removeEventListener("mousemove", onMove);
            document.removeEventListener("mouseup", onUp);
        }

        document.addEventListener("mousemove", onMove);
        document.addEventListener("mouseup", onUp);
    });
}

function enableResize(handle, box, zone) {

    handle.addEventListener("mousedown", function(event) {

        event.preventDefault();
        event.stopPropagation();

        const rect = zoneCameraContainer.getBoundingClientRect();
        const startX = event.clientX;
        const startY = event.clientY;
        const startWidth = zone.regionWidth;
        const startHeight = zone.regionHeight;

        function onMove(moveEvent) {

            const deltaW = ((moveEvent.clientX - startX) / rect.width) * 100;
            const deltaH = ((moveEvent.clientY - startY) / rect.height) * 100;

            zone.regionWidth = clamp(startWidth + deltaW, 5, 100 - zone.regionX);
            zone.regionHeight = clamp(startHeight + deltaH, 5, 100 - zone.regionY);

            box.style.width = zone.regionWidth + "%";
            box.style.height = zone.regionHeight + "%";
        }

        function onUp() {
            document.removeEventListener("mousemove", onMove);
            document.removeEventListener("mouseup", onUp);
        }

        document.addEventListener("mousemove", onMove);
        document.addEventListener("mouseup", onUp);
    });
}

/*
===========================================
    오른쪽 의약품(구역) 목록
===========================================
*/

function renderList() {

    // 오른쪽 목록은 카메라와 상관없이 전체를 보여준다 (관리 편의를 위해).
    // 왼쪽 화면만 현재 선택된 카메라의 구역을 보여준다.
    const zones = FramZones.getZones();
    const cameras = FramZones.getCameras();

    if (zones.length === 0) {
        zoneList.innerHTML = `<li class="zone-list-empty">등록된 구역이 없습니다.</li>`;
        return;
    }

    zoneList.innerHTML = zones.map(zone => `

        <li class="zone-list-item">

            <span class="zone-color-dot" style="background:${colorFor(zone.medicineId)}"></span>

            <span class="zone-name-label" title="${escapeHtml(zone.medicineName)}">${escapeHtml(zone.medicineName)}</span>

            <select class="zone-camera-select" data-medicine-id="${zone.medicineId}">
                ${cameras.map(camera => `
                    <option value="${camera.cameraId}" ${camera.cameraId === zone.cameraId ? "selected" : ""}>
                        ${escapeHtml(camera.cameraName)}
                    </option>
                `).join("")}
            </select>

            <button type="button" class="zone-edit-btn" data-medicine-id="${zone.medicineId}">
                수정
            </button>

            <button type="button" class="zone-delete-btn" data-medicine-id="${zone.medicineId}">
                삭제
            </button>

        </li>

    `).join("");

    zoneList.querySelectorAll(".zone-edit-btn").forEach(button => {

        button.addEventListener("click", function() {

            const zone = FramZones.getZones().find(item => item.medicineId === Number(button.dataset.medicineId));

            if (zone) {
                openMedicineModal(zone);
            }
        });
    });

    zoneList.querySelectorAll(".zone-camera-select").forEach(select => {

        select.addEventListener("change", function() {

            const medicineId = Number(select.dataset.medicineId);
            const newCameraId = Number(select.value);

            // 다른 카메라로 옮기면 기존 좌표는 의미가 없어서 기본 위치로 다시 잡는다
            FramZones.updateLocal(medicineId, {
                cameraId: newCameraId,
                regionX: 35,
                regionY: 35,
                regionWidth: 20,
                regionHeight: 20
            });

            FramZones.setCurrentCameraId(newCameraId);
        });
    });

    zoneList.querySelectorAll(".zone-delete-btn").forEach(button => {

        button.addEventListener("click", async function() {

            const confirmed = confirm("이 의약품과 구역을 삭제하시겠습니까?");

            if (!confirmed) {
                return;
            }

            try {
                await FramZones.removeZone(Number(button.dataset.medicineId));
            } catch (error) {
                zoneMessage.textContent = error.message;
            }
        });
    });
}

/*
===========================================
    전체 렌더링 + 이벤트
===========================================
*/

function render() {
    renderCameraView();
    renderList();
}

FramZones.subscribe(render);

document.getElementById("zoneCameraPrevBtn").addEventListener("click", function() {

    const cameras = FramZones.getCameras();

    if (cameras.length === 0) {
        return;
    }

    const currentIndex = cameras.findIndex(camera => camera.cameraId === FramZones.getCurrentCameraId());
    const prevIndex = (currentIndex - 1 + cameras.length) % cameras.length;

    FramZones.setCurrentCameraId(cameras[prevIndex].cameraId);
});

document.getElementById("zoneCameraNextBtn").addEventListener("click", function() {

    const cameras = FramZones.getCameras();

    if (cameras.length === 0) {
        return;
    }

    const currentIndex = cameras.findIndex(camera => camera.cameraId === FramZones.getCurrentCameraId());
    const nextIndex = (currentIndex + 1) % cameras.length;

    FramZones.setCurrentCameraId(cameras[nextIndex].cameraId);
});

document.getElementById("zoneAddBtn").addEventListener("click", function() {

    zoneMessage.textContent = "";
    openMedicineModal(null);
});

document.getElementById("zoneSaveBtn").addEventListener("click", async function() {

    zoneMessage.textContent = "저장 중...";

    try {
        await FramZones.saveAll();
        zoneMessage.textContent = "저장되었습니다.";
    } catch (error) {
        zoneMessage.textContent = error.message;
    }
});

FramZones.loadAll();
