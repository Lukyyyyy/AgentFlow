package com.agentflow.service;

import com.agentflow.config.MinioConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class MinioServiceTest {
    private final MinioConfig config = new MinioConfig();
    private final MinioService service = new MinioService();
    private static final String OBJECT_PATH = "/agentflow/audio/a%20b%2Bc.wav";
    private static final String QUERY = "X-Amz-Credential=test%2F20260904%2Fus-east-1%2Fs3%2Faws4_request"
            + "&X-Amz-SignedHeaders=host&X-Amz-Signature=abc123";

    MinioServiceTest() {
        ReflectionTestUtils.setField(service, "minioConfig", config);
    }

    @Test
    void publicProxyPreservesEncodedObjectPathAndSignature() {
        config.setPublicUrl("https://app.example.com/media/");
        assertEquals("https://app.example.com/media" + OBJECT_PATH + "?" + QUERY,
                service.toPublicUrl("http://minio:9000" + OBJECT_PATH + "?" + QUERY));
    }

    @Test
    void localDirectAccessIsUnchanged() {
        config.setPublicUrl("http://localhost:9000");
        String url = "http://localhost:9000" + OBJECT_PATH + "?" + QUERY;
        assertEquals(url, service.toPublicUrl(url));
    }

    @Test
    void localDockerUsesFrontendProxyWithAbsoluteUrl() {
        config.setPublicUrl("http://localhost:5173/media");
        assertEquals("http://localhost:5173/media" + OBJECT_PATH + "?" + QUERY,
                service.toPublicUrl("http://minio:9000" + OBJECT_PATH + "?" + QUERY));
    }

    @Test
    void emptyPublicUrlFallsBackToOriginalUrl() {
        String url = "http://localhost:9000" + OBJECT_PATH + "?" + QUERY;
        assertEquals(url, service.toPublicUrl(url));
        config.setPublicUrl(" ");
        assertEquals(url, service.toPublicUrl(url));
    }

    @Test
    void rejectsNonAbsoluteOrAmbiguousPublicAddresses() {
        for (String base : new String[]{"/media", "ftp://example.com", "https://example.com?x=1",
                "https://example.com#media", "https://user:pass@example.com"}) {
            config.setPublicUrl(base);
            assertThrows(IllegalArgumentException.class,
                    () -> service.toPublicUrl("http://minio:9000" + OBJECT_PATH + "?" + QUERY));
        }
    }
}
