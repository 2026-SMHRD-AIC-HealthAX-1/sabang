/*
===========================================
    회원가입 폼 처리

    아이디/이메일/전화번호 중복 확인은
    /api/auth/check 로 백엔드에 물어보고,
    최종 제출은 /api/auth/signup 으로 보낸다.
===========================================
*/

const memberIdInput = document.getElementById("memberId");
const emailInput = document.getElementById("email");
const phoneInput = document.getElementById("phone");

const memberIdError = document.getElementById("memberIdError");
const emailError = document.getElementById("emailError");
const phoneError = document.getElementById("phoneError");

// 중복 확인 버튼을 눌러야만 아이디 통과로 인정
let idChecked = false;

async function checkDuplicate(field, value) {

    const response = await fetch(
        `/api/auth/check?field=${field}&value=${encodeURIComponent(value)}`
    );

    const data = await response.json();

    return data.duplicate;
}

document
    .getElementById("checkIdBtn")
    .addEventListener("click", async function() {

        const value = memberIdInput.value.trim();

        if (!value) {
            memberIdError.innerText = "아이디를 입력해 주세요.";
            return;
        }

        const duplicate = await checkDuplicate("id", value);

        if (duplicate) {
            memberIdError.innerText = "이미 사용 중인 아이디입니다.";
            idChecked = false;
        } else {
            memberIdError.innerText = "사용 가능한 아이디입니다.";
            memberIdError.style.color = "#1a9c5a";
            idChecked = true;
        }
    });

// 아이디를 다시 수정하면 중복 확인을 다시 받아야 함
memberIdInput.addEventListener("input", function() {
    idChecked = false;
    memberIdError.innerText = "";
    memberIdError.style.color = "";
});

emailInput.addEventListener("blur", async function() {

    const value = emailInput.value.trim();

    if (!value) {
        emailError.innerText = "";
        return;
    }

    const duplicate = await checkDuplicate("email", value);

    emailError.innerText = duplicate
        ? "이미 등록된 이메일입니다."
        : "";
});

phoneInput.addEventListener("blur", async function() {

    const value = phoneInput.value.trim();

    if (!value) {
        phoneError.innerText = "";
        return;
    }

    const duplicate = await checkDuplicate("phone", value);

    phoneError.innerText = duplicate
        ? "이미 등록된 전화번호입니다."
        : "";
});


document
    .getElementById("signupForm")
    .addEventListener("submit", async function(event) {

        event.preventDefault();

        const memberId = memberIdInput.value.trim();
        const name = document.getElementById("name").value.trim();
        const email = emailInput.value.trim();
        const phone = phoneInput.value.trim();
        const password = document.getElementById("password").value;
        const passwordConfirm = document.getElementById("passwordConfirm").value;
        const agree = document.getElementById("agree").checked;

        const message = document.getElementById("message");

        message.innerText = "";

        if (!idChecked) {
            message.innerText = "아이디 중복 확인을 해주세요.";
            return;
        }

        if (password !== passwordConfirm) {
            message.innerText = "비밀번호가 일치하지 않습니다.";
            return;
        }

        if (!agree) {
            message.innerText = "이용약관과 개인정보처리방침에 동의해주세요.";
            return;
        }

        if (emailError.innerText || phoneError.innerText) {
            message.innerText = "입력하신 정보를 다시 확인해 주세요.";
            return;
        }

        try {

            const response = await fetch("/api/auth/signup", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({
                    memberId,
                    password,
                    memberName: name,
                    email,
                    phone
                })
            });

            const data = await response.json();

            if (!response.ok) {
                message.innerText = data.message || "회원가입에 실패했습니다. 다시 시도해 주세요.";
                return;
            }

            alert("회원가입이 완료되었습니다.");

            window.location.href = "login.html";

        } catch (error) {

            message.innerText = "회원가입에 실패했습니다. 다시 시도해 주세요.";
        }

    });
