/*
===========================================
    구역(Zone) 상태 저장소

    anomaly-store.js와 비슷하게 subscribe/notify 패턴을 쓰지만,
    구역 데이터는 결국 DB에 저장돼야 하는 설정값이라
    localStorage 대신 메모리 상태 + 서버 API로 관리한다.
===========================================
*/

(() => {
    "use strict";

    let zones = [];
    let cameras = [];
    let currentCameraId = null;

    const listeners = new Set();

    function notify() {
        listeners.forEach(fn => fn());
    }

    async function loadAll() {

        const [zoneResponse, cameraResponse] = await Promise.all([
            fetch("/api/medicine-zones"),
            fetch("/api/cameras")
        ]);

        zones = zoneResponse.ok ? await zoneResponse.json() : [];

        // OCR 스캔용 카메라는 의약품 구역을 둘 수 없어서, 이 화면(구역설정)에서는 처음부터 뺀다.
        // (드롭다운/카메라 전환/기본 선택 카메라 전부 이 목록을 그대로 쓰므로, 여기서 한 번만
        // 걸러내면 이 화면 어디에서도 OCR 카메라가 안 보이고 선택도 안 된다)
        const allCameras = cameraResponse.ok ? await cameraResponse.json() : [];
        cameras = allCameras.filter(camera => camera.cameraRole !== "OCR_SCAN");

        if (currentCameraId === null && cameras.length > 0) {
            currentCameraId = cameras[0].cameraId;
        }

        notify();
    }

    function getZones() {
        return zones;
    }

    function getCameras() {
        return cameras;
    }

    function getCurrentCameraId() {
        return currentCameraId;
    }

    function setCurrentCameraId(cameraId) {
        currentCameraId = cameraId;
        notify();
    }

    function getZonesForCurrentCamera() {
        return zones.filter(zone => zone.cameraId === currentCameraId);
    }

    // 이 함수로 바꾼 값은 화면(메모리)에만 반영되고, "저장" 버튼을 눌러야 서버에 올라간다.
    // (드래그 중처럼 자주 바뀌는 값은 매번 서버에 보내지 않기 위함)
    function updateLocal(medicineId, patch) {

        const zone = zones.find(item => item.medicineId === medicineId);

        if (!zone) {
            return;
        }

        Object.assign(zone, patch);

        notify();
    }

    // details: { medicineName, highRiskYn, manufacturer, registerDate, minQty }
    async function addZone(details) {

        if (currentCameraId === null) {
            throw new Error("먼저 실시간 모니터링에서 카메라를 등록해 주세요.");
        }

        const response = await fetch("/api/medicine-zones", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ...details, cameraId: currentCameraId })
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || "추가에 실패했습니다.");
        }

        zones.push(data);
        notify();

        return data;
    }

    // 수정 창에서 바꾼 항목(details)만 그 약품에 바로 저장한다.
    // 구역 위치/카메라는 건드리지 않으므로 드래그로 옮긴 값은 계속 "저장" 버튼으로 저장한다.
    async function updateZoneDetails(medicineId, details) {

        const response = await fetch(`/api/medicine-zones/${medicineId}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(details)
        });

        const data = await response.json();

        if (!response.ok) {
            throw new Error(data.message || "저장에 실패했습니다.");
        }

        // 위치(regionX 등)는 아직 저장 전 값이 메모리에 있을 수 있어서 수정한 항목만 반영
        updateLocal(medicineId, details);
    }

    async function removeZone(medicineId) {

        const response = await fetch(`/api/medicine-zones/${medicineId}`, { method: "DELETE" });

        if (!response.ok) {
            const data = await response.json();
            throw new Error(data.message || "삭제에 실패했습니다.");
        }

        zones = zones.filter(item => item.medicineId !== medicineId);
        notify();
    }

    // 현재 메모리에 있는 모든 구역 상태를 서버에 그대로 반영
    async function saveAll() {

        const responses = await Promise.all(zones.map(zone => {

            return fetch(`/api/medicine-zones/${zone.medicineId}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    medicineName: zone.medicineName,
                    highRiskYn: zone.highRiskYn,
                    manufacturer: zone.manufacturer,
                    registerDate: zone.registerDate,
                    minQty: zone.minQty ?? null,
                    cameraId: zone.cameraId,
                    regionX: zone.regionX,
                    regionY: zone.regionY,
                    regionWidth: zone.regionWidth,
                    regionHeight: zone.regionHeight
                })
            });
        }));

        if (responses.some(response => !response.ok)) {
            throw new Error("일부 항목 저장에 실패했습니다.");
        }
    }

    window.FramZones = {
        loadAll,
        getZones,
        getCameras,
        getCurrentCameraId,
        setCurrentCameraId,
        getZonesForCurrentCamera,
        updateLocal,
        addZone,
        updateZoneDetails,
        removeZone,
        saveAll,
        subscribe(fn) {
            listeners.add(fn);
            return () => listeners.delete(fn);
        }
    };
})();
