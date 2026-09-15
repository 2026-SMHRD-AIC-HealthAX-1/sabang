/*
        ==========================================================
        DB 연결 전 테스트용 회원가입 사용자

        나중에는 이 배열을 삭제하고

        Spring
            ↓
        Controller
            ↓
        Service
            ↓
        Repository
            ↓
        Oracle

        방식으로 회원 정보를 검색하면 됩니다.
        ==========================================================
        */


        const registeredUsers = [

            {
                id: "user02",

                name: "박민수",

                email: "minsu@hospital.com",

                department: "약품관리팀",

                role: "사용자"
            },


            {
                id: "user03",

                name: "최서연",

                email: "seoyeon@hospital.com",

                department: "간호팀",

                role: "사용자"
            },


            {
                id: "user04",

                name: "정도현",

                email: "dohyun@hospital.com",

                department: "약제팀",

                role: "사용자"
            },


            {
                id: "user05",

                name: "김유진",

                email: "yujin@hospital.com",

                department: "병동",

                role: "사용자"
            }

        ];



        /*
        ==========================================================
        HTML 요소 가져오기
        ==========================================================
        */


        const searchInput =
            document.getElementById("userSearchInput");


        const searchBtn =
            document.getElementById("searchUserBtn");


        const searchResult =
            document.getElementById("searchResult");


        const searchMessage =
            document.getElementById("searchMessage");


        const resultName =
            document.getElementById("resultName");


        const resultEmail =
            document.getElementById("resultEmail");


        const addUserBtn =
            document.getElementById("addUserBtn");


        const userTableBody =
            document.getElementById("userTableBody");



        /*
        검색해서 선택된 사용자를 저장하는 변수
        */

        let selectedUser = null;



        /*
        ==========================================================
        사용자 검색
        ==========================================================
        */


        function searchUser() {


            /* 검색창 값 */

            const keyword =
                searchInput.value.trim();



            /*
            이름을 입력하지 않았을 경우
            */

            if (keyword === "") {


                searchResult.classList.remove(
                    "active"
                );


                searchMessage.textContent =
                    "검색할 사용자 이름을 입력해주세요.";


                searchMessage.classList.add(
                    "active"
                );


                return;

            }



            /*
            registeredUsers 배열에서
            입력한 이름과 같은 사용자 검색
            */


            selectedUser =
                registeredUsers.find(
                    function(user) {

                        return user.name === keyword;

                    }
                );



            /*
            사용자를 찾은 경우
            */

            if (selectedUser) {


                resultName.textContent =
                    selectedUser.name;


                resultEmail.textContent =
                    selectedUser.email;


                searchMessage.classList.remove(
                    "active"
                );


                searchResult.classList.add(
                    "active"
                );

            }


            /*
            사용자를 찾지 못한 경우
            */

            else {


                selectedUser = null;


                searchResult.classList.remove(
                    "active"
                );


                searchMessage.textContent =
                    "회원가입된 사용자를 찾을 수 없습니다.";


                searchMessage.classList.add(
                    "active"
                );

            }

        }



        /*
        ==========================================================
        사용자 목록에 추가
        ==========================================================
        */


        function addUser() {


            if (!selectedUser) {

                return;

            }



            /*
            이미 사용자 목록에 등록된 사람인지 확인
            */


            const rows =
                userTableBody.querySelectorAll("tr");



            for (let row of rows) {


                const userId =
                    row.children[0].textContent.trim();



                if (userId === selectedUser.id) {


                    alert(
                        "이미 사용자 목록에 등록되어 있습니다."
                    );


                    return;

                }

            }



            /*
            새로운 tr 생성
            */


            const newRow =
                document.createElement("tr");



            /*
            새로운 사용자 정보 넣기
            */


            newRow.innerHTML = `

                <td>
                    ${selectedUser.id}
                </td>

                <td>
                    ${selectedUser.name}
                </td>

                <td>
                    ${selectedUser.email}
                </td>

                <td>
                    ${selectedUser.department}
                </td>

                <td>
                    ${selectedUser.role}
                </td>

                <td>
                    사용중
                </td>

            `;



            /*
            왼쪽 사용자 목록에 추가
            */


            userTableBody.appendChild(
                newRow
            );



            /*
            추가 완료 알림
            */


            alert(
                selectedUser.name +
                " 사용자가 사용자 목록에 추가되었습니다."
            );



            /*
            검색창 초기화
            */


            searchInput.value = "";


            searchResult.classList.remove(
                "active"
            );


            searchMessage.classList.remove(
                "active"
            );


            selectedUser = null;

        }



        /*
        ==========================================================
        검색 버튼
        ==========================================================
        */


        searchBtn.addEventListener(
            "click",
            searchUser
        );



        /*
        ==========================================================
        사용자 추가 버튼
        ==========================================================
        */


        addUserBtn.addEventListener(
            "click",
            addUser
        );



        /*
        ==========================================================
        검색창에서 Enter 눌러도 검색
        ==========================================================
        */


        searchInput.addEventListener(
            "keydown",

            function(event) {


                if (event.key === "Enter") {

                    searchUser();

                }

            }

        );
