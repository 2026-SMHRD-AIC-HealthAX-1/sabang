document
        .getElementById("signupForm")
        .addEventListener("submit", function(event) {

            event.preventDefault();


            const password =
                document.getElementById("password").value;

            const passwordConfirm =
                document.getElementById("passwordConfirm").value;

            const agree =
                document.getElementById("agree").checked;

            const message =
                document.getElementById("message");


            if(password !== passwordConfirm) {

                message.innerText =
                    "비밀번호가 일치하지 않습니다.";

                return;
            }


            if(!agree) {

                message.innerText =
                    "이용약관과 개인정보처리방침에 동의해주세요.";

                return;
            }


            /*
                현재는 프론트 화면 테스트용.
                가입 완료 시 로그인 페이지로 이동.
            */

            alert("회원가입이 완료되었습니다.");

            window.location.href = "login.html";

        });
