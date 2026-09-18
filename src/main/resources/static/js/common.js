const profilePageUrl = new URL("../HTML/profile.html", document.currentScript.src).href;
/*
===========================================
    현재 페이지
===========================================
*/

const page = document.body.dataset.page;


/*
===========================================
    메뉴
===========================================
*/

const menus = [

    {
        id: "dashboard",
        ko: "대시보드",
        en: "Dashboard",
        icon: "fa-house",
        url: "dashboard.html"
    },

    {
        id: "monitoring",
        ko: "실시간 모니터링",
        en: "Live Monitoring",
        icon: "fa-video",
        url: "monitoring.html"
    },

    {
        id: "receipts",
        ko: "전표 관리",
        en: "Receipt Management",
        icon: "fa-file-lines",
        url: "receipts.html"
    },

    {
        id: "alerts",
        ko: "이상 알림",
        en: "Alerts",
        icon: "fa-bell",
        url: "alerts.html"
    },

    {
        id: "analytics",
        ko: "통계 분석",
        en: "Analytics",
        icon: "fa-chart-column",
        url: "analytics.html"
    },

    {
        id: "users",
        ko: "관리자페이지",
        en: "Admin Page",
        icon: "fa-user",
        url: "users.html"
    },

    {
        id: "settings",
        ko: "설정",
        en: "Settings",
        icon: "fa-gear",
        url: "settings.html"
    }

];


/*
===========================================
    언어
===========================================
*/

const language =
    localStorage.getItem("language") || "ko";


/*
===========================================
    HEADER
===========================================
*/

const headerTitle =
    language === "en"
        ? "High-Risk Medication Management System"
        : "병동 고위험군 의약품 관리 시스템";


const teamName =
    language === "en"
        ? "Medication Team"
        : "약품관리팀";


document.getElementById("header").innerHTML = `

<header class="header">

    <div class="logo-area">

        <div class="logo-icon">

            <i class="fa-solid fa-plus"></i>

        </div>


        <div class="logo-text">

            <h2>
                Fram <span>Vision</span>
            </h2>

            <p>
                AI Healthcare Monitoring
            </p>

        </div>

    </div>


    <div class="header-title">

        ${headerTitle}

    </div>


    <div class="header-right">


        <div id="currentTime"></div>


        <div class="notification">

            <i class="fa-regular fa-bell"></i>

            <div class="notification-count">
                3
            </div>

        </div>


        <a class="user profile-link" href="${profilePageUrl}" aria-label="개인정보 설정" title="개인정보 설정">

            <div class="user-circle">

                <i class="fa-solid fa-user"></i>

            </div>

            ${teamName}

        </a>


        <button id="headerLogoutBtn" class="header-logout-btn" type="button" title="로그아웃" aria-label="로그아웃">

            <i class="fa-solid fa-right-from-bracket"></i>

        </button>

    </div>

</header>

`;


/*
===========================================
    로그아웃
===========================================
*/

document.getElementById("headerLogoutBtn").addEventListener("click", async function() {

    await fetch("/api/auth/logout", { method: "POST" });

    sessionStorage.removeItem("framVision.isAdmin");
    sessionStorage.removeItem("framVision.billingCache");

    window.location.href = "index.html";
});


/*
===========================================
    SIDEBAR

    깜빡임 없이 바로 그린 뒤, 관리자 확인이 끝나면
    이상 알림 / 사용자 관리 항목만 조용히 숨긴다.
    (hasAccess 자체가 없는 경우만 index.html로 돌려보낸다)

    매 페이지 이동마다 서버에 다시 물어봐야 하지만, 직전에 확인한
    결과를 세션에 기억해뒀다가 그 값으로 먼저 그리면 같은 세션 안에서
    페이지를 옮겨다닐 때는 깜빡임 없이 바로 맞는 상태로 보인다.
    (그 세션의 첫 페이지 로드만 확인 전까지 잠깐 숨겨진 채로 시작함)
===========================================
*/

const adminOnlyMenuIds = ["alerts", "users"];
const isAdminCacheKey = "framVision.isAdmin";
const cachedIsAdmin = sessionStorage.getItem(isAdminCacheKey) === "true";

const menuHtml = menus.map(menu => {

    const menuName =
        language === "en"
            ? menu.en
            : menu.ko;


    const isAdminOnly = adminOnlyMenuIds.includes(menu.id);

    return `

        <a href="${menu.url}"
           data-menu-id="${menu.id}"
           class="menu-item ${page === menu.id ? "active" : ""}"
           ${isAdminOnly && !cachedIsAdmin ? "hidden" : ""}>

            <i class="fa-solid ${menu.icon}"></i>

            <span>
                ${menuName}
            </span>

        </a>

    `;

}).join("");

document.getElementById("sidebar").innerHTML = `

<aside class="sidebar">

    <div class="menu">

        ${menuHtml}

    </div>


    <div class="sidebar-bottom">

        ${
            language === "en"
            ?
            "Safe Medicine,<br>Better Tomorrow"
            :
            "안전한 약,<br>더 나은 내일"
        }

    </div>

</aside>

`;

// 언어 설정을 저장하면(같은 페이지에서) 사이드바도 새로고침 없이 바로 바뀌게 한다
function updateSidebarLanguage(lang) {

    document.querySelectorAll(".menu-item[data-menu-id]").forEach(item => {

        const menu = menus.find(m => m.id === item.dataset.menuId);

        if (!menu) {
            return;
        }

        const span = item.querySelector("span");

        if (span) {
            span.textContent = lang === "en" ? menu.en : menu.ko;
        }
    });

    const sidebarBottom = document.querySelector(".sidebar-bottom");

    if (sidebarBottom) {

        sidebarBottom.innerHTML = lang === "en"
            ? "Safe Medicine,<br>Better Tomorrow"
            : "안전한 약,<br>더 나은 내일";
    }
}

(async () => {

    try {

        const response = await fetch("/api/auth/me");

        if (!response.ok) {
            window.location.replace("index.html");
            return;
        }

        const me = await response.json();

        if (!me.hasAccess) {
            sessionStorage.removeItem(isAdminCacheKey);
            window.location.replace("index.html");
            return;
        }

        sessionStorage.setItem(isAdminCacheKey, String(!!me.isAdmin));

        // 캐시로 미리 그린 상태와 실제 값이 다를 때만(드묾) 반영
        if (!!me.isAdmin !== cachedIsAdmin) {

            adminOnlyMenuIds.forEach(id => {

                const item = document.querySelector(`.menu-item[data-menu-id="${id}"]`);

                if (item) {
                    item.hidden = !me.isAdmin;
                }
            });
        }

    } catch (error) {
        window.location.replace("index.html");
    }
})();


/*
===========================================
    시간
===========================================
*/

function updateTime() {

    const now = new Date();


    const weekdaysKO =
        ["일", "월", "화", "수", "목", "금", "토"];


    const weekdaysEN =
        ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];


    const year =
        now.getFullYear();


    const month =
        String(now.getMonth() + 1)
            .padStart(2, "0");


    const day =
        String(now.getDate())
            .padStart(2, "0");


    const hour =
        String(now.getHours())
            .padStart(2, "0");


    const minute =
        String(now.getMinutes())
            .padStart(2, "0");


    const second =
        String(now.getSeconds())
            .padStart(2, "0");


    const week =
        language === "en"
            ? weekdaysEN[now.getDay()]
            : weekdaysKO[now.getDay()];


    document
        .getElementById("currentTime")
        .innerText =

        `${year}. ${month}. ${day} (${week}) ${hour}:${minute}:${second}`;


    const cameraTime =
        document.getElementById("cameraTime");


    if (cameraTime) {

        cameraTime.innerText =

            `${year}-${month}-${day} ${hour}:${minute}:${second}`;

    }

}


updateTime();

setInterval(updateTime, 1000);


/*
===========================================
    언어 번역
===========================================
*/

const translations = {

    ko: {

        settingsTitle: "⚙ 설정",

        settingsDescription:
            "Fram Vision 시스템 환경을 설정합니다.",

        environmentSettings:
            "환경 설정",

        fontSize:
            "글자 크기",

        fontSizeDescription:
            "화면에 표시되는 글자의 크기를 설정합니다.",

        darkMode:
            "화면 모드",

        darkModeDescription:
            "밝은 화면 또는 어두운 화면을 선택합니다.",

        language:
            "언어",

        languageDescription:
            "시스템에서 사용할 언어를 선택합니다.",

        notificationSetting:
            "알림 설정",

        notificationDescription:
            "이상 감지 시 알림을 표시합니다.",

        saveSettings:
            "설정 저장",

        saveComplete:
            "설정이 저장되었습니다.",

        dashboardTitle:
            "대시보드",

        dashboardDescription:
            "Fram Vision 의약품 관리 현황을 확인합니다.",

        ocrTitle:
            "최근 전표 OCR 인식 결과",

        ocrDescription:
            "예시 데이터 · ORD-20250910-001 · 2025-09-10 · 출고 · 7병동 · 담당자 김지은",

        ocrColMedicine:
            "필요한 의약품명",

        ocrColQuantity:
            "개수",

        medicinePropofol:
            "프로포폴",

        medicineFentanyl:
            "펜타닐",

        medicineKetamine:
            "케타민",

        qty1:
            "1개",

        qty2:
            "2개",

        monitoringTitle:
            "실시간 모니터링",

        monitoringDescription:
            "AI 객체탐지를 이용하여 고위험 의약품의 이동을 실시간으로 감지합니다.",

        liveCctv:
            "실시간 CCTV",

        cameraManageBtn:
            "카메라 관리",

        cameraEmptyMessage:
            "등록된 카메라가 없습니다.",

        cameraManageTitle:
            "카메라 관리",

        cameraAddPlaceholder:
            "새 카메라 이름 (예: CAM 03)",

        addBtn:
            "추가",

        closeBtn:
            "닫기",

        receiptsTitle:
            "전표 관리",

        receiptsDescription:
            "의약품 입·출고 전표와 OCR 인식 결과를 확인합니다.",

        receiptListTitle:
            "전표 목록",

        colReceiptNo:
            "전표번호",

        colDate:
            "일자",

        colType:
            "구분",

        colWard:
            "병동",

        colMedicineCount:
            "의약품 수",

        colStaff:
            "담당자",

        colStatus:
            "상태",

        colAction:
            "작업",

        typeOutbound:
            "출고",

        ward7:
            "7병동",

        medicineCount34:
            "3종 / 4개",

        staffKimJieun:
            "김지은",

        sampleData:
            "예시 데이터",

        viewBtn:
            "보기",

        receiptImageTitle:
            "촬영한 전표 이미지",

        receiptCaption:
            "예시 전표 · ORD-20250910-001 · 2025-09-10 · 7병동 · 담당자 김지은",

        receiptOcrTitle:
            "OCR 인식 결과",

        receiptOcrNote:
            "예시 데이터 · 첨부 전표를 보고 입력한 결과입니다.",

        colMedicineName:
            "의약품명",

        alertsTitle:
            "이상 알림",

        alertsDescription:
            "AI가 감지한 재고 이상 및 전표 불일치 내역입니다.",

        mismatchAlertTitle:
            "전표·세그먼트 불일치",

        mismatchAlertDescription:
            "전표의 요청 수량과 세그먼트 인식 수량이 다른 내역입니다.",

        colTime:
            "시간",

        colMedicine:
            "의약품",

        colMismatchDetail:
            "불일치 내용",

        colProcessStatus:
            "처리상태",

        inventoryAlertTitle:
            "카메라 재고 부족",

        inventoryAlertDescription:
            "카메라에서 확인된 재고가 설정된 최소 수량 이하인 내역입니다.",

        colInventoryStatus:
            "재고 현황",

        analyticsTitle:
            "통계 분석",

        analyticsDescription:
            "입·출고 및 이상 감지 데이터를 분석합니다.",

        filterSelectLabel:
            "항목 선택 (여러 개 선택 가능)",

        filterWard:
            "병동",

        filterMedicine:
            "의약품",

        filterDay:
            "일별",

        filterWeek:
            "주별",

        filterMonth:
            "월별",

        filterQty:
            "수량",

        usersPageTitle:
            "사용자 관리",

        usersPageDescription:
            "약품관리 시스템을 사용하는 담당자를 관리합니다.",

        userListTitle:
            "사용자 목록",

        colUserId:
            "사용자 ID",

        colName:
            "이름",

        colEmail:
            "이메일",

        colDepartment:
            "소속",

        colRole:
            "권한",

        exampleDept1:
            "약품관리팀",

        roleAdmin:
            "관리자",

        statusActive:
            "사용중",

        exampleUserName:
            "이현우",

        exampleDept2:
            "약제팀",

        roleUser:
            "사용자",

        userAddTitle:
            "사용자 추가",

        userAddDescription:
            "시스템에 회원가입한 사용자의 아이디를 검색한 뒤 사용자 목록에 추가할 수 있습니다.",

        searchIdPlaceholder:
            "사용자 아이디를 입력하세요",

        searchBtn:
            "검색",

        addToListBtn:
            "사용자 목록에 추가",

        searchNotFoundMessage:
            "회원가입된 사용자를 찾을 수 없습니다.",

        zoneConfigTitle:
            "구역(Zone) 설정",

        noCameraLabel:
            "카메라 없음",

        noCameraRegisteredMessage:
            "먼저 실시간 모니터링에서 카메라를 등록해 주세요.",

        addMedicineBtn:
            "약품추가",

        saveBtn:
            "저장",

        addMedicineModalTitle:
            "약품 추가",

        highRiskLabel:
            "고위험군 여부",

        riskNormal:
            "일반",

        riskHigh:
            "고위험",

        manufacturerLabel:
            "제조사",

        registerDateLabel:
            "등록일자",

        cancelBtn:
            "취소",

        noMismatchMessage:
            "감지된 수량 불일치가 없습니다.",

        noInventoryAlertMessage:
            "감지된 재고 부족 알림이 없습니다.",

        selectAtLeastOneMessage:
            "표시할 항목을 하나 이상 선택하세요.",

        noOutboundDataMessage:
            "표시할 출고 데이터가 없습니다.",

        profileTitle:
            "개인정보 설정",

        profileDescription:
            "회원 정보와 구독결제 정보를 한곳에서 확인하세요.",

        backToDashboard:
            "대시보드로 돌아가기",

        profileNotice:
            "화면 데모 · 회원 정보는 이 브라우저에 저장됩니다. 구독결제 영역은 예시 데이터입니다.",

        noAccountName:
            "가입 정보 없음",

        noAccountEmail:
            "회원가입 후 같은 이메일로 로그인해 주세요.",

        personalInfoTitle:
            "회원 정보",

        personalInfoDescription:
            "회원가입할 때 입력한 정보입니다.",

        noSignupInfoTitle:
            "표시할 가입 정보가 없습니다.",

        noSignupInfoDescription:
            "기존 회원가입 화면은 정보를 저장하지 않았습니다. 새 회원가입 화면에서 등록한 뒤 같은 이메일로 로그인하면 여기에 표시됩니다.",

        signupLinkText:
            "회원가입",

        loginLinkText:
            "로그인",

        emailHelpText:
            "회원가입에 사용한 이메일입니다.",

        joinedDateLabel:
            "가입일",

        termsAgreedLabel:
            "약관 및 개인정보 동의",

        passwordLabel:
            "비밀번호",

        passwordNotStoredNote:
            "비밀번호는 이 화면에 표시하거나 브라우저에 저장하지 않습니다.",

        editNameNote:
            "이름을 수정한 후 저장해 주세요.",

        saveChangesBtn:
            "변경사항 저장",

        withdrawTitle:
            "회원탈퇴",

        withdrawDescription:
            "탈퇴하면 계정과 구독·권한 정보가 모두 삭제되며 되돌릴 수 없습니다.",

        billingTitle:
            "구독결제 정보",

        billingDescription:
            "서비스 이용과 결제 정보를 확인합니다.",

        sampleLabel:
            "예시",

        monthlySubscription:
            "월간 구독",

        perMonth:
            "원 / 월",

        billingStatusLabel:
            "구독 상태",

        billingLastPaymentLabel:
            "최근 결제일",

        billingNextPaymentLabel:
            "다음 결제 예정일",

        billingAmountLabel:
            "결제 금액",

        billingMethodLabel:
            "결제수단",

        billingSampleValue:
            "예시 · 실제 구독 조회 전",

        billingNoteText:
            "기존 구독 화면의 예시 정보입니다. 실제 결제 내역이나 자동결제 상태를 나타내지 않습니다."

    },


    en: {

        settingsTitle:
            "⚙ Settings",

        settingsDescription:
            "Configure the Fram Vision system.",

        environmentSettings:
            "Environment Settings",

        fontSize:
            "Font Size",

        fontSizeDescription:
            "Adjust the size of text displayed on the screen.",

        darkMode:
            "Display Mode",

        darkModeDescription:
            "Choose light mode or dark mode.",

        language:
            "Language",

        languageDescription:
            "Select the language used by the system.",

        notificationSetting:
            "Notifications",

        notificationDescription:
            "Display notifications when an anomaly is detected.",

        saveSettings:
            "Save Settings",

        saveComplete:
            "Settings have been saved.",

        dashboardTitle:
            "Dashboard",

        dashboardDescription:
            "Check Fram Vision medicine management status.",

        ocrTitle:
            "Recent Receipt OCR Results",

        ocrDescription:
            "Sample Data · ORD-20250910-001 · 2025-09-10 · Outbound · Ward 7 · Staff Kim Ji-eun",

        ocrColMedicine:
            "Required Medicine",

        ocrColQuantity:
            "Quantity",

        medicinePropofol:
            "Propofol",

        medicineFentanyl:
            "Fentanyl",

        medicineKetamine:
            "Ketamine",

        qty1:
            "1",

        qty2:
            "2",

        monitoringTitle:
            "Live Monitoring",

        monitoringDescription:
            "Detect the movement of high-risk medicines in real time using AI object detection.",

        liveCctv:
            "Live CCTV",

        cameraManageBtn:
            "Manage Cameras",

        cameraEmptyMessage:
            "No cameras registered.",

        cameraManageTitle:
            "Manage Cameras",

        cameraAddPlaceholder:
            "New camera name (e.g. CAM 03)",

        addBtn:
            "Add",

        closeBtn:
            "Close",

        receiptsTitle:
            "Receipt Management",

        receiptsDescription:
            "Check medicine inbound/outbound receipts and OCR recognition results.",

        receiptListTitle:
            "Receipt List",

        colReceiptNo:
            "Receipt No.",

        colDate:
            "Date",

        colType:
            "Type",

        colWard:
            "Ward",

        colMedicineCount:
            "Medicine Count",

        colStaff:
            "Staff",

        colStatus:
            "Status",

        colAction:
            "Action",

        typeOutbound:
            "Outbound",

        ward7:
            "Ward 7",

        medicineCount34:
            "3 types / 4 units",

        staffKimJieun:
            "Kim Ji-eun",

        sampleData:
            "Sample Data",

        viewBtn:
            "View",

        receiptImageTitle:
            "Captured Receipt Image",

        receiptCaption:
            "Sample Receipt · ORD-20250910-001 · 2025-09-10 · Ward 7 · Staff Kim Ji-eun",

        receiptOcrTitle:
            "OCR Recognition Results",

        receiptOcrNote:
            "Sample data · Entered based on the attached receipt.",

        colMedicineName:
            "Medicine Name",

        alertsTitle:
            "Alerts",

        alertsDescription:
            "Inventory anomalies and receipt mismatches detected by AI.",

        mismatchAlertTitle:
            "Receipt · Segment Mismatch",

        mismatchAlertDescription:
            "Cases where the requested quantity on the receipt differs from the detected segment quantity.",

        colTime:
            "Time",

        colMedicine:
            "Medicine",

        colMismatchDetail:
            "Mismatch Detail",

        colProcessStatus:
            "Status",

        inventoryAlertTitle:
            "Low Camera Inventory",

        inventoryAlertDescription:
            "Cases where inventory confirmed by camera is at or below the configured minimum quantity.",

        colInventoryStatus:
            "Inventory Status",

        analyticsTitle:
            "Analytics",

        analyticsDescription:
            "Analyze inbound/outbound and anomaly detection data.",

        filterSelectLabel:
            "Select Fields (multiple allowed)",

        filterWard:
            "Ward",

        filterMedicine:
            "Medicine",

        filterDay:
            "Daily",

        filterWeek:
            "Weekly",

        filterMonth:
            "Monthly",

        filterQty:
            "Quantity",

        usersPageTitle:
            "User Management",

        usersPageDescription:
            "Manage staff using the medicine management system.",

        userListTitle:
            "User List",

        colUserId:
            "User ID",

        colName:
            "Name",

        colEmail:
            "Email",

        colDepartment:
            "Department",

        colRole:
            "Role",

        exampleDept1:
            "Medicine Management Team",

        roleAdmin:
            "Admin",

        statusActive:
            "Active",

        exampleUserName:
            "Lee Hyunwoo",

        exampleDept2:
            "Pharmacy Team",

        roleUser:
            "User",

        userAddTitle:
            "Add User",

        userAddDescription:
            "Search for a registered user's ID and add them to the user list.",

        searchIdPlaceholder:
            "Enter a user ID",

        searchBtn:
            "Search",

        addToListBtn:
            "Add to User List",

        searchNotFoundMessage:
            "No registered user found.",

        zoneConfigTitle:
            "Zone Configuration",

        noCameraLabel:
            "No Camera",

        noCameraRegisteredMessage:
            "Please register a camera in Live Monitoring first.",

        addMedicineBtn:
            "Add Medicine",

        saveBtn:
            "Save",

        addMedicineModalTitle:
            "Add Medicine",

        highRiskLabel:
            "High-Risk",

        riskNormal:
            "Normal",

        riskHigh:
            "High-Risk",

        manufacturerLabel:
            "Manufacturer",

        registerDateLabel:
            "Registered Date",

        cancelBtn:
            "Cancel",

        noMismatchMessage:
            "No quantity mismatches detected.",

        noInventoryAlertMessage:
            "No low inventory alerts detected.",

        selectAtLeastOneMessage:
            "Please select at least one field to display.",

        noOutboundDataMessage:
            "No outbound data to display.",

        profileTitle:
            "Profile Settings",

        profileDescription:
            "Check your account and subscription billing info in one place.",

        backToDashboard:
            "Back to Dashboard",

        profileNotice:
            "Screen demo · Account info is stored in this browser. The billing section is sample data.",

        noAccountName:
            "No Account Info",

        noAccountEmail:
            "Please sign up and log in with the same email.",

        personalInfoTitle:
            "Account Info",

        personalInfoDescription:
            "Information entered when you signed up.",

        noSignupInfoTitle:
            "No signup info to display.",

        noSignupInfoDescription:
            "The previous signup screen didn't save information. Register on the new signup screen and log in with the same email to see it here.",

        signupLinkText:
            "Sign Up",

        loginLinkText:
            "Log In",

        emailHelpText:
            "The email you used to sign up.",

        joinedDateLabel:
            "Joined Date",

        termsAgreedLabel:
            "Terms & Privacy Agreement",

        passwordLabel:
            "Password",

        passwordNotStoredNote:
            "Your password is not shown on this screen or stored in the browser.",

        editNameNote:
            "Edit your name and save.",

        saveChangesBtn:
            "Save Changes",

        withdrawTitle:
            "Delete Account",

        withdrawDescription:
            "Deleting your account removes it along with all subscription/permission data. This cannot be undone.",

        billingTitle:
            "Subscription Billing",

        billingDescription:
            "Check your service usage and payment info.",

        sampleLabel:
            "Sample",

        monthlySubscription:
            "Monthly Subscription",

        perMonth:
            "/ month",

        billingStatusLabel:
            "Subscription Status",

        billingLastPaymentLabel:
            "Last Payment Date",

        billingNextPaymentLabel:
            "Next Payment Date",

        billingAmountLabel:
            "Payment Amount",

        billingMethodLabel:
            "Payment Method",

        billingSampleValue:
            "Sample · Before real subscription lookup",

        billingNoteText:
            "Sample info from the previous subscription screen. It does not represent actual payment history or auto-payment status."

    }

};


// 다른 스크립트(anomaly-ui.js, analytics.js 등)에서 현재 언어로 번역된 문구를
// 바로 읽어갈 수 있게 헬퍼 함수로 노출한다.
function t(key) {

    const lang = localStorage.getItem("language") || "ko";

    return (translations[lang] && translations[lang][key]) || key;
}


/*
===========================================
    설정 적용
===========================================
*/

function applySettings() {


    /*
    ===========================
        글자 크기
    ===========================
    */

    const fontSize =
        localStorage.getItem("fontSize")
        || "normal";


    document.body.classList.remove(
        "font-small",
        "font-large"
    );


    if (fontSize === "small") {

        document.body.classList.add(
            "font-small"
        );

    }


    if (fontSize === "large") {

        document.body.classList.add(
            "font-large"
        );

    }


    /*
    ===========================
        다크모드
    ===========================
    */

    const theme =
        localStorage.getItem("theme")
        || "light";


    if (theme === "dark") {

        document.body.classList.add(
            "dark-mode"
        );

    } else {

        document.body.classList.remove(
            "dark-mode"
        );

    }


    /*
    ===========================
        언어
    ===========================
    */

    const savedLanguage =
        localStorage.getItem("language")
        || "ko";


    updateSidebarLanguage(savedLanguage);


    const elements =
        document.querySelectorAll(
            "[data-i18n]"
        );


    elements.forEach(element => {

        const key =
            element.dataset.i18n;


        if (
            translations[savedLanguage]
            &&
            translations[savedLanguage][key]
        ) {

            element.innerText =
                translations[savedLanguage][key];

        }

    });


    // placeholder 속성 번역 (input의 placeholder는 innerText로 못 바꿈)
    const placeholderElements =
        document.querySelectorAll(
            "[data-i18n-placeholder]"
        );


    placeholderElements.forEach(element => {

        const key =
            element.dataset.i18nPlaceholder;


        if (
            translations[savedLanguage]
            &&
            translations[savedLanguage][key]
        ) {

            element.setAttribute(
                "placeholder",
                translations[savedLanguage][key]
            );

        }

    });

}


/*
===========================================
    최초 실행
===========================================
*/

applySettings();