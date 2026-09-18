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
            "설정이 저장되었습니다."

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
            "Settings have been saved."

    }

};


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

}


/*
===========================================
    최초 실행
===========================================
*/

applySettings();