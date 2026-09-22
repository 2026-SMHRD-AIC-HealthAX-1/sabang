import os
import cv2
import numpy as np
import requests
import uuid
import time
import json
import re
import oracledb
import sys
from datetime import datetime

# ==========================================
# 1. 환경 변수 및 설정값 (실제 환경에 맞게 수정)
# ==========================================
NAVER_OCR_URL = "https://5s6w2v0ihj.apigw.ntruss.com/custom/v1/58113/c886a5a41de758102c52865b0506929ae6cd9d5b844f10e4fb42f72623df8084/general"
NAVER_OCR_SECRET = "SlBTSUlXQURwd05xSUdZY0tFWnB2RER0YlJScUp6VVc="

DB_USER = "cd_26K_HI1_p2_5"
DB_PASSWORD = "smhrd5"
DB_DSN = "project-db-campus.smhrd.com:1523/xe"

# 서버가 구버전 Oracle이라 thin 모드가 지원되지 않아 Instant Client 19c+(thick 모드)가 필요함
ORACLE_INSTANT_CLIENT_DIR = r"C:\Users\smhrd\instantclient-basic-windows.x64-23.26.3.0.0\instantclient_23_26"
try:
    oracledb.init_oracle_client(lib_dir=ORACLE_INSTANT_CLIENT_DIR)
except Exception as e:
    # 스프링과 연동할 때는 print 대신 json으로 에러를 넘겨주는 것이 좋지만, 일단 로그용으로 둡니다.
    pass 

# 테스트용 의약품 키워드
MEDICINE_KEYWORDS = ["몬스터", "Bacchus", "battery", "박카스"]

# ==========================================
# 2. 이미지 화질 개선 모듈
# ==========================================
def enhance_image_for_ocr(image_path, output_path):
    img = cv2.imread(image_path)
    if img is None:
        return image_path

    # 색상 왜곡 방지 및 명암비 개선
    lab = cv2.cvtColor(img, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    cl = clahe.apply(l)
    limg = cv2.merge((cl, a, b))
    balanced_img = cv2.cvtColor(limg, cv2.COLOR_LAB2BGR)

    # 해상도 2배 확대 및 샤프닝
    h, w = balanced_img.shape[:2]
    resized = cv2.resize(balanced_img, (w * 2, h * 2), interpolation=cv2.INTER_CUBIC)
    kernel = np.array([[0, -1, 0], [-1, 5, -1], [0, -1, 0]])
    sharpened = cv2.filter2D(resized, -1, kernel)

    cv2.imwrite(output_path, sharpened)
    return output_path

# ==========================================
# 3. 네이버 OCR API 호출 모듈
# ==========================================
def recognize_with_naver_ocr(image_path):
    if not os.path.exists(image_path):
        return None

    headers = {"X-OCR-SECRET": NAVER_OCR_SECRET}
    ext = image_path.split('.')[-1].lower()
    if ext not in ['jpg', 'jpeg', 'png']:
        ext = 'jpg'

    message = {
        "images": [{"format": ext, "name": "document_image"}],
        "requestId": str(uuid.uuid4()),
        "version": "V2",
        "timestamp": int(round(time.time() * 1000))
    }
    payload = {"message": json.dumps(message)}

    with open(image_path, "rb") as f:
        files_data = [("file", f.read())]

    response = requests.post(NAVER_OCR_URL, headers=headers, data=payload, files=files_data)

    if response.status_code == 200:
        return response.json()
    return None

# ==========================================
# 4. 데이터 추출 및 후처리 모듈
# ==========================================
def correct_text(text):
    text = text.replace("나트f", "나트륨").replace("네숲", "네슘")
    if text == "a": return "2"
    elif text.lower() in ["xl", "l", "i"]: return "1"
    return text

def extract_information(ocr_result):
    naver_texts = []
    for image in ocr_result.get("images", []):
        for field in image.get("fields", []):
            naver_texts.append(field.get("inferText", ""))

    full_text_str = " ".join(naver_texts)

    # 1. 기본 정보 추출
    extra_info = {
        "전표번호": "찾지 못함",
        "일자": "찾지 못함",
        "요청병동": "찾지 못함",
        "담당자": "찾지 못함"
    }

    ord_match = re.search(r'ORD-\d{8}-\d{3}', full_text_str)
    if ord_match: extra_info["전표번호"] = ord_match.group()

    date_match = re.search(r'\d{4}-\d{2}-\d{2}', full_text_str)
    if date_match: extra_info["일자"] = date_match.group()

    ward_match = re.search(r'\d+병동', full_text_str)
    if ward_match: extra_info["요청병동"] = ward_match.group()

    for i, text in enumerate(naver_texts):
        if "담당자" in text:
            clean_text = text.replace("담당자", "").replace(":", "").strip()
            if clean_text:
                extra_info["담당자"] = clean_text
            elif i + 1 < len(naver_texts):
                extra_info["담당자"] = naver_texts[i+1]
            break

    # 2. 의약품 추출
    extracted_data = []
    current_med = None

    for raw_text in naver_texts:
        text = correct_text(raw_text)
        is_medicine = any(keyword.lower() in text.lower() for keyword in MEDICINE_KEYWORDS)

        if is_medicine:
            current_med = text
        elif current_med and text.isdigit():
            extracted_data.append({"의약품명": current_med, "수량": text})
            current_med = None

    return extra_info, extracted_data

# ==========================================
# 5. 오라클 DB 저장 모듈 (경로 변환 로직 추가됨)
# ==========================================
def save_to_oracle(basic_info, med_data, image_path):
    try:
        connection = oracledb.connect(user=DB_USER, password=DB_PASSWORD, dsn=DB_DSN)
        cursor = connection.cursor()

        # 지워졌던 병동 확인 및 날짜 세팅 코드 복구
        cursor.execute(
            "SELECT MIN(WARD_SEQ_ID) FROM WARD WHERE WARD_CODE = :1 OR WARD_NAME = :1",
            [basic_info['요청병동']],
        )
        ward_row = cursor.fetchone()
        if not ward_row or ward_row[0] is None:
            raise ValueError(f"병동 정보를 찾지 못함: {basic_info['요청병동']}")

        if not med_data:
            raise ValueError("OCR에서 저장할 의약품 정보를 찾지 못함")

        try:
            slip_date = datetime.strptime(basic_info['일자'], '%Y-%m-%d')
        except ValueError:
            slip_date = None

        # ⭐ C드라이브 경로를 웹 경로로 변환 (잘 수정하신 부분!)
        file_name = os.path.basename(image_path)
        web_image_path = f"/uploads/{file_name}"

        insert_slip_sql = """
            INSERT INTO SLIP (SLIP_ID, REQUESTER_NAME, SLIP_DATE, WARD_ID, IMAGE_PATH, CREATED_AT)
            VALUES (:1, :2, :3, :4, :5, SYSDATE)
        """
        cursor.execute(insert_slip_sql, [
            basic_info['전표번호'],
            basic_info['담당자'],
            slip_date,
            ward_row[0],
            web_image_path,  # 변환된 가짜 경로로 저장
        ])

        insert_item_sql = """
            INSERT INTO SLIP_ITEM (SLIP_ID, ITEM_NO, MEDICINE_ID, REQUEST_QTY)
            VALUES (:1, :2, :3, :4)
        """

        for item_no, item in enumerate(med_data, start=1):
            cursor.execute(
                "SELECT MEDICINE_ID FROM MEDICINE WHERE MEDICINE_NAME = :1",
                [item['의약품명']],
            )
            medicine_row = cursor.fetchone()
            if not medicine_row:
                raise ValueError(f"의약품 정보를 찾지 못함: {item['의약품명']}")

            cursor.execute(insert_item_sql, [
                basic_info['전표번호'], 
                item_no,
                medicine_row[0],
                int(item['수량']),
            ])

        connection.commit()
        return True

    except (oracledb.Error, ValueError) as e:
        if 'connection' in locals(): connection.rollback()
        return False
    finally:
        if 'cursor' in locals(): cursor.close()
        if 'connection' in locals(): connection.close()

# ==========================================
# 6. 메인 파이프라인 (지워졌던 함수 복구)
# ==========================================
def process_slip_image(image_path):
    enhanced_path = "enhanced_" + os.path.basename(image_path)

    # 1. 화질 개선
    enhance_image_for_ocr(image_path, enhanced_path)

    # 2. OCR API 호출
    ocr_result = recognize_with_naver_ocr(enhanced_path)
    if not ocr_result:
        return {"status": "error", "message": "OCR API 호출 실패"}

    # 3. 데이터 추출
    basic_info, med_data = extract_information(ocr_result)

    # 4. DB 저장
    if basic_info['전표번호'] != "찾지 못함":
        # 원래 이미지 경로(C드라이브)를 넘겨주면, 함수 안에서 웹 경로로 바꿔 저장함
        if not save_to_oracle(basic_info, med_data, image_path):
            return {"status": "error", "message": "DB 저장 실패"}

    # 임시 화질개선 파일 삭제
    if os.path.exists(enhanced_path):
        os.remove(enhanced_path)

    return {
        "status": "success",
        "basic_info": basic_info,
        "medicines": med_data
    }

# ==========================================
# 7. 스프링 연동을 위한 실행부
# ==========================================
if __name__ == "__main__":
    # 스프링(Java)에서 이 파이썬 파일을 실행할 때 인자값으로 이미지 경로를 전달받습니다.
    if len(sys.argv) > 1:
        target_image_path = sys.argv[1] 
        result = process_slip_image(target_image_path)
        
        # 결과를 JSON 형태로 출력 (스프링 백엔드에서 이 출력값을 읽어갑니다)
        print(json.dumps(result, ensure_ascii=False)) 
    else:
        # 경로 전달이 안 됐을 때 에러를 JSON 형태로 출력
        print(json.dumps({"status": "error", "message": "이미지 경로가 전달되지 않았습니다."}, ensure_ascii=False))