package youtube.youtubeService.controller;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.URL;
import java.net.UnknownHostException;

@Slf4j
@Controller
@RequiredArgsConstructor
public class InstanceController {

    @ResponseBody
    @GetMapping("/server-info")
    public String serverInfo(HttpSession session) {
        String serverIp;
        String serverHostName;
        String containerId;
        try {
            InetAddress serverHost = InetAddress.getLocalHost();
            serverIp = serverHost.getHostAddress();
            serverHostName = serverHost.getHostName();
            containerId = System.getenv("HOSTNAME"); // Cloud Run 컨테이너 ID
        } catch (UnknownHostException e) {
            serverIp = "Error getting IP";
            serverHostName = "Error getting Hostname";
            containerId = "Error getting ContainerId";
        }

        return "세션 ID: " + session.getId() + "<br>" +
                "서버 IP: " + serverIp + "<br>" +
                "서버 호스트명: " + serverHostName + "<br>" +
                "컨테이너명: " + containerId;
    }

    @ResponseBody
    @GetMapping("/instance-info")
    public String instanceInfo(HttpSession session) {
        String instanceId = fetchInstanceId();

        return "세션 ID: " + session.getId() + "<br>" +
                "<b>요청 처리 인스턴스 ID: " + instanceId + "</b>";
    }

    private String fetchInstanceId() {
        String metadataUrl = "http://metadata.google.internal/computeMetadata/v1/instance/id";
        try {
            URL url = new URL(metadataUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestProperty("Metadata-Flavor", "Google");
            conn.setRequestMethod("GET");

            if (conn.getResponseCode() == HttpURLConnection.HTTP_OK) {
                try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    return in.readLine();
                }
            } else {
                return "메타데이터 서버 응답 실패: " + conn.getResponseCode();
            }
        } catch (Exception e) {
            return "인스턴스 ID 조회 실패: " + e.getMessage();
        }
    }
}
