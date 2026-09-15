document
        .getElementById("loginForm")
        .addEventListener("submit", function(event) {

            event.preventDefault();

            const email =
                document.getElementById("email").value;

            const password =
                document.getElementById("password").value;

            const message =
                document.getElementById("message");

            if(!email || !password) {
                return;
            }

            /*
                가입된 이메일인지 확인 후 데모 로그인 처리.
                실제 비밀번호 인증은 Spring 연결 시 대체 예정.
            */

            FramAccount.selectDemoAccount(email);

            if (!FramAccount.current()) {

                message.innerText =
                    "가입되지 않은 이메일입니다. 회원가입 후 이용해 주세요.";

                return;
            }

            window.location.href = "dashboard.html";
        });
