import threading
import time
import cv2
import os
import requests

from datetime import datetime
from ultralytics import YOLO
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from collections import Counter

# 새로 만든 OCR 모듈 임포트
import ocr_processor

# YOLO 세그멘테이션 모델 불러오기
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "best.pt")

# 스프링 웹 서버가 이미지를 읽어갈 수 있는 실제 저장 폴더 경로 설정
SPRING_UPLOAD_DIR = os.path.join(BASE_DIR, "..", "src", "main", "resources", "static", "uploads")
os.makedirs(SPRING_UPLOAD_DIR, exist_ok=True)

# 전표(slip) 인식을 위한 설정값
SLIP_WAIT_TIME = 1.0      # 전표가 카메라에 1초 동안 유지되어야 캡처
SLIP_COOLDOWN = 10.0      # 한 번 캡처 후 다음 캡처까지 10초 대기 (중복 캡처 방지)

model = YOLO(MODEL_PATH)
model_lock = threading.Lock()
SPRING_URL = "http://127.0.0.1:8089"
ZONE_REFRESH_INTERVAL_SEC = 30

# 구역별 개수를 "안정됐다"고 확정해서 출력/전송할지 판단하는 주기
COUNT_DECISION_INTERVAL_SEC = 1.5

# 전표 인식 후, 반출로 세그먼트 개수가 줄어드는 걸 이만큼(초) 안에 못 잡으면 포기한다
DISPENSE_WATCH_TIMEOUT_SEC = 300

# 이 파이썬 인스턴스가 담당하는 병원(관리자) 계정
ADMIN_ID = "admin"


# ====================================================
# Spring API 연동 및 카메라 레지스트리 관리
# ====================================================
def fetch_camera_registry(admin_id):
    try:
        response = requests.get(f"{SPRING_URL}/api/cameras/by-admin/{admin_id}", timeout=5)
        if response.status_code != 200:
            print("카메라 목록 조회 실패:", response.status_code)
            return None

        python_id_to_spring_id = {}
        ocr_python_id = None

        for camera in response.json():
            stream_url = camera.get("streamUrl") or ""
            try:
                python_id = int(stream_url.rstrip("/").rsplit("/", 1)[-1])
            except ValueError:
                continue

            python_id_to_spring_id[python_id] = camera["cameraId"]

            if camera.get("cameraRole") == "OCR_SCAN":
                ocr_python_id = python_id

        return python_id_to_spring_id, ocr_python_id

    except Exception as e:
        print("카메라 목록 조회 오류:", e)
        return None


def fetch_camera_registry_with_retry(admin_id, retry_interval_sec=5, max_attempts=12):
    for attempt in range(max_attempts):
        result = fetch_camera_registry(admin_id)
        if result is not None:
            return result
        print(f"카메라 목록 조회 재시도 {attempt + 1}/{max_attempts}...")
        time.sleep(retry_interval_sec)

    print("카메라 목록 조회 계속 실패 - 일단 기본 매핑으로 시작")
    # 기본값: 0번=OCR_SCAN(cameraId: 1), 1번=MONITOR(cameraId: 2)
    return {0: 1, 1: 2}, 0


PYTHON_ID_TO_SPRING_ID, OCR_PYTHON_ID = fetch_camera_registry_with_retry(ADMIN_ID)
if OCR_PYTHON_ID is None:
    OCR_PYTHON_ID = 0  # 기본값: 0번 카메라를 OCR로 설정


def refresh_camera_registry_loop():
    while True:
        time.sleep(ZONE_REFRESH_INTERVAL_SEC)
        result = fetch_camera_registry(ADMIN_ID)
        if result is not None:
            fresh_mapping, _ = result
            PYTHON_ID_TO_SPRING_ID.clear()
            PYTHON_ID_TO_SPRING_ID.update(fresh_mapping)


def get_zones(camera_id):
    spring_camera_id = PYTHON_ID_TO_SPRING_ID.get(camera_id, camera_id)
    try:
        response = requests.get(
            f"{SPRING_URL}/api/medicine-zones/camera/{spring_camera_id}",
            timeout=2
        )
        if response.status_code == 200:
            return response.json()
        return []
    except Exception as e:
        print("Spring 구역 API 연결 실패:", e)
        return []


def send_counts(camera_id, counts):
    spring_camera_id = PYTHON_ID_TO_SPRING_ID.get(camera_id, camera_id)
    try:
        response = requests.post(
            f"{SPRING_URL}/api/medicine-zones/count",
            json={
                "cameraId": spring_camera_id,
                "counts": counts
            },
            timeout=2
        )
        if response.status_code != 200:
            print("카운트 전송 실패:", response.status_code)
    except Exception as e:
        print("Spring 카운트 전송 오류:", e)


def is_inside_zone(center_x, center_y, zone, frame_width, frame_height):
    x1 = frame_width * (zone["regionX"] / 100)
    y1 = frame_height * (zone["regionY"] / 100)
    x2 = frame_width * ((zone["regionX"] + zone["regionWidth"]) / 100)
    y2 = frame_height * ((zone["regionY"] + zone["regionHeight"]) / 100)
    return x1 <= center_x <= x2 and y1 <= center_y <= y2


# ====================================================
# 반출 감시 및 알림 로직 (팀원 기능 완벽 통합)
# ====================================================
def report_outbound(slip_id, medicine_id, outbound_qty):
    try:
        response = requests.post(
            f"{SPRING_URL}/api/outbound",
            json={"slipId": slip_id, "medicineId": medicine_id, "outboundQty": outbound_qty},
            timeout=5
        )
        if response.status_code == 200:
            return response.json()
        print("반출 보고 실패:", response.status_code, response.text)
    except Exception as e:
        print("반출 보고 오류:", e)
    return None


def snapshot_zone_counts():
    counts = {}
    for cam_id, state in camera_state.items():
        if cam_id == OCR_PYTHON_ID:
            continue
        with state["lock"]:
            for name, count in state["stable_zone_counts"].items():
                counts[name] = counts.get(name, 0) + count
    return counts


_medicine_watch_lock_guard = threading.Lock()
_medicine_watch_locks = {}


def get_medicine_watch_lock(medicine_name):
    with _medicine_watch_lock_guard:
        if medicine_name not in _medicine_watch_locks:
            _medicine_watch_locks[medicine_name] = threading.Lock()
        return _medicine_watch_locks[medicine_name]


def watch_single_item(slip_id, item):
    medicine_name = item.get("medicineName") or item.get("의약품명")
    medicine_id = item.get("medicineId")
    request_qty = item.get("requestQty") or int(item.get("수량", 1))

    if not medicine_name:
        return

    lock = get_medicine_watch_lock(medicine_name)
    with lock:
        baseline = snapshot_zone_counts().get(medicine_name, 0)
        started = time.time()
        print(f"[전표 {slip_id}] {medicine_name} 반출 감시 시작 (기준 {baseline}개)")

        while time.time() - started < DISPENSE_WATCH_TIMEOUT_SEC:
            time.sleep(1)
            now_count = snapshot_zone_counts().get(medicine_name, 0)

            if now_count < baseline:
                dispensed = baseline - now_count
                result = report_outbound(slip_id, medicine_id, dispensed)
                if result is not None:
                    tag = "이상" if result.get("abnormal") else "정상"
                    print(f"[전표 {slip_id}] {medicine_name} 반출 {dispensed}개 확인 ({tag}, 요청 {request_qty}개)")
                return

        print(f"[전표 {slip_id}] {medicine_name} 시간 초과로 감시 종료")


def watch_and_compare(slip_id, items):
    for item in items:
        threading.Thread(target=watch_single_item, args=(slip_id, item), daemon=True).start()


# 백그라운드에서 실제 네이버 OCR 및 DB 저장 수행 후 반출 감시 트리거
def run_ocr_background(image_path):
    print(f"\n[자동 캡처] 전표 OCR 분석 및 DB 저장을 시작합니다: {image_path}")
    result = ocr_processor.process_slip_image(image_path)
    print(f"[자동 캡처 결과] {result}\n")

    if result and result.get("status") == "success":
        basic_info = result.get("basic_info", {})
        slip_id = basic_info.get("전표번호")
        med_list = result.get("medicines", [])
        if slip_id and slip_id != "찾지 못함" and med_list:
            watch_and_compare(slip_id, med_list)


# ====================================================
# 카메라 물리 장치 연결 (MSMF / DSHOW 자동 호환)
# - 0번 (CAM 0, streamUrl /video/0) : 외장 웹캠 (장치 1) -> OCR 전표용
# - 1번 (CAM 1, streamUrl /video/1) : 노트북 웹캠 (장치 0) -> 세그먼트용
# ====================================================
def open_camera(index):
    # 1) MSMF 우선 시도
    cap = cv2.VideoCapture(index, cv2.CAP_MSMF)
    if cap.isOpened():
        for _ in range(5):
            ret, frame = cap.read()
            if ret and frame.std() > 2.0:
                return cap
            time.sleep(0.05)
        cap.release()

    # 2) DSHOW 시도
    cap = cv2.VideoCapture(index, cv2.CAP_DSHOW)
    if cap.isOpened():
        ret, frame = cap.read()
        if ret and frame.std() > 2.0:
            return cap
        cap.release()

    return cv2.VideoCapture(index)


app = FastAPI()

# 0번 = 외장 웹캠(하드웨어 1), 1번 = 노트북 내장(하드웨어 0)
cameras = {
    0: open_camera(1),
    1: open_camera(0)
}

camera_state = {
    camera_id: {
        "lock": threading.Lock(),
        "frame_bytes": None,
        "stable_zone_counts": {},
    }
    for camera_id in cameras
}


# ====================================================
# 1) 세그먼트 전용 감지 루프 (노트북 카메라: 시간 기반 안정화 적용)
# ====================================================
def segment_detection_loop(camera_id):
    camera = cameras[camera_id]
    state = camera_state[camera_id]

    zones = get_zones(camera_id)
    last_zone_refresh = time.time()

    stable_zone_counts = {}
    count_accumulator = {}
    last_decision_time = time.time()

    print(f"[시작] CAM {camera_id} : 세그먼트(의약품 모니터링) 루프 시작")

    while True:
        if time.time() - last_zone_refresh > ZONE_REFRESH_INTERVAL_SEC:
            zones = get_zones(camera_id)
            last_zone_refresh = time.time()
            count_accumulator = {}

        success, frame = camera.read()
        if not success:
            print(f"CAM {camera_id} (세그먼트) 영상 읽기 실패")
            time.sleep(1)
            continue

        with model_lock:
            results = model(frame, conf=0.6, verbose=False)

        frame_height, frame_width = frame.shape[:2]
        zone_counts = {zone["medicineName"]: 0 for zone in zones}

        for box in results[0].boxes:
            x1, y1, x2, y2 = box.xyxy[0].tolist()
            center_x = (x1 + x2) / 2
            center_y = (y1 + y2) / 2
            class_id = int(box.cls[0])
            class_name = model.names[class_id]

            for zone in zones:
                medicine_name = zone["medicineName"]
                if class_name != medicine_name:
                    continue
                if is_inside_zone(center_x, center_y, zone, frame_width, frame_height):
                    zone_counts[medicine_name] += 1

        for name, count in zone_counts.items():
            count_accumulator.setdefault(name, []).append(count)

        # 판단 주기가 찼을 때만 안정값을 확정하고, 변경 시 Spring에 전송
        if time.time() - last_decision_time >= COUNT_DECISION_INTERVAL_SEC:
            new_stable_counts = {
                name: Counter(counts).most_common(1)[0][0]
                for name, counts in count_accumulator.items()
            }

            if new_stable_counts != stable_zone_counts:
                print(f"\n===== CAM {camera_id} 안정화된 구역별 개수 =====")
                for name, count in new_stable_counts.items():
                    print(f"{name}: {count}")
                print("==============================================", flush=True)

                send_counts(camera_id, new_stable_counts)

            stable_zone_counts = new_stable_counts
            count_accumulator = {}
            last_decision_time = time.time()

        annotated_frame = results[0].plot()
        success, buffer = cv2.imencode(".jpg", annotated_frame)
        if not success:
            continue

        with state["lock"]:
            state["frame_bytes"] = buffer.tobytes()
            state["stable_zone_counts"] = stable_zone_counts


# ====================================================
# OpenCV 기반 전표(종이/문서) 자동 감지 함수
# ====================================================
def detect_slip_contour(frame):
    h, w = frame.shape[:2]
    frame_area = h * w

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)

    # 1) Canny 엣지 검출
    edges = cv2.Canny(blurred, 30, 120)
    kernel = cv2.getStructuringElement(cv2.MORPH_RECT, (5, 5))
    dilated = cv2.dilate(edges, kernel, iterations=2)
    contours, _ = cv2.findContours(dilated, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)

    candidates = []
    for cnt in contours:
        area = cv2.contourArea(cnt)
        if frame_area * 0.05 < area < frame_area * 0.90:
            peri = cv2.arcLength(cnt, True)
            approx = cv2.approxPolyDP(cnt, 0.035 * peri, True)
            if 4 <= len(approx) <= 6:
                candidates.append((area, approx))

    # 2) 밝기 이진화 보조
    if not candidates:
        _, thresh = cv2.threshold(blurred, 130, 255, cv2.THRESH_BINARY)
        contours, _ = cv2.findContours(thresh, cv2.RETR_EXTERNAL, cv2.CHAIN_APPROX_SIMPLE)
        for cnt in contours:
            area = cv2.contourArea(cnt)
            if frame_area * 0.06 < area < frame_area * 0.90:
                peri = cv2.arcLength(cnt, True)
                approx = cv2.approxPolyDP(cnt, 0.04 * peri, True)
                if 4 <= len(approx) <= 6:
                    candidates.append((area, approx))

    if candidates:
        candidates.sort(key=lambda x: x[0], reverse=True)
        return candidates[0][1]

    return None


# ====================================================
# 2) OCR 전용 루프 (외장 웹캠: 종이 윤곽 감지 및 1초 유지 시 자동 캡처)
# ====================================================
def ocr_detection_loop(camera_id):
    camera = cameras[camera_id]
    state = camera_state[camera_id]

    slip_first_seen_time = None
    last_slip_capture_time = 0
    capture_success_display_until = 0

    print(f"[시작] CAM {camera_id} : OCR 전표 인식 루프 시작 (종이 윤곽 자동 감지)")

    while True:
        success, frame = camera.read()
        if not success:
            print(f"CAM {camera_id} (OCR) 영상 읽기 실패")
            time.sleep(1)
            continue

        h, w = frame.shape[:2]
        display_frame = frame.copy()

        # 전표/문서 윤곽 감지
        paper_contour = detect_slip_contour(frame)
        slip_detected_now = paper_contour is not None
        current_time = time.time()

        if slip_detected_now:
            cv2.drawContours(display_frame, [paper_contour], -1, (0, 255, 0), 3)

            if current_time - last_slip_capture_time > SLIP_COOLDOWN:
                if slip_first_seen_time is None:
                    slip_first_seen_time = current_time
                    cv2.putText(display_frame, "Slip Detected: Hold still...", (20, 40),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 255), 2)
                elif current_time - slip_first_seen_time >= SLIP_WAIT_TIME:
                    # 1초 이상 유지 -> 캡처 실행!
                    filename = f"auto_slip_cam{camera_id}_{int(current_time)}.jpg"
                    filepath = os.path.join(SPRING_UPLOAD_DIR, filename)

                    # 깨끗한 원본 프레임 저장
                    cv2.imwrite(filepath, frame)
                    threading.Thread(target=run_ocr_background, args=(filepath,), daemon=True).start()

                    last_slip_capture_time = current_time
                    slip_first_seen_time = None
                    capture_success_display_until = current_time + 4.0
                else:
                    remain = SLIP_WAIT_TIME - (current_time - slip_first_seen_time)
                    cv2.putText(display_frame, f"Capturing in {remain:.1f}s...", (20, 40),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 0), 2)
            else:
                remain_cd = int(SLIP_COOLDOWN - (current_time - last_slip_capture_time))
                cv2.putText(display_frame, f"Cooldown: next scan in {remain_cd}s", (20, 40),
                            cv2.FONT_HERSHEY_SIMPLEX, 0.7, (200, 200, 200), 2)
        else:
            slip_first_seen_time = None
            gw, gh = int(w * 0.75), int(h * 0.75)
            gx1, gy1 = (w - gw) // 2, (h - gh) // 2
            cv2.rectangle(display_frame, (gx1, gy1), (gx1 + gw, gy1 + gh), (255, 200, 100), 1)
            cv2.putText(display_frame, "Place Slip inside frame", (gx1 + 10, gy1 - 10),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.65, (255, 200, 100), 2)

        if current_time < capture_success_display_until:
            cv2.putText(display_frame, "[SUCCESS] Slip Captured! Processing OCR & DB...", (20, 80),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.75, (0, 255, 0), 2)

        success, buffer = cv2.imencode(".jpg", display_frame)
        if not success:
            continue

        with state["lock"]:
            state["frame_bytes"] = buffer.tobytes()

        time.sleep(0.03)


# ====================================================
# 스트리밍 및 엔드포인트
# ====================================================
def generate_frames(camera_id):
    # 요청된 ID가 없으면 기본 첫 번째 카메라 사용
    state = camera_state.get(camera_id)
    if state is None:
        keys = list(camera_state.keys())
        state = camera_state[keys[0]] if keys else None

    if state is None:
        print(f"[경고] 카메라 ID {camera_id} 에 해당하는 상태가 없습니다.")
        return

    while True:
        with state["lock"]:
            frame_bytes = state["frame_bytes"]
        if frame_bytes is None:
            time.sleep(0.1)
            continue
        yield (b"--frame\r\n" b"Content-Type: image/jpeg\r\n\r\n" + frame_bytes + b"\r\n")
        time.sleep(0.03)


# 백그라운드 스레드 시작
# OCR_PYTHON_ID (기본 0번) 카메라는 ocr_detection_loop, 나머지는 segment_detection_loop 실행
for _camera_id in cameras:
    if _camera_id == OCR_PYTHON_ID:
        threading.Thread(target=ocr_detection_loop, args=(_camera_id,), daemon=True).start()
    else:
        threading.Thread(target=segment_detection_loop, args=(_camera_id,), daemon=True).start()

threading.Thread(target=refresh_camera_registry_loop, daemon=True).start()


@app.get("/video/{camera_id}")
def video(camera_id: int):
    return StreamingResponse(
        generate_frames(camera_id),
        media_type="multipart/x-mixed-replace; boundary=frame"
    )


@app.get("/status")
def status():
    return {
        f"CAM {camera_id}": camera.isOpened()
        for camera_id, camera in cameras.items()
    }


@app.get("/")
def home():
    return {"message": "PharmVision Camera API"}
