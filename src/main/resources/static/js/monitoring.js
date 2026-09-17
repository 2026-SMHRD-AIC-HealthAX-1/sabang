/*
===========================================
    실시간 모니터링

    카메라 목록은 더 이상 하드코딩하지 않고
    /api/cameras (CAMERA 테이블)에서 실제로 불러온다.

    카메라 추가/삭제는 구독 중인 관리자만 가능해서,
    관리 버튼은 기본적으로 숨겨두고(hidden) 관리자로
    확인됐을 때만 보여준다 (깜빡이지 않게).
===========================================
*/

let cameras = [];
let currentCamera = 0;

const cameraVideo = document.getElementById("cameraVideo");
const cameraNameEl = document.getElementById("cameraName");
const cameraEmpty = document.getElementById("cameraEmpty");
const cameraPrevBtn = document.getElementById("cameraPrevBtn");
const cameraNextBtn = document.getElementById("cameraNextBtn");
const cameraManageBtn = document.getElementById("cameraManageBtn");

const cameraManageModal = document.getElementById("cameraManageModal");
const cameraModalBackdrop = document.getElementById("cameraModalBackdrop");
const cameraModalCloseBtn = document.getElementById("cameraModalCloseBtn");
const cameraManageList = document.getElementById("cameraManageList");
const cameraAddForm = document.getElementById("cameraAddForm");
const cameraAddInput = document.getElementById("cameraAddInput");
const cameraManageMessage = document.getElementById("cameraManageMessage");

/*
===========================================
    카메라 화면 표시
===========================================
*/

function renderCurrentCamera() {

    if (cameras.length === 0) {

        cameraVideo.hidden = true;
        cameraEmpty.hidden = false;
        cameraNameEl.textContent = "";
        cameraPrevBtn.hidden = true;
        cameraNextBtn.hidden = true;

        return;
    }

    cameraVideo.hidden = false;
    cameraEmpty.hidden = true;
    cameraPrevBtn.hidden = cameras.length < 2;
    cameraNextBtn.hidden = cameras.length < 2;

    const camera = cameras[currentCamera];

    cameraVideo.src = camera.streamUrl;
    cameraNameEl.textContent = camera.cameraName;
}

async function loadCameras() {

    try {

        const response = await fetch("/api/cameras");

        cameras = response.ok ? await response.json() : [];

    } catch (error) {
        cameras = [];
    }

    if (currentCamera >= cameras.length) {
        currentCamera = 0;
    }

    renderCurrentCamera();
}

cameraPrevBtn.addEventListener("click", function() {

    currentCamera = (currentCamera - 1 + cameras.length) % cameras.length;

    renderCurrentCamera();
});

cameraNextBtn.addEventListener("click", function() {

    currentCamera = (currentCamera + 1) % cameras.length;

    renderCurrentCamera();
});

/*
===========================================
    관리자 전용 - 카메라 관리 패널
===========================================
*/

async function checkAdminAndReveal() {

    try {

        const response = await fetch("/api/auth/me");

        if (!response.ok) {
            return;
        }

        const me = await response.json();

        if (me.isAdmin) {
            cameraManageBtn.hidden = false;
        }

    } catch (error) {
        // 확인 실패 시 관리 버튼은 계속 숨김
    }
}

function openManageModal() {

    cameraManageMessage.textContent = "";
    renderManageList();

    cameraManageModal.hidden = false;
}

function closeManageModal() {
    cameraManageModal.hidden = true;
}

function renderManageList() {

    if (cameras.length === 0) {

        cameraManageList.innerHTML = `<li>등록된 카메라가 없습니다.</li>`;
        return;
    }

    cameraManageList.innerHTML = cameras.map(camera => `

        <li>
            <span>${camera.cameraName}</span>
            <button type="button" data-camera-id="${camera.cameraId}">삭제</button>
        </li>

    `).join("");

    cameraManageList.querySelectorAll("button[data-camera-id]").forEach(button => {

        button.addEventListener("click", () => deleteCamera(button.dataset.cameraId));
    });
}

async function deleteCamera(cameraId) {

    try {

        const response = await fetch(`/api/cameras/${cameraId}`, { method: "DELETE" });

        if (!response.ok) {
            const data = await response.json();
            cameraManageMessage.textContent = data.message || "삭제에 실패했습니다.";
            return;
        }

        await loadCameras();

        renderManageList();

    } catch (error) {
        cameraManageMessage.textContent = "삭제에 실패했습니다. 다시 시도해 주세요.";
    }
}

cameraManageBtn.addEventListener("click", openManageModal);
cameraModalCloseBtn.addEventListener("click", closeManageModal);
cameraModalBackdrop.addEventListener("click", closeManageModal);

cameraAddForm.addEventListener("submit", async function(event) {

    event.preventDefault();

    const cameraName = cameraAddInput.value.trim();

    if (!cameraName) {
        return;
    }

    cameraManageMessage.textContent = "";

    try {

        const response = await fetch("/api/cameras", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ cameraName })
        });

        const data = await response.json();

        if (!response.ok) {
            cameraManageMessage.textContent = data.message || "추가에 실패했습니다.";
            return;
        }

        cameraAddInput.value = "";

        await loadCameras();

        renderManageList();

    } catch (error) {
        cameraManageMessage.textContent = "추가에 실패했습니다. 다시 시도해 주세요.";
    }
});

/*
===========================================
    시작
===========================================
*/

loadCameras();
checkAdminAndReveal();
