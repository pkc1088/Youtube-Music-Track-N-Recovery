package youtube.youtubeService.service;

import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.model.CountryResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetAddress;

@Slf4j
@Service
public class GeoIpService {

    private final DatabaseReader databaseReader;

    public GeoIpService(DatabaseReader databaseReader) {
        this.databaseReader = databaseReader;
    }

    public String getClientCountryCode(HttpServletRequest request) {

        String clientIp = extractClientIp(request);

        if ("0:0:0:0:0:0:0:1".equals(clientIp) || "127.0.0.1".equals(clientIp)) { // 로컬 접근 대비용
            clientIp = "121.145.145.227";//"66.249.73.72";// "2401:fa00:6b:11:b5f9:3297:35e4:e63b"; // "8.8.8.8"; // 테스트용
        }

        try {
            InetAddress ipAddress = InetAddress.getByName(clientIp);
            CountryResponse response = databaseReader.country(ipAddress);
            // log.info("[country code: {}]", countryCode);
            return response.getCountry().getIsoCode();
        } catch (Exception e) {
            log.info("[country code: UNKNOWN -> KR]");
            return "KR";
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xfHeader = request.getHeader("X-Forwarded-For");
        //log.info("[xfHeader: {}]", xfHeader);
        if (xfHeader == null) {
            //log.info("[xfHeader Remote: {}]", remoteAddr);
            return request.getRemoteAddr();
        }
        return xfHeader.split(",")[0];
    }
}