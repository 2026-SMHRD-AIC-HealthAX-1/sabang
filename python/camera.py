import cv2

from fastapi import FastAPI
from fastapi.responses import StreamingResponse


# FastAPI 서버 생성
app = FastAPI()


# 카메라 2대 설정
# CAM 01 = USB 카메라
# CAM 02 = 노트북 웹캠

cameras = {
    0: cv2.VideoCapture(1, cv2.CAP_DSHOW),  # USB 카메라
    1: cv2.VideoCapture(0, cv2.CAP_DSHOW)   # 노트북 웹캠
}


# 영상 프레임 생성

def generate_frames(camera_id):

    # 요청한 카메라 가져오기
    camera = cameras.get(camera_id)

    # 카메라가 존재하지 않는 경우
    if camera is None:
        print(f"CAM {camera_id} 없음")
        return

    while True:

        # 카메라에서 한 프레임 읽기
        success, frame = camera.read()

        if not success:
            print(f"CAM {camera_id} 영상 읽기 실패")
            break

        # JPG 형식으로 변환
        success, buffer = cv2.imencode(".jpg", frame)

        if not success:
            continue

        frame_bytes = buffer.tobytes()

        # MJPEG 스트리밍
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