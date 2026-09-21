package com.smhrd.myapp.controller;

import java.io.InputStream;
import java.net.URI;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

// Python 카메라 서버와 Spring Boot를 연결하는 Controller
@RestController
public class CameraController {

    // Python Flask에서 실행 중인 CCTV 영상 주소
	// Python FastAPI에서 실행 중인 CCTV 영상 주소
    private static final String CAMERA_URL =
    		"http://127.0.0.1:8000/video/0";


    // 웹페이지에서 /camera/video 요청이 들어오면 실행
    @GetMapping(
        value = "/camera/video",
        produces = "multipart/x-mixed-replace; boundary=frame"
    )
    public StreamingResponseBody cameraVideo() {

        return outputStream -> {

            try {

                // Python Flask의 /video에 연결
                URI uri = URI.create(CAMERA_URL);

                try (InputStream inputStream =
                        uri.toURL().openStream()) {

                    byte[] buffer = new byte[8192];

                    int length;

                    // Python에서 받은 CCTV 데이터를
                    // Spring Boot를 통해 계속 전달
                    while ((length = inputStream.read(buffer)) != -1) {

                        outputStream.write(
                                buffer,
                                0,
                                length
                        );

                        outputStream.flush();
                    }
                }

            } catch (Exception e) {

                System.out.println(
                        "Python 카메라 서버 연결 실패 : "
                        + e.getMessage()
                );
            }
        };
    }
}
