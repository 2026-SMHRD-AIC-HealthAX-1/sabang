document
        .getElementById("signupForm")
        .addEventListener("submit", function(event) {

            event.preventDefault();


            const name =
                document.getElementById("name").value;

            const email =
                document.getElementById("email").value;

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
                가입 정보를 저장한 뒤 로그인 페이지로 이동.
                실제 인증은 Spring 연결 시 대체 예정.
            */

            try {

                FramAccount.register({ name, email, agreed: agree });

                alert("회원가입이 완료되었습니다.");

                window.location.href = "login.html";

            } catch (error) {

                message.innerText =
                    error.message
                    || "회원가입에 실패했습니다. 다시 시도해 주세요.";
            }

        });
