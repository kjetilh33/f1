package com.kinnovatio.signalr;

import java.io.ByteArrayInputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipInputStream;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MessageDecoderIntegrationTest {
    final Logger LOG = LoggerFactory.getLogger(this.getClass());

    @Test
    void messageEncoderTest() throws Exception {
        final String hub = "streaming";
        final String method = "subscribe";
        final List<Object> arguments = List.of(List.of("Heartbeat", "CarData.z"));
        final int identifier = 1;

        String json = MessageDecoder.toMessageJson(hub, method, arguments, identifier);
        LOG.info("Returned json: \n {}", json);

    }

    @Test
    void unzipTest() throws Exception {
        final String carDataZ = "pZjNbhwhDMffhfMmss33XKO+QXtp1UNURWqlKoc2tyjvXgZMSoJhJuxlV5vM34A/ftjzrD49Pv359fBXbd+e1ZenH2pTBKRvEG8wfIawgdtsvDXOI5n4VV3U3f2f9PSzwv3j7uf94+PD7/wHUFskxIsitSHQRWm1pU+jNu0vyqoN0o/0FV5e8jOdOq2AMf9L74okD1mOAK0eaTegRQPoXTGAkB/xYwNGMgCmHiCGbMDmB63pDoAg6p0zZQMQxicoBkQHOqKyvtZZr4sDYy+X908esp4CNOtHQe9EvTe+6HfJPAIYBAs+C5OB6Of7J9l/Nmpe3x74j6QU8hBL/Hxs8g+pV0v5E6ItqyOV1U2Wl7XzHv7rRfcn/3nOPzzavpf9j5zA0Ppf2oGW8md3YDUwcSCaXAHSGSJyBZBpa9D1J7BWcqF2xQO4H+Q1AXq1EwsYfCjpQ1Tqxw0d6GUHBlvOnwthRIBsIIgFGPfQ7/q95l/1fQCShRkwUx1B0PYEMAMD0zcJa/AsMAkYmBPcTIHp4DpgIgIDExtgSgk7ImYM1xGTKx51SxwjyOUD6JpxoS3ZcJqYwfKdxVfeh4lpfeQUMM0BrICMgQMtZ2w8cqCMTKQSAL4xGJn6JDIDcPzRdvV+Cpk5bjkDJ/4bIzMVLN8YjtaI6WxNQHcUwAExDTExW2a7/tKRiYnRsAdxgZh7AZXVuWP4KDFTAHj7PqwQMz3oKwLsNciMyY6NdITMYCsysU1YI9zxMjL5gtD6sMORkUm69pi4gsyoA+8/+DVieqw95uQEY2LuAbMSMu1ZZBpuUt4hs4/ACJmcMPMeZYzMQLyBd8gUmDVAZh0TANaQGbyAzN5/IjJ9dBWZtIhM565B5n5pMzONW2RmQHbg4aA2YGaEK5jpY3zLgOLCXj1gJnlGnnZrzCS+tJaZSbVtCm6RmX4DuE0wAIr6kJmvBYPtWHmamZan0mVmosE3GfthZvKLAQx6lZn2Omai48HctBm318/JNlMHgZlWYK7MzMhNxrRNnjHTgxXaDHt6Mvd11Jj1uRNm5vZqZya2zBTeDInMNJqbJOjHylPMpJrCaBaZWd+MIKwxswbgRARHzKyjaZtCvp8VB5M5Vhe2tz718gE0DfctZPBKaIZJ4zRtNC2Pep4OoPn95R8=";
        final String positionZ = "nZa9blsxDIXfRbMdiH8idffOLdAMbYoOQZHBKJoUiTsFfvfqXomKO0Syuxg24ANJhzwf+Ro+Pb0cjoenx7B8ew23h18PL8f7X7/DEjAi7QH2YLfRlpgWyTeCEonlLuzCh8fj8+HhJSyvAdaPz8f745/yM3x8vH2+//Gz/OVLWKgIduFrWCBn3IW7sKSY4mkX8H3NXlOGTUSZoYqAqYhoLJIqQrJzEb8vAkhWb0eRzm8HcXxS2lQWMbeTSFfVwIgsuZ7ErO0kkVUzuN4ehZp7xRG/4PYoSO/LmGkTMQm4Jq8aG9Upqzuxvm5TwVaokRWoWiu1h8TiXqRVNigwsduOhuaH4aoaVBiZq6jcK/u7VgtxZKEiNOMxWuvAUvZVpiOZYr2i+KtktZAGFV77olaraMm9sLUDRy3IUZqDmDwhupZYZFQubF5w4nMv0igibEm9Wln9XWuRdeRFyYS03t26eDvN1ivaKPkcaxuaeRcmPp1OuzlllCVGgCsoA6L/Q5lqIiWODgycU6ZmH7NXq4pGJY4ILVs9WqIXUKb6p9p7cEoZa3EEIfdBYEoZjS2OGnMnBs4oQ9U9TtDTaDPKUGwZJjC3IuYpZST2vnV6AuGUMgBOmaQXU0acMghwBWXaxAIwYKeMzSlDjk8PCSSYc8aqHRqhj8c444yYcwbIkZF0xpmUmhsi0d1IU86kFDtn+g3zlDNgbdqxsk87kxlnBPtc4MtBo0uMN4ic0a4BTWRfZ1SvAA23zaRPoEvWmRpKTF6u6Tpj4umXnv5LtpmqUn7bZmzCGdU2VHseW/iHnMnUVIrdPeYZZ7A6zn0XvIQzXqa611TO2JQzYNn71tu9wGPGGXJklAd6SABmnNE2UyMhnHsx5gylZmHJk+8YCBdwps6SPYN34Na2M87UO6bty4WcUd8x+oAs68MEM2VTbWYk+mfNHWOG38aC9YzoFDPk45jZOmZ0hpltv92c7yRMVDDz/fQX";
        
        byte[] carDataBytes = Base64.getDecoder().decode(carDataZ);
        //GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(carDataBytes));
        ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(carDataBytes));
        String decompressed = new String(zis.readAllBytes(), StandardCharsets.UTF_8); 

        LOG.info("Cardata byte steam size: {}", carDataBytes.length);
        LOG.info("Decompressed byte stream size: {}", zis.readAllBytes().length);
        LOG.info("Decompressed string: \n {}", decompressed);

    }
    
}
