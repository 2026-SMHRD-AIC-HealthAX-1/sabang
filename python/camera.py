import cv2

from fastapi import FastAPI
from fastapi.responses import StreamingResponse


# FastAPI 서버 생성


app = FastAPI()

# 카메라 연결

# 0 = 노트북 기본 카메라
# 1 = 외부 USB 카메라인 경우가 많음
camera = cv2.VideoCapture(1)


# 실시간 영상 생성 함수


def generate_frames():

    while True:

        # 카메라에서 영상 한 프레임 읽기
        success, frame = camera.read()

        if not success:
            print("카메라 영상 읽기 실패")
            break


        # 이미지를 JPG 형식으로 변환
        success, buffer = cv2.imencode(
            ".jpg",
            frame
        )

        if not success:
            continue


        # JPG 데이터를 byte 형태로 변환
        frame_bytes = buffer.tobytes()


        # 실시간 영상 스트리밍
        yield (
            b"--frame\r\n"
            b"Content-Type: image/jpeg\r\n\r\n"
            + frame_bytes
            + b"\r\n"
        )

# 실시간 CCTV 영상

@app.get("/video")
def video():

    return StreamingResponse(
        generate_frames(),
        media_type="multipart/x-mixed-replace; boundary=frame"
    )

# 카메라 연결 상태

@app.get("/status")
def status():

    if camera.isOpened():

        return {
            "status": "connected"
        }

    return {
        "status": "disconnected"
    }

# 기본 페이지

@app.get("/")
def home():

    return {
        "message": "PharmVision Camera API"
    }