import threading
import time

import cv2
import os
import requests

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


def get_zones(camera_id):
    try:
        response = requests.get(
            f"{SPRING_URL}/api/medicine-zones/camera/{camera_id}",
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
    try:
        response = requests.post(
            f"{SPRING_URL}/api/medicine-zones/count",
            json={
                "cameraId": camera_id,
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



# FastAPI 서버 생성
app = FastAPI()


# 카메라 2대 설정
# CAM 01 = USB 카메라
# CAM 02 = 노트북 웹캠

cameras = {
    0: cv2.VideoCapture(1, cv2.CAP_DSHOW), # USB 카메라
    1: cv2.VideoCapture(0, cv2.CAP_DSHOW)   # 노트북 웹캠
}

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
for _camera_id in cameras:
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
        "CAM 01 USB": cameras[0].isOpened(),
        "CAM 02 LAPTOP": cameras[1].isOpened()
    }


# 기본 페이지

@app.get("/")
def home():

    return {
        "message": "PharmVision Camera API"
    }
