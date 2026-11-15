package app.trashbin.config;

import jakarta.servlet.MultipartConfigElement;
import org.springframework.boot.web.servlet.MultipartConfigFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.unit.DataSize;

/**
 * Servlet multipart limits derived from {@link UploadProperties}, so there is one source of truth.
 * The request may be slightly larger than the file to leave room for the metadata part. The upload
 * service additionally enforces the limit on the bytes actually streamed.
 */
@Configuration(proxyBeanMethods = false)
public class UploadConfig {

    private static final DataSize METADATA_ALLOWANCE = DataSize.ofMegabytes(1);

    @Bean
    MultipartConfigElement multipartConfigElement(UploadProperties properties) {
        MultipartConfigFactory factory = new MultipartConfigFactory();
        factory.setMaxFileSize(properties.maxFileSize());
        factory.setMaxRequestSize(
                DataSize.ofBytes(
                        properties.maxFileSize().toBytes() + METADATA_ALLOWANCE.toBytes()));
        // Small uploads stay in memory; anything larger spills to the servlet temp directory.
        factory.setFileSizeThreshold(DataSize.ofMegabytes(1));
        return factory.createMultipartConfig();
    }
}
