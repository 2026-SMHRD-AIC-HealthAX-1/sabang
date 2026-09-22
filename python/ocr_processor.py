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

# 전표 저장은 DB에 직접 쓰지 않고 이 Spring 서버의 API를 거친다 (save_slip 참고)
SPRING_URL = "http://127.0.0.1:8089"

# 서버가 구버전 Oracle이라 thin 모드가 지원되지 않아 Instant Client 19c+(thick 모드)가 필요함
ORACLE_INSTANT_CLIENT_DIR = r"C:\Users\smhrd\instantclient-basic-windows.x64-23.26.3.0.0\instantclient_23_26"
try:
    oracledb.init_oracle_client(lib_dir=ORACLE_INSTANT_CLIENT_DIR)
except Exception as e:
    # 스프링과 연동할 때는 print 대신 json으로 에러를 넘겨주는 것이 좋지만, 일단 로그용으로 둡니다.
    pass 

# 하드코딩된 키워드 목록 대신, 실제로 그 병원(관리자)이 등록한 의약품 목록으로
# OCR 텍스트를 매칭한다 (fetch_admin_medicines 참고) - 새 의약품을 등록해도 코드 수정 없이 바로 인식됨

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

def extract_information(ocr_result, medicine_lookup):
    """
    medicine_lookup: {의약품명(DB에 등록된 실제 이름): MEDICINE_ID} - fetch_admin_medicines()로 받아온
    "이 관리자가 실제 등록한 의약품 목록". OCR 텍스트를 이 목록과 대조해서 의약품을 찾는다.
    """
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
    if ord_match:
        extra_info["전표번호"] = ord_match.group()
    else:
        # 전표에 미리 인쇄된 번호가 없거나 OCR이 못 읽으면, 인식 시각 기준으로 자동 채번한다.
        # (예전엔 여기서 못 찾으면 "찾지 못함"으로 남아서 뒤에서 저장 자체가 조용히 스킵됐음 -
        #  실제 병원 전표 양식이 이 정규식과 정확히 안 맞으면 아무것도 저장 안 되는 문제가 있었음)
        extra_info["전표번호"] = f"OCR-{datetime.now().strftime('%Y%m%d%H%M%S%f')}"

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

    # 2. 의약품 추출 - 등록된 의약품명이 OCR 텍스트에 부분 포함돼 있으면 그 줄을 의약품으로 본다
    extracted_data = []
    current_med_name = None
    current_med_id = None

    for raw_text in naver_texts:
        text = correct_text(raw_text)

        matched_name = None
        matched_id = None

        for db_name, db_id in medicine_lookup.items():
            if db_name.lower() in text.lower():
                matched_name = db_name
                matched_id = db_id
                break

        if matched_name:
            current_med_name = matched_name
            current_med_id = matched_id
        elif current_med_name and text.isdigit():
            extracted_data.append({
                "의약품명": current_med_name,
                "medicineId": current_med_id,
                "수량": text
            })
            current_med_name = None
            current_med_id = None

    return extra_info, extracted_data

# ==========================================
# 5. 관리자(병원)별 등록 의약품 목록 조회
# ==========================================
def fetch_admin_medicines(admin_id):
    """이 관리자가 등록한 의약품을 {의약품명: MEDICINE_ID}로 돌려준다 (없으면 빈 dict)."""
    try:
        connection = oracledb.connect(user=DB_USER, password=DB_PASSWORD, dsn=DB_DSN)
        cursor = connection.cursor()

        cursor.execute("SELECT MEDICINE_ID, MEDICINE_NAME FROM MEDICINE WHERE ADMIN_ID = :1", [admin_id])
        rows = cursor.fetchall()

        return {name: medicine_id for medicine_id, name in rows}

    except oracledb.Error:
        return {}
    finally:
        if 'cursor' in locals(): cursor.close()
        if 'connection' in locals(): connection.close()


# ==========================================
# 6. 병동 코드/이름 -> WARD_SEQ_ID 조회
#
# 이건 사람이 읽는 텍스트(OCR로 읽은 병동명)를 PK로 바꾸는 단순 조회라 별다른
# 검증/업무 로직이 없어서, 여기서는 계속 직접 SQL로 읽기만 한다(쓰기는 없음).
# ==========================================
def find_ward_seq_id(ward_text, admin_id):
    try:
        connection = oracledb.connect(user=DB_USER, password=DB_PASSWORD, dsn=DB_DSN)
        cursor = connection.cursor()

        # 이름이 같은 다른 병원(관리자)의 병동을 잘못 가져오지 않게 ADMIN_ID를 반드시 같이 건다
        cursor.execute(
            "SELECT MIN(WARD_SEQ_ID) FROM WARD WHERE (WARD_CODE = :1 OR WARD_NAME = :1) AND ADMIN_ID = :2",
            [ward_text, admin_id],
        )
        row = cursor.fetchone()

        return row[0] if row and row[0] is not None else None

    except oracledb.Error:
        return None
    finally:
        if 'cursor' in locals(): cursor.close()
        if 'connection' in locals(): connection.close()


# ==========================================
# 7. 전표 저장 - Spring API(POST /api/slips)를 통해서 저장한다.
#
# 예전엔 여기서 SLIP/SLIP_ITEM에 직접 INSERT를 했는데, 그러면 Java의 SlipService.create()가
# 갖고 있는 검증(전표번호 중복 체크, 병동/의약품 존재 확인 등)을 이 경로만 비껴가게 되고,
# 나중에 그 검증 로직이 바뀌어도 여기는 따로 안 챙기면 반영이 안 된다. API를 통해 저장하면
# 그 검증이 항상 같이 적용되고, 로직이 한 곳(Java)에만 있으면 된다.
# ==========================================
def save_slip(basic_info, med_data, image_path, admin_id):

    ward_seq_id = find_ward_seq_id(basic_info['요청병동'], admin_id)

    if ward_seq_id is None:
        print(f"전표 저장 실패: 병동 정보를 찾지 못함 ({basic_info['요청병동']})")
        return False

    if not med_data:
        print("전표 저장 실패: OCR에서 저장할 의약품 정보를 찾지 못함")
        return False

    items = []

    for item in med_data:

        medicine_id = item.get('medicineId')

        if not medicine_id:
            print(f"전표 저장 실패: 의약품 정보를 찾지 못함 ({item.get('의약품명')})")
            return False

        items.append({"medicineId": medicine_id, "requestQty": int(item['수량'])})

    # ⭐ C드라이브 경로를 웹 경로로 변환 (잘 수정하신 부분!)
    file_name = os.path.basename(image_path)
    web_image_path = f"/uploads/{file_name}"

    body = {
        "slipId": basic_info['전표번호'],
        "wardSeqId": ward_seq_id,
        "requesterName": basic_info['담당자'],
        "slipDate": basic_info['일자'] if basic_info['일자'] != "찾지 못함" else None,
        "imagePath": web_image_path,
        "items": items,
    }

    try:
        response = requests.post(f"{SPRING_URL}/api/slips", json=body, timeout=5)

        if response.status_code == 200:
            return True

        print("전표 저장 실패:", response.status_code, response.text)
        return False

    except Exception as e:
        print("전표 저장 오류:", e)
        return False

# ==========================================
# 7. 메인 파이프라인
# ==========================================
def process_slip_image(image_path, admin_id):
    enhanced_path = "enhanced_" + os.path.basename(image_path)

    # 1. 화질 개선
    enhance_image_for_ocr(image_path, enhanced_path)

    # 2. OCR API 호출
    ocr_result = recognize_with_naver_ocr(enhanced_path)
    if not ocr_result:
        return {"status": "error", "message": "OCR API 호출 실패"}

    # 3. 데이터 추출 - 이 관리자가 실제 등록한 의약품 목록과 대조해서 medicineId까지 채운다
    medicine_lookup = fetch_admin_medicines(admin_id)
    basic_info, med_data = extract_information(ocr_result, medicine_lookup)

    # 4. 저장 (Spring API 경유 - 전표번호는 extract_information에서 항상 채워지므로 스킵될 일이 없음)
    if basic_info['전표번호'] != "찾지 못함":
        # 원래 이미지 경로(C드라이브)를 넘겨주면, 함수 안에서 웹 경로로 바꿔 저장함
        if not save_slip(basic_info, med_data, image_path, admin_id):
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
# 8. 스프링 연동을 위한 실행부
# ==========================================
if __name__ == "__main__":
    # 스프링(Java)에서 이 파이썬 파일을 실행할 때 인자값으로 이미지 경로(+선택적으로 admin_id)를 전달받습니다.
    if len(sys.argv) > 1:
        target_image_path = sys.argv[1]
        target_admin_id = sys.argv[2] if len(sys.argv) > 2 else "admin"
        result = process_slip_image(target_image_path, target_admin_id)

        # 결과를 JSON 형태로 출력 (스프링 백엔드에서 이 출력값을 읽어갑니다)
        print(json.dumps(result, ensure_ascii=False))
    else:
        # 경로 전달이 안 됐을 때 에러를 JSON 형태로 출력
        print(json.dumps({"status": "error", "message": "이미지 경로가 전달되지 않았습니다."}, ensure_ascii=False))