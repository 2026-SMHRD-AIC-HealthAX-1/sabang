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

SPRING_URL = "http://127.0.0.1:8089"


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

# 영상 프레임 생성
def generate_frames(camera_id):

    print(">>> generate_frames 실행됨!", flush=True)

    camera = cameras.get(camera_id)

    zones = get_zones(1)

    last_zone_counts = None
    count_history = {}

    if camera is None:
        print(f"CAM {camera_id} 없음")
        return

    while True:

        success, frame = camera.read()


        if not success:
            print(f"CAM {camera_id} 영상 읽기 실패")
            break

        # YOLO 탐지
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


         # 안정화된 값이 바뀔 때만 출력
        if stable_zone_counts != last_zone_counts:

            print("\n===== 안정화된 구역별 개수 =====")

            for name, count in stable_zone_counts.items():
                print(f"{name}: {count}")

            print("==============================", flush=True)

            send_counts(1, stable_zone_counts)

            last_zone_counts = stable_zone_counts.copy()

        # YOLO 결과 화면에 표시
        annotated_frame = results[0].plot()

        success, buffer = cv2.imencode(".jpg", annotated_frame)



        if not success:
            continue

        frame_bytes = buffer.tobytes()

        yield (
            b"--frame\r\n"
            b"Content-Type: image/jpeg\r\n\r\n"
            + frame_bytes
            + b"\r\n"
        )
   

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
