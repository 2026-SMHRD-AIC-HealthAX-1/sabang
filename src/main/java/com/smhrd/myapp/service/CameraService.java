package com.smhrd.myapp.service;

import java.io.File;

import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

@Service
public class CameraService {

    // 실행된 FastAPI 프로세스를 저장
    private Process fastApiProcess;


    // Spring Boot가 시작되면 자동 실행
    @PostConstruct
    public void startFastApi() {

        try {

            // 현재 sabang 프로젝트의 위치
            String projectPath = System.getProperty("user.dir");

            // sabang/python 폴더
            File pythonFolder =
                    new File(projectPath, "python");


            System.out.println(
                    "프로젝트 위치 : " + projectPath
            );

            System.out.println(
                    "Python 폴더 위치 : "
                    + pythonFolder.getAbsolutePath()
            );


            // 우리가 터미널에서 사용했던
            //
            // python -m uvicorn camera:app
            // --host 0.0.0.0
            // --port 8000
            //
            // 명령어를 Spring이 대신 실행
            ProcessBuilder processBuilder =
                    new ProcessBuilder(
                            "python",
                            "-m",
                            "uvicorn",
                            "camera:app",
                            "--host",
                            "0.0.0.0",
                            "--port",
                            "8000"
                    );


            // camera.py가 있는 폴더에서 명령 실행
            processBuilder.directory(pythonFolder);


            // FastAPI 실행 로그를
            // Spring Console에서도 볼 수 있도록 설정
            processBuilder.inheritIO();


            // FastAPI 실행
            fastApiProcess =
                    processBuilder.start();


            System.out.println(
                    "FastAPI 카메라 서버 실행 요청 완료"
            );


        } catch (Exception e) {

            System.out.println(
                    "FastAPI 카메라 서버 실행 실패"
            );

            e.printStackTrace();
        }
    }

    // Spring Boot 종료 시 FastAPI도 종료
    @PreDestroy
    public void stopFastApi() {

        if (fastApiProcess != null
                && fastApiProcess.isAlive()) {

            fastApiProcess.destroy();

            System.out.println(
                    "FastAPI 카메라 서버 종료"
            );
        }
    }
}
