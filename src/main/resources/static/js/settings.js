/*
    ===========================================
        설정 페이지
    ===========================================
    */

    const fontSizeSetting =
        document.getElementById("fontSizeSetting");

    const themeSetting =
        document.getElementById("themeSetting");

    const languageSetting =
        document.getElementById("languageSetting");

    const notificationSetting =
        document.getElementById("notificationSetting");


    /*
        저장되어 있는 설정 가져오기
    */

    fontSizeSetting.value =
        localStorage.getItem("fontSize") || "normal";


    themeSetting.value =
        localStorage.getItem("theme") || "light";


    languageSetting.value =
        localStorage.getItem("language") || "ko";


    notificationSetting.checked =
        localStorage.getItem("notification") !== "false";


    /*
        저장 버튼
    */

    document
        .getElementById("saveSettings")
        .addEventListener("click", function () {


            /* 글자 크기 저장 */

            localStorage.setItem(
                "fontSize",
                fontSizeSetting.value
            );


            /* 테마 저장 */

            localStorage.setItem(
                "theme",
                themeSetting.value
            );


            /* 언어 저장 */

            localStorage.setItem(
                "language",
                languageSetting.value
            );


            /* 알림 설정 저장 */

            localStorage.setItem(
                "notification",
                notificationSetting.checked
            );


            /*
                설정 바로 적용
            */

            applySettings();


            /*
                저장 메시지
            */

            const message =
                document.getElementById("saveMessage");

            message.classList.add("show");


            setTimeout(function () {

                message.classList.remove("show");

            }, 2500);

        });


    /*
        선택 즉시 미리보기
    */

    themeSetting.addEventListener("change", function () {

        if (this.value === "dark") {

            document.body.classList.add("dark-mode");

        } else {

            document.body.classList.remove("dark-mode");

        }

    });


    fontSizeSetting.addEventListener("change", function () {

        document.body.classList.remove(
            "font-small",
            "font-large"
        );


        if (this.value === "small") {

            document.body.classList.add("font-small");

        }


        if (this.value === "large") {

            document.body.classList.add("font-large");

        }

    });
