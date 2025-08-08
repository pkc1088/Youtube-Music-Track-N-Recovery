package youtube.youtubeService.config;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import java.io.IOException;

@Configuration
public class GeoIpConfig {

    @Bean
    public DatabaseReader databaseReader() throws IOException {
        ClassPathResource resource = new ClassPathResource("geo/GeoLite2-Country.mmdb");
        return new DatabaseReader.Builder(resource.getInputStream()).withCache(new CHMCache()).build();
//        File mmdbFile = new ClassPathResource("geo/GeoLite2-Country.mmdb").getFile();
//        return new DatabaseReader.Builder(mmdbFile).withCache(new CHMCache()).build();
    }
}
