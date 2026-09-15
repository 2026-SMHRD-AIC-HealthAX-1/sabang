document
        .getElementById("loginForm")
        .addEventListener("submit", function(event) {

            event.preventDefault();

            const email =
                document.getElementById("email").value;

            const password =
                document.getElementById("password").value;

            if(email && password) {

                /*
                    지금은 화면 테스트용이므로
                    값만 입력하면 대시보드로 이동
                */

                window.location.href = "dashboard.html";

            }
        });
