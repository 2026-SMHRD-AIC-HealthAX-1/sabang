import cv2

for i in range(4):
    print(f"\n===== index {i} 테스트 =====")

    cap = cv2.VideoCapture(i, cv2.CAP_DSHOW)

    print("isOpened:", cap.isOpened())

    if cap.isOpened():
        ret, frame = cap.read()
        print("frame read:", ret)

    cap.release()