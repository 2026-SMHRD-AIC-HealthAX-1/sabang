import threading
import time
import cv2
import os
import requests

from ultralytics import YOLO
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from collections import deque, Counter

# 새로 만든 OCR 모듈 임포트
import ocr_processor

# YOLO 세그멘테이션 모델 불러오기
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "best.pt")

# ⭐ 스프링 웹 서버가 이미지를 읽어갈 수 있는 실제 저장 폴더 경로 설정
SPRING_UPLOAD_DIR = os.path.join(BASE_DIR, "..", "src", "main", "resources", "static", "uploads")
os.makedirs(SPRING_UPLOAD_DIR, exist_ok=True) # 폴더가 없으면 자동 생성

# 전표(slip) 인식을 위한 설정값 ⭐ (본인의 YOLO 클래스명에 맞게 변경하세요!)
SLIP_CLASS_NAME = "slip"  # 예: "receipt", "document", "전표" 등 
SLIP_WAIT_TIME = 1.0      # 전표가 카메라에 1초 동안 유지되어야 캡처
SLIP_COOLDOWN = 10.0      # 한 번 캡처 후 다음 캡처까지 10초 대기 (중복 캡처 방지)

model = YOLO(MODEL_PATH)
model_lock = threading.Lock()
SPRING_URL = "http://127.0.0.1:8089"
ZONE_REFRESH_INTERVAL_SEC = 30


def get_zones(camera_id):
    try:
        response = requests.get(f"{SPRING_URL}/api/medicine-zones/camera/{camera_id}", timeout=2)
        if response.status_code == 200:
            return response.json()
        print("구역 조회 실패:", response.status_code)
        return []
    except Exception as e:
        print("Spring 구역 API 연결 실패:", e)
        return []

def send_counts(camera_id, counts):
    try:
        response = requests.post(
            f"{SPRING_URL}/api/medicine-zones/count",
            json={"cameraId": camera_id, "counts": counts},
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

# 백그라운드에서 전표 OCR을 처리할 함수 (카메라 렉 방지)
def run_ocr_background(image_path):
    print(f"\n[자동 캡처] 전표 OCR 분석 및 DB 저장을 시작합니다: {image_path}")
    result = ocr_processor.process_slip_image(image_path)
    print(f"[자동 캡처 결과] {result}\n")


app = FastAPI()

# ====================================================
# 카메라 매핑 설정 (DB 및 사용자 설정과 1:1 일치)
# - video/0 (cam1, ID 1) : 새로 연결한 외장 웹캠 (장치 1) -> OCR 전표 인식용
# - video/1 (cam2, ID 2) : 노트북 내장 카메라 (장치 0) -> 세그먼트(의약품 모니터링)용
# ====================================================
CAMERA_DEVICES = {
    1: {"device_index": 1, "role": "OCR_SCAN", "name": "외장 웹캠 (ABKO, OCR 전표용)"},
    2: {"device_index": 0, "role": "MONITOR", "name": "노트북 내장 웹캠 (HP, 세그먼트용)"}
}

def open_camera(index):
    # 1) MSMF (Media Foundation) 우선 시도 (새 웹캠 및 내장 카메라 호환)
    cap = cv2.VideoCapture(index, cv2.CAP_MSMF)
    if cap.isOpened():
        for _ in range(5):
            ret, frame = cap.read()
            if ret and frame.std() > 2.0:  # 단순 검은 화면이 아닌 실제 정상 영상 확인
                return cap
            time.sleep(0.05)
        cap.release()

    # 2) 실패 시 DSHOW 시도
    cap = cv2.VideoCapture(index, cv2.CAP_DSHOW)
    if cap.isOpened():
        ret, frame = cap.read()
        if ret and frame.std() > 2.0:
            return cap
        cap.release()

    # 3) 최후 기본 백엔드
    return cv2.VideoCapture(index)

cameras = {
    cam_id: open_camera(cfg["device_index"])
    for cam_id, cfg in CAMERA_DEVICES.items()
}

camera_state = {
    camera_id: {
        "lock": threading.Lock(),
        "frame_bytes": None,
        "stable_zone_counts": {},
        "role": CAMERA_DEVICES[camera_id]["role"]
    }
    for camera_id in cameras
}


# ====================================================
# 1) 세그먼트 전용 루프 (노트북 카메라: 의약품 탐지 및 구역 카운트)
# ====================================================
def segment_detection_loop(camera_id):
    camera = cameras[camera_id]
    state = camera_state[camera_id]

    zones = get_zones(camera_id)
    last_zone_refresh = time.time()
    last_zone_counts = None
    count_history = {}

    print(f"[시작] CAM {camera_id} : 세그먼트(의약품 모니터링) 루프 시작")

    while True:
        if time.time() - last_zone_refresh > ZONE_REFRESH_INTERVAL_SEC:
            zones = get_zones(camera_id)
            last_zone_refresh = time.time()
            count_history = {}

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

        # 최근 5프레임 기준으로 개수 안정화
        stable_zone_counts = {}
        for name, count in zone_counts.items():
            if name not in count_history:
                count_history[name] = deque(maxlen=5)
            count_history[name].append(count)
            stable_count = Counter(count_history[name]).most_common(1)[0][0]
            stable_zone_counts[name] = stable_count

        if stable_zone_counts != last_zone_counts:
            print(f"\n===== CAM {camera_id} (세그먼트) 안정화된 구역별 개수 =====")
            for name, count in stable_zone_counts.items():
                print(f"{name}: {count}")
            print("===================================================", flush=True)

            send_counts(camera_id, stable_zone_counts)
            last_zone_counts = stable_zone_counts.copy()

        # 의약품 탐지 박스가 표시된 화면 송출
        annotated_frame = results[0].plot()
        success, buffer = cv2.imencode(".jpg", annotated_frame)
        if not success:
            continue

        with state["lock"]:
            state["frame_bytes"] = buffer.tobytes()
            state["stable_zone_counts"] = stable_zone_counts


# ====================================================
# OpenCV 기반 전표(문서/종이) 자동 감지 함수
# ====================================================
def detect_slip_contour(frame):
    h, w = frame.shape[:2]
    frame_area = h * w

    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    blurred = cv2.GaussianBlur(gray, (5, 5), 0)

    # 1) Canny 엣지 검출 기반
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

    # 2) 밝기 이진화 보조 (흰색 종이 영역 검출)
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

        # 전표 감지 시 시각적 피드백
        if slip_detected_now:
            # 감지된 전표 윤곽선 그리기 (초록색)
            cv2.drawContours(display_frame, [paper_contour], -1, (0, 255, 0), 3)

            if current_time - last_slip_capture_time > SLIP_COOLDOWN:
                if slip_first_seen_time is None:
                    slip_first_seen_time = current_time
                    cv2.putText(display_frame, "Slip Detected: Hold still...", (20, 40),
                                cv2.FONT_HERSHEY_SIMPLEX, 0.8, (0, 255, 255), 2)
                elif current_time - slip_first_seen_time >= SLIP_WAIT_TIME:
                    # 1초 이상 유지됨 -> 캡처 실행!
                    filename = f"auto_slip_cam{camera_id}_{int(current_time)}.jpg"
                    filepath = os.path.join(SPRING_UPLOAD_DIR, filename)

                    # 원본 깨끗한 프레임 저장 (OCR 분석용)
                    cv2.imwrite(filepath, frame)
                    threading.Thread(target=run_ocr_background, args=(filepath,), daemon=True).start()

                    last_slip_capture_time = current_time
                    slip_first_seen_time = None
                    capture_success_display_until = current_time + 4.0  # 4초간 안내 표시
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
            # 전표 대기 가이드라인 (중앙에 은은한 사각형)
            gw, gh = int(w * 0.75), int(h * 0.75)
            gx1, gy1 = (w - gw) // 2, (h - gh) // 2
            cv2.rectangle(display_frame, (gx1, gy1), (gx1 + gw, gy1 + gh), (255, 200, 100), 1)
            cv2.putText(display_frame, "Place Slip inside frame", (gx1 + 10, gy1 - 10),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.65, (255, 200, 100), 2)

        # 캡처 완료 안내 문구 표시
        if current_time < capture_success_display_until:
            cv2.putText(display_frame, "[SUCCESS] Slip Captured! Processing OCR & DB...", (20, 80),
                        cv2.FONT_HERSHEY_SIMPLEX, 0.75, (0, 255, 0), 2)

        success, buffer = cv2.imencode(".jpg", display_frame)
        if not success:
            continue

        with state["lock"]:
            state["frame_bytes"] = buffer.tobytes()

        time.sleep(0.03)


def resolve_camera_state(camera_id: int):
    # DB의 STREAM_URL 매핑:
    # /video/0 -> cam1 (OCR_SCAN: 외장 웹캠, ID 1)
    if camera_id == 0:
        return camera_state.get(1)
    # /video/1 -> cam2 (MONITOR: 노트북 웹캠, ID 2)
    if camera_id == 1:
        return camera_state.get(2)
    # /video/2 이상 -> cam3 등 (MONITOR: 노트북 웹캠, ID 2)
    if camera_id >= 2:
        return camera_state.get(2)

    # 직접 ID가 있는 경우
    if camera_id in camera_state:
        return camera_state[camera_id]
    # fallback
    keys = list(camera_state.keys())
    return camera_state[keys[0]] if keys else None

def generate_frames(camera_id):
    state = resolve_camera_state(camera_id)
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


# 각 카메라 역할(MONITOR vs OCR_SCAN)에 맞게 백그라운드 탐지 스레드 시작
for _camera_id, _cfg in CAMERA_DEVICES.items():
    if _cfg["role"] == "MONITOR":
        threading.Thread(target=segment_detection_loop, args=(_camera_id,), daemon=True).start()
    else:
        threading.Thread(target=ocr_detection_loop, args=(_camera_id,), daemon=True).start()


@app.get("/video/{camera_id}")
def video(camera_id: int):
    return StreamingResponse(
        generate_frames(camera_id),
        media_type="multipart/x-mixed-replace; boundary=frame"
    )

@app.get("/status")
def status():
    return {f"CAM_{cid}": cam.isOpened() for cid, cam in cameras.items()}

@app.get("/")
def home():
    return {"message": "PharmVision Camera API"}