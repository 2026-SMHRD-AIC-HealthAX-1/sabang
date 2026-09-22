import threading
import time

import cv2
import os
import requests

from datetime import datetime
from ultralytics import YOLO
from fastapi import FastAPI
from fastapi.responses import StreamingResponse
from collections import deque, Counter


# YOLO 세그멘테이션 모델 불러오기
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
MODEL_PATH = os.path.join(BASE_DIR, "best.pt")

model = YOLO(MODEL_PATH)

# 같은 model 객체를 카메라 스레드 여러 개가 동시에 쓰게 되므로, 추론 호출 자체는 순서대로만 하게 묶는다.
# (카메라 read() 자체는 이제 스레드당 자기 카메라 하나만 읽어서 문제 없지만, YOLO 추론까지
# 안전하다고 보장된 건 아니라서 안전하게 직렬화해둔다)
model_lock = threading.Lock()

SPRING_URL = "http://127.0.0.1:8089"

# 구역 설정을 얼마 만에 다시 불러올지 (관리자가 구역을 바꿔도, 서버 재시작 없이 이 주기마다 반영됨)
ZONE_REFRESH_INTERVAL_SEC = 30

# 전표 인식 후, 반출로 세그먼트 개수가 줄어드는 걸 이만큼(초) 안에 못 잡으면 포기한다
DISPENSE_WATCH_TIMEOUT_SEC = 300

# 이 파이썬 인스턴스가 담당하는 병원(관리자) 계정.
# 병원마다 자기 서버 + 카메라 세트를 따로 운영하는 배포 구조라, 배포할 때 그 병원의
# 관리자 계정 ID로 바꿔줘야 한다 (지금은 캠퍼스 테스트 DB의 admin 계정 기준).
ADMIN_ID = "admin"


def get_zones(camera_id):

    spring_camera_id = PYTHON_ID_TO_SPRING_ID.get(camera_id)

    if spring_camera_id is None:
        return []

    try:
        response = requests.get(
            f"{SPRING_URL}/api/medicine-zones/camera/{spring_camera_id}",
            timeout=2
        )

        if response.status_code == 200:
            return response.json()

        print("구역 조회 실패:", response.status_code)
        return []

    except Exception as e:
        print("Spring 구역 API 연결 실패:", e)
        return []

def send_counts(camera_id, counts):

    spring_camera_id = PYTHON_ID_TO_SPRING_ID.get(camera_id)

    if spring_camera_id is None:
        return

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


# 이 관리자의 카메라 목록을 Spring에서 받아와서, "파이썬 내부 카메라 인덱스(STREAM_URL 끝자리,
# 예: /video/0 -> 0) -> 진짜 CAMERA_ID(전역 PK)" 매핑을 만든다.
# 파이썬 내부 인덱스는 관리자마다 0부터 다시 매겨지는 별개의 번호라서, 그대로 CAMERA_ID인 것처럼
# Spring을 호출하면(과거에 그랬음) 엉뚱한 카메라(혹은 존재하지 않는 카메라)를 조회하게 된다.
# 같이 받아온 cameraRole로 OCR_SCAN 카메라의 내부 인덱스도 알아낸다.
def fetch_camera_registry(admin_id):

    try:
        response = requests.get(f"{SPRING_URL}/api/cameras/by-admin/{admin_id}", timeout=5)

        if response.status_code != 200:
            print("카메라 목록 조회 실패:", response.status_code)
            return {}, None

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
        return {}, None


PYTHON_ID_TO_SPRING_ID, OCR_PYTHON_ID = fetch_camera_registry(ADMIN_ID)


def is_inside_zone(center_x, center_y, zone, frame_width, frame_height):

    x1 = frame_width * (zone["regionX"] / 100)
    y1 = frame_height * (zone["regionY"] / 100)

    x2 = frame_width * ((zone["regionX"] + zone["regionWidth"]) / 100)
    y2 = frame_height * ((zone["regionY"] + zone["regionHeight"]) / 100)

    return x1 <= center_x <= x2 and y1 <= center_y <= y2



# FastAPI 서버 생성
app = FastAPI()


# 카메라 물리 장치 연결
# CAM 0, 1 = 기존 모니터링용 2대 (실측 결과 OS 장치 인덱스가 뒤바뀌어 있어 아래처럼 매핑함)
# OCR 스캔용 카메라는 DB에 등록된 파이썬 내부 인덱스(OCR_PYTHON_ID)를 그대로 OS 장치 인덱스로
# 시도한다 - 실제 연결된 웹캠 번호가 다르면 이 값만 바꿔주면 된다.
cameras = {
    0: cv2.VideoCapture(1, cv2.CAP_DSHOW), # USB 카메라
    1: cv2.VideoCapture(0, cv2.CAP_DSHOW)   # 노트북 웹캠
}

if OCR_PYTHON_ID is not None and OCR_PYTHON_ID not in cameras:
    cameras[OCR_PYTHON_ID] = cv2.VideoCapture(OCR_PYTHON_ID, cv2.CAP_DSHOW)

# 카메라별 "최신 상태" - 감지 스레드가 계속 갱신하고, 뷰어들은 이 값만 읽어간다.
# (예전엔 뷰어가 접속할 때마다 카메라를 직접 읽고 안정화 이력을 새로 시작했는데,
#  그러면 ①아무도 안 보고 있으면 감지 자체가 멈추고 ②재접속마다 안정화가 리셋되고
#  ③여러 명이 보면 카메라 read()가 스레드 간에 겹치는 문제가 있었음.
#  그래서 카메라 읽기+감지는 카메라당 스레드 1개가 서버 켜져있는 동안 계속 돌게 하고,
#  뷰어는 그 결과(최신 프레임)만 구경하는 구조로 바꿈)
camera_state = {
    camera_id: {
        "lock": threading.Lock(),
        "frame_bytes": None,
        "stable_zone_counts": {},
    }
    for camera_id in cameras
}


# 카메라 한 대를 계속 읽으면서 감지하는 백그라운드 작업.
# 서버 시작할 때 카메라마다 이 함수를 스레드로 하나씩 띄워서, 프로세스가 살아있는 동안 계속 돈다.
def detection_loop(camera_id):

    camera = cameras[camera_id]
    state = camera_state[camera_id]

    zones = get_zones(camera_id)
    last_zone_refresh = time.time()

    last_zone_counts = None
    count_history = {}

    while True:

        # 구역 설정을 주기적으로 다시 불러온다 (관리자가 구역을 바꿨을 수 있어서)
        if time.time() - last_zone_refresh > ZONE_REFRESH_INTERVAL_SEC:
            zones = get_zones(camera_id)
            last_zone_refresh = time.time()
            count_history = {}  # 구역이 바뀌었을 수 있으니 안정화 이력도 같이 초기화

        success, frame = camera.read()

        if not success:
            print(f"CAM {camera_id} 영상 읽기 실패")
            time.sleep(1)
            continue

        # YOLO 탐지 (model은 카메라 스레드들이 공유하니 추론 호출만 순서대로)
        with model_lock:
            results = model(frame, conf=0.6, verbose=False)

        # 프레임 크기
        frame_height, frame_width = frame.shape[:2]

        # 구역별 탐지 개수 초기화
        zone_counts = {}

        for zone in zones:
            zone_counts[zone["medicineName"]] = 0

        # 탐지된 물체 하나씩 확인
        for box in results[0].boxes:

            x1, y1, x2, y2 = box.xyxy[0].tolist()

            center_x = (x1 + x2) / 2
            center_y = (y1 + y2) / 2

            class_id = int(box.cls[0])
            class_name = model.names[class_id]

            for zone in zones:

                medicine_name = zone["medicineName"]

                # YOLO 클래스명과 구역의 medicineName이 다르면 무시
                if class_name != medicine_name:
                    continue

                # 클래스명도 같고 구역 안에 있으면 카운트
                if is_inside_zone(
                    center_x,
                    center_y,
                    zone,
                    frame_width,
                    frame_height
                ):
                    zone_counts[medicine_name] += 1

        # 최근 5프레임 기준으로 개수 안정화
        stable_zone_counts = {}

        for name, count in zone_counts.items():

            if name not in count_history:
                count_history[name] = deque(maxlen=5)

            count_history[name].append(count)

            # 최근 값 중 가장 많이 나온 개수를 안정값으로 사용
            stable_count = Counter(count_history[name]).most_common(1)[0][0]

            stable_zone_counts[name] = stable_count

        # 안정화된 값이 바뀔 때만 출력 + Spring으로 전송
        if stable_zone_counts != last_zone_counts:

            print(f"\n===== CAM {camera_id} 안정화된 구역별 개수 =====")

            for name, count in stable_zone_counts.items():
                print(f"{name}: {count}")

            print("==============================", flush=True)

            send_counts(camera_id, stable_zone_counts)

            last_zone_counts = stable_zone_counts.copy()

        # YOLO 결과를 그려넣은 프레임을 JPEG로 인코딩해서 "최신 상태"에 반영
        annotated_frame = results[0].plot()

        success, buffer = cv2.imencode(".jpg", annotated_frame)

        if not success:
            continue

        with state["lock"]:
            state["frame_bytes"] = buffer.tobytes()
            state["stable_zone_counts"] = stable_zone_counts


# ===========================================
# 전표 자동 감지 + 반출 비교 (OCR 스캔용 카메라 전용)
#
# 아직 네이버 CLOVA OCR 모델이 없어서, "전표가 놓였다"를 감지하는 부분과
# "반출 전후 세그먼트 개수를 비교해서 알림까지 만드는" 부분을 먼저 준비해둔다.
# run_ocr_stub()만 실제 OCR 호출로 나중에 바꾸면, 나머지(전표 등록 -> 반출 감시 -> 비교/알림)는
# 그대로 이어 쓸 수 있게 짜여 있다.
# ===========================================

def run_ocr_stub(frame):

    # TODO: 실제 네이버 CLOVA OCR 연동으로 교체.
    # 지금은 모델이 없어서, 전표 하나가 인식된 것처럼 고정된 값을 돌려준다.
    # (medicineId 1=Bacchus, 2=battery는 캠퍼스 테스트 DB의 admin 계정 기준 - 실제로는
    #  OCR이 읽은 의약품명을 관리자의 의약품 목록과 매칭해서 medicineId를 찾아야 한다)
    return {
        "wardSeqId": 4,
        "requesterName": "자동인식(테스트)",
        "items": [
            {"medicineId": 1, "medicineName": "Bacchus", "requestQty": 2},
            {"medicineId": 2, "medicineName": "battery", "requestQty": 1},
        ]
    }


def create_slip_from_ocr(ocr_result):

    slip_id = f"OCR-{datetime.now().strftime('%Y%m%d-%H%M%S')}"

    body = {
        "slipId": slip_id,
        "wardSeqId": ocr_result["wardSeqId"],
        "requesterName": ocr_result["requesterName"],
        "slipDate": datetime.now().strftime("%Y-%m-%d"),
        # TODO: 전표 이미지를 서버가 접근 가능한 경로(정적 리소스 등)에 저장하고 그 경로를 넣기
        "imagePath": None,
        "items": [
            {"medicineId": item["medicineId"], "requestQty": item["requestQty"]}
            for item in ocr_result["items"]
        ]
    }

    try:
        response = requests.post(f"{SPRING_URL}/api/slips", json=body, timeout=5)

        if response.status_code == 200:
            return response.json()

        print("전표 등록 실패:", response.status_code, response.text)

    except Exception as e:
        print("전표 등록 오류:", e)

    return None


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


# 전표 인식 시점의 구역 카운트를 기준(baseline)으로 잡고, 그 이후 MONITOR 카메라들의
# 안정화된 카운트가 baseline보다 줄어들 때까지 지켜본다. 줄어들어 다시 안정되면 그 차이를
# 실제 반출 수량으로 보고, 의약품마다 독립적으로 요청수량과 비교해 /api/outbound로 보고한다.
# (여러 카메라에 구역이 나뉘어 있을 수 있어서 같은 이름의 구역 카운트는 합산한다)
def watch_and_compare(slip_id, items):

    def snapshot():

        counts = {}

        for cam_id, state in camera_state.items():

            if cam_id == OCR_PYTHON_ID:
                continue

            with state["lock"]:
                for name, count in state["stable_zone_counts"].items():
                    counts[name] = counts.get(name, 0) + count

        return counts

    baseline = snapshot()
    pending = {item["medicineName"]: item for item in items}
    started = time.time()

    print(f"[전표 {slip_id}] 반출 감시 시작: {list(pending.keys())}")

    while pending and time.time() - started < DISPENSE_WATCH_TIMEOUT_SEC:

        time.sleep(1)
        current = snapshot()

        for medicine_name in list(pending.keys()):

            before = baseline.get(medicine_name, 0)
            now_count = current.get(medicine_name, 0)

            if now_count < before:

                item = pending.pop(medicine_name)
                dispensed = before - now_count

                result = report_outbound(slip_id, item["medicineId"], dispensed)

                if result is not None:
                    tag = "이상" if result.get("abnormal") else "정상"
                    print(f"[전표 {slip_id}] {medicine_name} 반출 {dispensed}개 확인 ({tag}, 요청 {item['requestQty']}개)")

    if pending:
        print(f"[전표 {slip_id}] 시간 초과로 감시 종료 (미확인: {list(pending.keys())})")


# OCR 스캔용 카메라 전용 감지 루프 - YOLO 대신 프레임 차이로 "전표가 놓였다/치워졌다"만 본다.
# 화면이 배경과 다르게 바뀐 채로 일정 프레임 동안 안 움직이면(=종이가 놓이고 흔들림이 멎으면)
# 한 번만 인식을 시도하고, 다시 배경과 비슷해질 때까지(=치워질 때까지) 재시도하지 않는다.
def ocr_detection_loop(camera_id):

    camera = cameras[camera_id]
    state = camera_state[camera_id]

    background = None
    prev_gray = None
    stable_streak = 0
    placed = False

    STABLE_STREAK_NEEDED = 10   # 이 프레임 수만큼 연속으로 안 움직이면 "고정됐다"고 판단
    PLACED_DIFF_THRESHOLD = 15  # 배경과의 평균 밝기 차이(0~255) - 이보다 크면 "뭔가 놓여있다"
    FRAME_DIFF_THRESHOLD = 3    # 프레임간 평균 밝기 차이 - 이보다 작으면 "안 움직인다"
    # 위 세 값은 실제 카메라/조명 환경에 맞춰 조정이 필요할 수 있다

    while True:

        success, frame = camera.read()

        if not success:
            print(f"CAM {camera_id}(OCR) 영상 읽기 실패")
            time.sleep(1)
            continue

        gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
        gray = cv2.GaussianBlur(gray, (21, 21), 0)

        if background is None:
            background = gray.astype("float")

        if prev_gray is not None:

            frame_diff = cv2.absdiff(prev_gray, gray).mean()
            stable_streak = stable_streak + 1 if frame_diff < FRAME_DIFF_THRESHOLD else 0

            background_diff = cv2.absdiff(cv2.convertScaleAbs(background), gray).mean()

            if not placed and stable_streak >= STABLE_STREAK_NEEDED and background_diff > PLACED_DIFF_THRESHOLD:

                placed = True
                print(f"CAM {camera_id} 전표 감지됨 - 인식 시도")

                ocr_result = run_ocr_stub(frame)
                slip = create_slip_from_ocr(ocr_result)

                if slip is not None:
                    threading.Thread(
                        target=watch_and_compare,
                        args=(slip["slipId"], ocr_result["items"]),
                        daemon=True
                    ).start()

            elif placed and background_diff <= PLACED_DIFF_THRESHOLD:

                placed = False
                print(f"CAM {camera_id} 전표 치워짐 - 다음 전표 대기")

            elif not placed:
                # 전표가 없는 동안엔 배경을 천천히 갱신해서 조명 변화 등에 적응한다
                background = background * 0.98 + gray * 0.02

        prev_gray = gray

        success, buffer = cv2.imencode(".jpg", frame)

        if success:
            with state["lock"]:
                state["frame_bytes"] = buffer.tobytes()


# 뷰어가 /video/{id}를 열면 실행되는 제너레이터.
# 더 이상 카메라를 직접 읽거나 감지를 하지 않고, detection_loop가 계속 갱신해두는
# 최신 프레임을 그대로 스트리밍만 해준다 - 그래서 몇 명이 동시에 봐도 서로 안 부딪힌다.
def generate_frames(camera_id):

    state = camera_state.get(camera_id)

    if state is None:
        print(f"CAM {camera_id} 없음")
        return

    while True:

        with state["lock"]:
            frame_bytes = state["frame_bytes"]

        if frame_bytes is None:
            # 감지 스레드가 아직 첫 프레임을 못 만든 경우 (서버 막 켜졌을 때) 잠깐 대기
            time.sleep(0.1)
            continue

        yield (
            b"--frame\r\n"
            b"Content-Type: image/jpeg\r\n\r\n"
            + frame_bytes
            + b"\r\n"
        )

        time.sleep(0.03)


# 서버가 뜰 때 카메라마다 감지용 백그라운드 스레드를 하나씩 실행
# (OCR_SCAN 카메라만 전표 감지 루프, 나머지는 기존 YOLO 구역 감지 루프)
for _camera_id in cameras:
    if _camera_id == OCR_PYTHON_ID:
        threading.Thread(target=ocr_detection_loop, args=(_camera_id,), daemon=True).start()
    else:
        threading.Thread(target=detection_loop, args=(_camera_id,), daemon=True).start()


# 실시간 영상 API

@app.get("/video/{camera_id}")
def video(camera_id: int):

    return StreamingResponse(
        generate_frames(camera_id),
        media_type="multipart/x-mixed-replace; boundary=frame"
    )


# 카메라 연결 상태 확인

@app.get("/status")
def status():
    return {
        f"CAM {camera_id}": camera.isOpened()
        for camera_id, camera in cameras.items()
    }


# 기본 페이지

@app.get("/")
def home():

    return {
        "message": "PharmVision Camera API"
    }
