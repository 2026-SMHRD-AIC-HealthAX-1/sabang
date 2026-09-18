/*
===========================================
    사용자 관리 화면

    회원 목록(GET /api/staff/candidates)을 불러와서
    이름으로 검색하고, 권한부여(POST /api/staff)로
    대시보드 접근 권한을 부여한다.
===========================================
*/

let candidates = [];
let selectedUser = null;

const searchInput = document.getElementById("userSearchInput");
const searchBtn = document.getElementById("searchUserBtn");
const searchResult = document.getElementById("searchResult");
const searchMessage = document.getElementById("searchMessage");
const resultName = document.getElementById("resultName");
const resultEmail = document.getElementById("resultEmail");
const addUserBtn = document.getElementById("addUserBtn");
const userTableBody = document.getElementById("userTableBody");

async function loadCandidates() {

    const response = await fetch("/api/staff/candidates");

    if (!response.ok) {
        return;
    }

    candidates = await response.json();

    renderGrantedTable();
}

function renderGrantedTable() {

    const granted = candidates.filter(user => user.granted);

    userTableBody.innerHTML = granted.map(user => `
        <tr>
            <td>${user.memberId}</td>
            <td>${user.memberName}</td>
            <td>${user.email}</td>
            <td>-</td>
            <td>사용자</td>
            <td>
                사용중
                <button type="button" class="revoke-btn" data-staff-id="${user.memberId}">
                    ${t("revokeBtn")}
                </button>
            </td>
        </tr>
    `).join("");

    userTableBody.querySelectorAll(".revoke-btn").forEach(button => {
        button.addEventListener("click", () => revokeAccess(button.dataset.staffId));
    });
}

/*
===========================================
    권한 회수
===========================================
*/

async function revokeAccess(staffId) {

    const confirmed = confirm(t("revokeConfirmMessage"));

    if (!confirmed) {
        return;
    }

    try {

        const response = await fetch(`/api/staff/${staffId}`, { method: "DELETE" });

        if (!response.ok) {
            alert(t("revokeFailMessage"));
            return;
        }

        await loadCandidates();

    } catch (error) {

        alert(t("revokeFailMessage"));
    }
}

/*
===========================================
    사용자 검색
===========================================
*/

function searchUser() {

    const keyword = searchInput.value.trim();

    if (keyword === "") {

        searchResult.classList.remove("active");

        searchMessage.textContent = "검색할 사용자 이름을 입력해주세요.";
        searchMessage.classList.add("active");

        return;
    }

    selectedUser = candidates.find(user => user.memberId === keyword) || null;

    if (selectedUser) {

        resultName.textContent = selectedUser.memberName;
        resultEmail.textContent = selectedUser.email;

        searchMessage.classList.remove("active");
        searchResult.classList.add("active");

    } else {

        searchResult.classList.remove("active");

        searchMessage.textContent = "회원가입된 사용자를 찾을 수 없습니다.";
        searchMessage.classList.add("active");
    }
}

/*
===========================================
    사용자 목록에 추가 (권한 부여)
===========================================
*/

async function addUser() {

    if (!selectedUser) {
        return;
    }

    if (selectedUser.granted) {
        alert("이미 사용자 목록에 등록되어 있습니다.");
        return;
    }

    try {

        const response = await fetch("/api/staff", {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ staffId: selectedUser.memberId })
        });

        const data = await response.json();

        if (!response.ok) {
            alert(data.message || "권한 부여에 실패했습니다.");
            return;
        }

        alert(selectedUser.memberName + " 사용자가 사용자 목록에 추가되었습니다.");

        searchInput.value = "";
        searchResult.classList.remove("active");
        searchMessage.classList.remove("active");
        selectedUser = null;

        await loadCandidates();

    } catch (error) {

        alert("권한 부여에 실패했습니다. 다시 시도해 주세요.");
    }
}

/*
===========================================
    이벤트 연결
===========================================
*/

searchBtn.addEventListener("click", searchUser);
addUserBtn.addEventListener("click", addUser);

searchInput.addEventListener("keydown", function(event) {

    if (event.key === "Enter") {
        searchUser();
    }
});

loadCandidates();
